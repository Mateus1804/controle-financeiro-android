package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class ReviewQueueTest {
    @Test fun snapshotStartsWithTheChosenItemAndUsesStableUniqueIds() {
        assertEquals(listOf("b", "c", "a"), ReviewQueue.snapshot(listOf("a", "b", "b", "c"), "b"))
    }
    @Test fun newArrivalsNeverReplaceTheCurrentSnapshot() {
        val queue = ReviewQueue.snapshot(listOf("a", "b"), "a")
        assertEquals("b", ReviewQueue.next(queue, "a", setOf("a", "b", "novo")))
        assertNull(ReviewQueue.next(queue, "b", setOf("novo")))
    }
    @Test fun removedOrAlreadyReviewedItemsAreSkipped() {
        assertEquals("c", ReviewQueue.next(listOf("a", "b", "c"), "a", setOf("c")))
        assertNull(ReviewQueue.next(emptyList(), "ausente", setOf("c")))
    }
}
