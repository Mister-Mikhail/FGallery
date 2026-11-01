package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveNamePlannerTest {
    @Test fun noConflictKeepsOriginalName() {
        assertEquals(listOf("IMG_1234.jpg"), MoveNamePlanner.plan(listOf("IMG_1234.jpg"), emptyList()))
    }

    @Test fun caseInsensitiveCollisionsPickNextFreeSuffix() {
        assertEquals(listOf("IMG_1234_4.jpg"), MoveNamePlanner.plan(
            listOf("IMG_1234.jpg"),
            listOf("img_1234.JPG", "IMG_1234_2.jpg", "Img_1234_3.JPG")
        ))
    }

    @Test fun rawJpegPairSharesSuffixWhenOnlyOneExtensionCollides() {
        assertEquals(listOf("IMG_1234_2.CR2", "IMG_1234_2.JPG"), MoveNamePlanner.plan(
            listOf("IMG_1234.CR2", "IMG_1234.JPG"), listOf("img_1234.jpg")
        ))
    }

    @Test fun pairAvoidsCollisionsInBothExtensionsAndBatch() {
        assertEquals(
            listOf("IMG_1234_4.CR2", "IMG_1234_4.JPG", "IMG_1235.JPG"),
            MoveNamePlanner.plan(
                listOf("IMG_1234.CR2", "IMG_1234.JPG", "IMG_1235.JPG"),
                listOf("img_1234.cr2", "IMG_1234_2.JPG", "IMG_1234_3.CR2")
            )
        )
    }

    @Test fun multipleSameNamedSourcesGetDifferentNames() {
        assertEquals(listOf("A.jpg", "A_2.jpg"), MoveNamePlanner.plan(listOf("A.jpg", "A.jpg"), emptyList()))
    }
}
