package com.mistermikhail.fgallery.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateTrashPreflightTest {
    @Test fun interruptionDuringFinalIdentitySnapshotCannotAuthorizeBatch() = runBlocking {
        var identityCalls = 0
        var reads = 0
        var interrupted = false
        try {
            preflightDuplicateTrash(
                listOf("content://copy"),
                { reads++; true },
                sourceIdentity = {
                    identityCalls++
                    if (identityCalls == 2) Thread.currentThread().interrupt()
                    it
                },
            )
        } catch (_: InterruptedException) {
            interrupted = true
        } finally {
            Thread.interrupted()
        }
        assertTrue(interrupted)
        assertEquals(1, reads)
        assertEquals(2, identityCalls)
    }

    @Test fun preInterruptedWorkerNeverStartsDuplicateByteReads() = runBlocking {
        var reads = 0
        var interrupted = false
        Thread.currentThread().interrupt()
        try {
            preflightDuplicateTrash(
                listOf("content://copy"),
                { reads++; true },
                sourceIdentity = { it },
            )
        } catch (_: InterruptedException) {
            interrupted = true
        } finally {
            Thread.interrupted()
        }
        assertTrue(interrupted)
        assertEquals(0, reads)
    }

    @Test fun refusesEntireBatchIfLaterItemFails() = runBlocking {
        val checked = mutableListOf<String>()
        val allowed = preflightDuplicateTrash(listOf("copyA", "copyB", "copyC"), { item ->
            checked += item
            item != "copyB"
        }, sourceIdentity = { it })
        assertFalse(allowed)
        assertEquals(listOf("copyA", "copyB"), checked)
    }

    @Test fun rejectsEmptyBatchAndAllowsFullyVerifiedBatch() = runBlocking {
        assertFalse(preflightDuplicateTrash(emptyList<String>(), { true }, sourceIdentity = { it }))
        assertTrue(preflightDuplicateTrash(listOf("a", "b"), { true }, sourceIdentity = { it }))
    }

    @Test fun duplicateSourceRejectedBeforeVerifierRuns() = runBlocking {
        var checks = 0
        assertFalse(preflightDuplicateTrash(listOf("same", "same"), {
            checks++
            true
        }, sourceIdentity = { it }))
        assertEquals(0, checks)
    }

    @Test fun blankSourceIdentityRejected() = runBlocking {
        assertFalse(preflightDuplicateTrash(listOf("a"), { true }, sourceIdentity = { "" }))
    }

    @Test fun distinctSourceIdentitiesAllowVerification() = runBlocking {
        assertTrue(preflightDuplicateTrash(listOf("uri-a", "uri-b"), { true }, sourceIdentity = { it }))
    }


    @Test fun differentUrisAliasingSamePhysicalFileAreRejectedBeforeReads() = runBlocking {
        var reads = 0
        val allowed = preflightDuplicateTrash(
            listOf("content://a/1", "content://b/2"),
            { reads++; true },
            { it },
            { "/storage/emulated/0/DCIM/original.jpg" },
        )
        assertFalse(allowed)
        assertEquals(0, reads)
    }

    @Test fun missingPhysicalPathsAreNotMistakenForAliases() = runBlocking {
        val allowed = preflightDuplicateTrash(
            listOf("content://saf/a", "content://saf/b"),
            { true },
            { it },
            { null },
        )
        assertTrue(allowed)
    }

    @Test fun distinctKnownPhysicalPathsAllowVerification() = runBlocking {
        val allowed = preflightDuplicateTrash(
            listOf("a", "b"),
            { true },
            { it },
            { "/storage/a/${it}" },
        )
        assertTrue(allowed)
    }

    @Test fun lexicalPhysicalAliasesAreRejectedBeforeAnyRead() = runBlocking {
        var reads = 0
        val allowed = preflightDuplicateTrash(
            listOf("content://provider/one", "content://provider/two"),
            { reads++; true },
            { it },
            { uri ->
                if (uri.endsWith("one")) "/storage/emulated/0/DCIM/./photo.jpg"
                else "/storage/emulated/0/Pictures/../DCIM/photo.jpg"
            },
        )
        assertFalse(allowed)
        assertEquals(0, reads)
    }

    @Test fun blankKnownPhysicalPathFailsClosed() = runBlocking {
        var reads = 0
        assertFalse(preflightDuplicateTrash(
            listOf("content://provider/one"),
            { reads++; true },
            { it },
            { "" },
        ))
        assertEquals(0, reads)
    }

    @Test fun cancellationAfterVerifierCannotAuthorizeBatch() = runBlocking {
        val worker = async {
            preflightDuplicateTrash(listOf("copyA", "copyB"), { item ->
                if (item == "copyA") {
                    // Simulate a provider returning after the scan was cancelled.
                    kotlinx.coroutines.currentCoroutineContext().cancel()
                }
                true
            }, sourceIdentity = { it })
        }
        var cancelled = false
        try {
            worker.await()
        } catch (_: java.util.concurrent.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test fun verifierExceptionNeverAuthorizesBatch() = runBlocking {
        val checked = mutableListOf<String>()
        val allowed = preflightDuplicateTrash(listOf("a", "b", "c"), {
            checked += it
            if (it == "b") throw java.io.IOException("SAF access revoked")
            true
        }, sourceIdentity = { it })
        assertFalse(allowed)
        assertEquals(listOf("a", "b"), checked)
    }

    @Test fun verifierCancellationPropagatesWithoutCheckingNextSource() = runBlocking {
        val checked = mutableListOf<String>()
        var cancelled = false
        try {
            preflightDuplicateTrash(listOf("a", "b"), {
                checked += it
                throw java.util.concurrent.CancellationException("cancelled")
            }, sourceIdentity = { it })
        } catch (_: java.util.concurrent.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(listOf("a"), checked)
    }

    @Test fun verifierInterruptionPropagatesAndPreservesFlag() = runBlocking {
        var interrupted = false
        try {
            preflightDuplicateTrash(listOf("a"), {
                throw InterruptedException("provider interrupted")
            }, sourceIdentity = { it })
        } catch (_: InterruptedException) {
            interrupted = Thread.currentThread().isInterrupted
        } finally {
            Thread.interrupted()
        }
        assertTrue(interrupted)
    }
    @Test fun protectedOriginalUriRejectsBatchBeforeReadingBytes() = runBlocking {
        var reads = 0
        val allowed = preflightDuplicateTrash(
            listOf("content://copy", "content://original"),
            { reads++; true },
            sourceIdentity = { it },
            protectedSourceIdentities = setOf("content://original"),
        )
        assertFalse(allowed)
        assertEquals(0, reads)
    }

    @Test fun protectedOriginalPhysicalAliasRejectsBatchBeforeReadingBytes() = runBlocking {
        var reads = 0
        val allowed = preflightDuplicateTrash(
            listOf("content://other-provider/copy"),
            { reads++; true },
            sourceIdentity = { it },
            physicalIdentity = { "/storage/emulated/0/DCIM/./photo.jpg" },
            protectedPhysicalIdentities = setOf("/storage/emulated/0/Pictures/../DCIM/photo.jpg"),
        )
        assertFalse(allowed)
        assertEquals(0, reads)
    }

    @Test fun unrelatedProtectedOriginalAllowsVerification() = runBlocking {
        assertTrue(preflightDuplicateTrash(
            listOf("content://copy"),
            { true },
            sourceIdentity = { it },
            physicalIdentity = { "/storage/emulated/0/DCIM/copy.jpg" },
            protectedSourceIdentities = setOf("content://original"),
            protectedPhysicalIdentities = setOf("/storage/emulated/0/DCIM/original.jpg"),
        ))
    }

    @Test fun blankProtectedPathFailsClosedBeforeReadingBytes() = runBlocking {
        var reads = 0
        assertFalse(preflightDuplicateTrash(
            listOf("content://copy"),
            { reads++; true },
            sourceIdentity = { it },
            protectedPhysicalIdentities = setOf(" "),
        ))
        assertEquals(0, reads)
    }

    @Test fun symlinkAliasOfProtectedOriginalFailsBeforeReadingBytes() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("duplicate-preflight")
        try {
            val original = java.nio.file.Files.write(directory.resolve("original.jpg"), byteArrayOf(1, 2, 3))
            val alias = directory.resolve("alias.jpg")
            java.nio.file.Files.createSymbolicLink(alias, original)
            var reads = 0
            assertFalse(preflightDuplicateTrash(
                listOf("content://provider/copy"),
                { reads++; true },
                sourceIdentity = { it },
                physicalIdentity = { alias.toString() },
                protectedPhysicalIdentities = setOf(original.toString()),
            ))
            assertEquals(0, reads)
        } finally {
            java.nio.file.Files.list(directory).use { stream ->
                stream.forEach { java.nio.file.Files.deleteIfExists(it) }
            }
            java.nio.file.Files.deleteIfExists(directory)
        }
    }

    @Test fun distinctUrisPointingToSymlinkAndTargetFailBeforeReads() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("duplicate-alias")
        try {
            val original = java.nio.file.Files.write(directory.resolve("original.jpg"), byteArrayOf(4))
            val alias = directory.resolve("alias.jpg")
            java.nio.file.Files.createSymbolicLink(alias, original)
            var reads = 0
            assertFalse(preflightDuplicateTrash(
                listOf("uri-original", "uri-alias"),
                { reads++; true },
                sourceIdentity = { it },
                physicalIdentity = { if (it == "uri-original") original.toString() else alias.toString() },
            ))
            assertEquals(0, reads)
        } finally {
            java.nio.file.Files.list(directory).use { stream ->
                stream.forEach { java.nio.file.Files.deleteIfExists(it) }
            }
            java.nio.file.Files.deleteIfExists(directory)
        }
    }

    @Test fun nulSourceIdentityFailsBeforeVerifierRuns() = runBlocking {
        var reads = 0
        assertFalse(preflightDuplicateTrash(
            listOf("copy"),
            { reads++; true },
            sourceIdentity = { "content://provider/copy\u0000suffix" },
        ))
        assertEquals(0, reads)
    }

    @Test fun nulPhysicalPathsFailBeforeVerifierRuns() = runBlocking {
        var reads = 0
        assertFalse(preflightDuplicateTrash(
            listOf("copy"),
            { reads++; true },
            sourceIdentity = { it },
            physicalIdentity = { "/storage/photo.jpg\u0000suffix" },
        ))
        assertFalse(preflightDuplicateTrash(
            listOf("copy"),
            { reads++; true },
            sourceIdentity = { it },
            protectedPhysicalIdentities = setOf("/storage/original.jpg\u0000suffix"),
        ))
        assertEquals(0, reads)
    }
    @Test fun sourceIdentityFailureRejectsBeforeAnyVerifierRead() = runBlocking {
        var reads = 0
        assertFalse(preflightDuplicateTrash(
            listOf("copy"),
            { reads++; true },
            sourceIdentity = { throw IllegalStateException("provider identity unavailable") },
        ))
        assertEquals(0, reads)
    }

    @Test fun physicalIdentityFailureRejectsBeforeAnyVerifierRead() = runBlocking {
        var reads = 0
        assertFalse(preflightDuplicateTrash(
            listOf("copy"),
            { reads++; true },
            sourceIdentity = { it },
            physicalIdentity = { throw java.io.IOException("storage disconnected") },
        ))
        assertEquals(0, reads)
    }

    @Test fun identityCancellationIsNotSwallowed() = runBlocking {
        var cancelled = false
        try {
            preflightDuplicateTrash(
                listOf("copy"),
                { true },
                sourceIdentity = { throw java.util.concurrent.CancellationException("cancelled") },
            )
        } catch (_: java.util.concurrent.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test fun sourceRebindingDuringVerificationRevokesBatch() = runBlocking {
        var identity = "content://provider/copy"
        assertFalse(preflightDuplicateTrash(
            listOf("copy"),
            { identity = "content://provider/rebound"; true },
            sourceIdentity = { identity },
        ))
    }

    @Test fun physicalPathRebindingDuringVerificationRevokesBatch() = runBlocking {
        var path = "/storage/old-copy.jpg"
        assertFalse(preflightDuplicateTrash(
            listOf("copy"),
            { path = "/storage/new-copy.jpg"; true },
            sourceIdentity = { it },
            physicalIdentity = { path },
        ))
    }

    @Test fun symlinkRetargetedToProtectedOriginalDuringVerificationRevokesBatch() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("duplicate-retarget")
        try {
            val copy = java.nio.file.Files.write(directory.resolve("copy.jpg"), byteArrayOf(1))
            val keeper = java.nio.file.Files.write(directory.resolve("keeper.jpg"), byteArrayOf(1))
            val alias = directory.resolve("alias.jpg")
            java.nio.file.Files.createSymbolicLink(alias, copy)
            assertFalse(preflightDuplicateTrash(
                listOf("content://provider/copy"),
                {
                    java.nio.file.Files.delete(alias)
                    java.nio.file.Files.createSymbolicLink(alias, keeper)
                    true
                },
                sourceIdentity = { it },
                physicalIdentity = { alias.toString() },
                protectedPhysicalIdentities = setOf(keeper.toString()),
            ))
        } finally {
            java.nio.file.Files.list(directory).use { stream ->
                stream.forEach { java.nio.file.Files.deleteIfExists(it) }
            }
            java.nio.file.Files.deleteIfExists(directory)
        }
    }

    @Test fun stableIdentitiesRemainEligibleAfterVerification() = runBlocking {
        assertTrue(preflightDuplicateTrash(
            listOf("copy"),
            { true },
            sourceIdentity = { "content://provider/copy" },
            physicalIdentity = { "/storage/copy.jpg" },
        ))
    }

    @Test fun physicalPathMovesBetweenOpaqueSourcesDuringVerificationRejectsBatch() = runBlocking {
        var rebind = false
        val allowed = preflightDuplicateTrash(
            listOf("content://provider/first", "content://provider/second"),
            { rebind = true; true },
            sourceIdentity = { it },
            physicalIdentity = { uri ->
                if (!rebind && uri.endsWith("first")) "/storage/copy.jpg"
                else if (rebind && uri.endsWith("second")) "/storage/copy.jpg"
                else null
            },
        )
        // Filtering null paths would make both snapshots look like ["/storage/copy.jpg"].
        assertFalse(allowed)
    }

    @Test fun multipleOpaqueSafSourcesRemainEligible() = runBlocking {
        assertTrue(preflightDuplicateTrash(
            listOf("content://saf/a", "content://saf/b"),
            { true },
            sourceIdentity = { it },
            physicalIdentity = { null },
        ))
    }

    @Test fun inaccessiblePhysicalIdentityFailsClosedWithoutReadingBytes() = runBlocking {
        var reads = 0
        assertFalse(preflightDuplicateTrash(
            listOf("content://provider/copy"),
            { reads++; true },
            sourceIdentity = { it },
            physicalIdentity = { throw SecurityException("path permission denied") },
        ))
        assertEquals(0, reads)
    }

    @Test fun revokedProviderPermissionDuringVerificationRejectsBatch() = runBlocking {
        val checked = mutableListOf<String>()
        assertFalse(preflightDuplicateTrash(
            listOf("content://provider/copy1", "content://provider/copy2"),
            { item ->
                checked += item
                throw SecurityException("provider permission revoked")
            },
            sourceIdentity = { it },
        ))
        assertEquals(listOf("content://provider/copy1"), checked)
    }

    @Test fun protectedKeeperSetChangedDuringHashVerificationRejectsBatch() = runBlocking {
        val protected = mutableSetOf("content://keeper-old")
        val allowed = preflightDuplicateTrash(
            listOf("content://copy"),
            { protected.clear(); protected.add("content://copy"); true },
            sourceIdentity = { it },
            protectedSourceIdentities = protected,
        )
        assertFalse(allowed)
    }

    @Test fun protectedPhysicalKeeperSetChangedToCanonicalAliasRevokesConsent() = runBlocking {
        val protected = mutableSetOf("/storage/DCIM/keeper.jpg")
        assertFalse(preflightDuplicateTrash(
            listOf("content://copy"),
            {
                // Both paths canonicalize to the same location, but the user's
                // protected-source selection has changed during SHA verification.
                protected.clear()
                protected.add("/storage/DCIM/./keeper.jpg")
                true
            },
            sourceIdentity = { it },
            physicalIdentity = { "/storage/DCIM/copy.jpg" },
            protectedPhysicalIdentities = protected,
        ))
    }

    @Test fun unchangedProtectedPhysicalKeeperSetStillAllowsVerifiedCopy() = runBlocking {
        val protected = mutableSetOf("/storage/DCIM/keeper.jpg")
        assertTrue(preflightDuplicateTrash(
            listOf("content://copy"),
            { true },
            sourceIdentity = { it },
            physicalIdentity = { "/storage/DCIM/copy.jpg" },
            protectedPhysicalIdentities = protected,
        ))
    }

    @Test fun unrelatedKeeperSetChangeAlsoInvalidatesConsent() = runBlocking {
        val protected = mutableSetOf("content://keeper-old")
        val allowed = preflightDuplicateTrash(
            listOf("content://copy"),
            { protected.clear(); protected.add("content://keeper-new"); true },
            sourceIdentity = { it },
            protectedSourceIdentities = protected,
        )
        assertFalse(allowed)
    }

    @Test fun protectedKeeperSnapshotInterruptionPropagates() = runBlocking {
        val interruptedSet = object : AbstractSet<String>() {
            override val size: Int get() = throw InterruptedException("keeper list interrupted")
            override fun iterator(): Iterator<String> = emptyList<String>().iterator()
        }
        var interrupted = false
        try {
            preflightDuplicateTrash(listOf("copy"), { true },
                sourceIdentity = { it }, protectedSourceIdentities = interruptedSet)
        } catch (_: InterruptedException) {
            interrupted = Thread.currentThread().isInterrupted
        } finally {
            Thread.interrupted()
        }
        assertTrue(interrupted)
    }

    @Test fun finalKeeperSnapshotInterruptionPropagates() = runBlocking {
        var snapshots = 0
        val interruptedSet = object : AbstractSet<String>() {
            override val size: Int
                get() {
                    snapshots++
                    if (snapshots > 1) throw InterruptedException("keeper changed during verification")
                    return 1
                }
            override fun iterator(): Iterator<String> = listOf("content://keeper").iterator()
        }
        var interrupted = false
        try {
            preflightDuplicateTrash(listOf("content://copy"), { true },
                sourceIdentity = { it }, protectedSourceIdentities = interruptedSet)
        } catch (_: InterruptedException) {
            interrupted = Thread.currentThread().isInterrupted
        } finally {
            Thread.interrupted()
        }
        assertTrue(interrupted)
    }

    @Test fun finalPhysicalKeeperSnapshotCancellationPropagates() = runBlocking {
        var snapshots = 0
        val cancelledSet = object : AbstractSet<String>() {
            override val size: Int
                get() {
                    snapshots++
                    if (snapshots > 1) throw java.util.concurrent.CancellationException("cancelled")
                    return 1
                }
            override fun iterator(): Iterator<String> = listOf("/storage/keeper.jpg").iterator()
        }
        var cancelled = false
        try {
            preflightDuplicateTrash(listOf("content://copy"), { true },
                sourceIdentity = { it }, protectedPhysicalIdentities = cancelledSet)
        } catch (_: java.util.concurrent.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

}
