package com.ua.astrumon.admin.docker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogStreamDeframerTest {
    private fun frame(
        streamType: Byte,
        text: String,
    ): ByteArray {
        val payload = text.toByteArray()
        val size = payload.size
        val header = byteArrayOf(
            streamType,
            0,
            0,
            0,
            (size ushr 24).toByte(),
            (size ushr 16).toByte(),
            (size ushr 8).toByte(),
            size.toByte(),
        )
        return header + payload
    }

    @Test
    fun should_returnSingleFrame_when_chunkHoldsCompleteFrame() {
        val frames = LogStreamDeframer().feed(frame(1, "hello"))

        assertEquals(1, frames.size)
        assertEquals(1, frames.first().streamType)
        assertEquals("hello", frames.first().payload)
    }

    @Test
    fun should_returnBothFrames_when_chunkHoldsMultipleFrames() {
        val frames = LogStreamDeframer().feed(frame(1, "hello") + frame(2, "world"))

        assertEquals(listOf("hello", "world"), frames.map { it.payload })
        assertEquals(listOf(1, 2), frames.map { it.streamType })
    }

    @Test
    fun should_bufferUntilComplete_when_frameSplitAcrossChunks() {
        val full = frame(1, "split-line")
        val deframer = LogStreamDeframer()

        val firstHalf = deframer.feed(full.copyOfRange(0, 5))
        val secondHalf = deframer.feed(full.copyOfRange(5, full.size))

        assertTrue(firstHalf.isEmpty())
        assertEquals(1, secondHalf.size)
        assertEquals("split-line", secondHalf.first().payload)
    }

    @Test
    fun should_emitFirstAndBufferSecond_when_chunkEndsMidFrame() {
        val combined = frame(1, "one") + frame(2, "two")
        val deframer = LogStreamDeframer()

        // Cut three bytes into the second frame's header.
        val cut = frame(1, "one").size + 3
        val first = deframer.feed(combined.copyOfRange(0, cut))
        val rest = deframer.feed(combined.copyOfRange(cut, combined.size))

        assertEquals(listOf("one"), first.map { it.payload })
        assertEquals(listOf("two"), rest.map { it.payload })
    }

    @Test
    fun should_returnEmpty_when_chunkIsEmpty() {
        assertTrue(LogStreamDeframer().feed(ByteArray(0)).isEmpty())
    }
}
