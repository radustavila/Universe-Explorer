package com.radustavila.nasaexplorer.feature.earth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HdFramePreloaderTest {

    private val urls = listOf("a", "b", "c", "d")

    @Test
    fun `downloads every frame, the ones due soonest first`() = runTest {
        val loader = RecordingFrameImageLoader()

        HdFramePreloader(urls, loader).preload(currentIndex = 1)

        assertEquals(listOf("c", "d", "a", "b"), loader.downloads)
    }

    @Test
    fun `a frame is ready once downloaded and decoded at the frame size`() = runTest {
        val loader = RecordingFrameImageLoader(pending = setOf("c"))
        val preloader = HdFramePreloader(urls, loader)
        backgroundScope.launch { preloader.preload(currentIndex = 0) }

        val ready = async { preloader.awaitFrame(index = 2, sizePx = 1_080) }
        runCurrent()
        assertFalse(ready.isCompleted)

        loader.finish("c")
        ready.await()
        assertEquals(listOf("c" to 1_080), loader.decodes)
    }
}

/** Downloads at once, except the [pending] ones, which wait for [finish]. */
internal class RecordingFrameImageLoader(pending: Set<String> = emptySet()) : FrameImageLoader {
    val downloads = mutableListOf<String>()
    val decodes = mutableListOf<Pair<String, Int>>()
    private val gates = pending.associateWith { CompletableDeferred<Unit>() }

    fun finish(url: String) {
        gates.getValue(url).complete(Unit)
    }

    override suspend fun download(url: String) {
        downloads += url
        gates[url]?.await()
    }

    override suspend fun decode(url: String, sizePx: Int) {
        decodes += url to sizePx
    }
}
