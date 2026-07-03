package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.music.data.playback.PlaybackResumptionStore
import pl.dakil.music.data.playback.PlaybackResumptionStore.Companion.ITEMS_BEFORE_CURRENT
import pl.dakil.music.data.playback.PlaybackResumptionStore.Companion.MAX_PERSISTED_ITEMS
import pl.dakil.music.data.playback.PlaybackResumptionStore.Companion.windowAround

class PlaybackResumptionWindowTest {

    private val hugeQueue = (0L until 2_000L).toList()

    @Test
    fun smallQueue_isKeptWhole() {
        val ids = (0L until 10L).toList()
        assertEquals(ids to 7, windowAround(ids, 7))
    }

    @Test
    fun queueAtLimit_isKeptWhole() {
        val ids = (0L until MAX_PERSISTED_ITEMS.toLong()).toList()
        assertEquals(ids to 250, windowAround(ids, 250))
    }

    @Test
    fun hugeQueue_windowsAroundCurrentItem() {
        val index = 1_000
        val (ids, mapped) = windowAround(hugeQueue, index)
        assertEquals(MAX_PERSISTED_ITEMS, ids.size)
        assertEquals(ITEMS_BEFORE_CURRENT, mapped)
        // The current item survives the windowing under its remapped index.
        assertEquals(hugeQueue[index], ids[mapped])
    }

    @Test
    fun currentNearStart_keepsWindowInBounds() {
        val (ids, mapped) = windowAround(hugeQueue, 5)
        assertEquals(hugeQueue.take(MAX_PERSISTED_ITEMS), ids)
        assertEquals(5, mapped)
    }

    @Test
    fun currentNearEnd_keepsWindowInBounds() {
        val index = hugeQueue.lastIndex
        val (ids, mapped) = windowAround(hugeQueue, index)
        assertEquals(hugeQueue.takeLast(MAX_PERSISTED_ITEMS), ids)
        assertEquals(hugeQueue[index], ids[mapped])
        assertEquals(MAX_PERSISTED_ITEMS - 1, mapped)
    }

    @Test
    fun outOfRangeIndex_isClamped() {
        val ids = listOf(1L, 2L, 3L)
        assertEquals(ids to 2, windowAround(ids, 99))
        assertEquals(ids to 0, windowAround(ids, -1))
    }

    @Test
    fun emptyQueue_returnsEmptyWindow() {
        assertEquals(emptyList<Long>() to 0, windowAround(emptyList(), 0))
    }
}
