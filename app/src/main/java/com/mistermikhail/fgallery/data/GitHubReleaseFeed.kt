package com.mistermikhail.fgallery.data

import android.content.pm.PackageInfo
import android.text.Html
import android.util.Xml
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

internal const val FGALLERY_RELEASES = "https://github.com/Mister-Mikhail/FGallery/releases"

/** Public release feed, served by GitHub even when the REST API is unreachable. */
internal class GitHubReleaseFeed(
    private val readPage: (String) -> String,
    private val supportedAbis: Array<String>,
) {
    private data class Release(val tag: String, val notes: String)
    private data class Candidate(val release: Release, val version: String, val build: Long)

    fun check(installed: PackageInfo, declined: String?): AvailableUpdate? {
        val candidates = readFeed(readPage("$FGALLERY_RELEASES.atom")).mapNotNull { release ->
            if (release.tag == declined) return@mapNotNull null
            val (version, code) = AppUpdates.releaseVersionAndCode(
                release.tag, release.notes, installed.versionName.orEmpty(), installed.longVersionCode,
            ) ?: return@mapNotNull null
            Candidate(release, version, code).takeIf { code > installed.longVersionCode }
        }.sortedWith(compareByDescending<Candidate> { it.build }.thenBy { it.release.tag.startsWith("qa-") })
        return candidates.firstNotNullOfOrNull(::availableApk)
    }

    private fun availableApk(candidate: Candidate): AvailableUpdate? {
        val (release, version, build) = candidate
        val prefix = "/Mister-Mikhail/FGallery/releases/download/${release.tag}/"
        val assets = Regex("""href="(${Regex.escape(prefix)}[a-zA-Z0-9._-]+\.apk)"""")
            .findAll(readPage("$FGALLERY_RELEASES/expanded_assets/${release.tag}"))
            .map { it.groupValues[1] }.toList()
        val best = (supportedAbis.asList() + "universal").firstNotNullOfOrNull { abi ->
            assets.firstOrNull { it.endsWith("-$abi.apk") }
        } ?: return null
        val digest = AppUpdates.releaseChecksum(best.substringAfterLast('/'), release.notes)
        if (release.tag.startsWith("qa-") && digest == null) return null
        return AvailableUpdate(version, build, release.tag, "https://github.com$best", 0L, digest,
            AppUpdates.releaseChanges(release.notes))
    }

    private fun readFeed(xml: String): List<Release> {
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        check(parser.nextTag() == XmlPullParser.START_TAG && parser.name == "feed") { "Invalid GitHub release feed" }
        return buildList {
            var entryDepth = -1
            var tag: String? = null
            var notes = ""
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when {
                        parser.name == "entry" && parser.depth == 2 -> {
                            entryDepth = parser.depth
                            tag = null
                            notes = ""
                        }
                        entryDepth < 0 || parser.depth != entryDepth + 1 -> Unit
                        parser.name == "link" && parser.getAttributeValue(null, "rel") == "alternate" -> {
                            tag = parser.getAttributeValue(null, "href")?.takeIf { it.startsWith("$FGALLERY_RELEASES/tag/") }
                                ?.substringAfterLast('/')
                        }
                        parser.name == "content" -> {
                            val html = parser.getAttributeValue(null, "type") == "html"
                            val content = parser.nextText()
                            notes = if (html) readableNotes(content) else content
                        }
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.depth == entryDepth) {
                    tag?.let { add(Release(it, notes)) }
                    entryDepth = -1
                }
            }
        }
    }

    private fun readableNotes(html: String): String {
        // GitHub renders the release body as HTML in Atom, including the version and checksum lines.
        val markdown = html
            .replace(Regex("<h[1-6](?:\\s[^>]*)?>", RegexOption.IGNORE_CASE), "\n### ")
            .replace(Regex("</h[1-6]>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<li(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE), "\n- ")
            .replace(Regex("</li>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        return markdown.lineSequence().joinToString("\n") { line ->
            Html.fromHtml(line, Html.FROM_HTML_MODE_COMPACT).toString().trim()
        }
    }
}
