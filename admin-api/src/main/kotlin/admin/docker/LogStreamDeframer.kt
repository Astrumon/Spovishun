package com.ua.astrumon.admin.docker

/**
 * Stateful de-multiplexer for a live (`follow=true`) Docker log stream (spovishun-111).
 *
 * Unlike [DockerResponseMapper.deframeLogs], which de-frames a complete buffer, a live stream splits
 * Docker's 8-byte frame headers and payloads across arbitrary TCP chunk boundaries. [feed] accumulates
 * bytes and returns every fully-arrived frame, retaining the incomplete tail for the next call.
 *
 * Assumes the TTY-disabled framed format (the proxy serves non-TTY containers). Not thread-safe —
 * one instance per connection.
 */
class LogStreamDeframer {
    private var buffer = ByteArray(0)

    fun feed(chunk: ByteArray): List<RawLogFrame> {
        if (chunk.isEmpty()) return emptyList()
        buffer += chunk
        val frames = mutableListOf<RawLogFrame>()
        var offset = 0
        while (offset + LogFrameHeader.HEADER_SIZE <= buffer.size && LogFrameHeader.isHeaderAt(buffer, offset)) {
            val start = offset + LogFrameHeader.HEADER_SIZE
            val end = start + LogFrameHeader.sizeAt(buffer, offset)
            if (end > buffer.size) break // payload not fully arrived yet — wait for the next feed
            frames += RawLogFrame(
                streamType = LogFrameHeader.streamTypeAt(buffer, offset),
                payload = String(buffer, start, end - start, Charsets.UTF_8),
            )
            offset = end
        }
        buffer = buffer.copyOfRange(offset, buffer.size)
        return frames
    }
}

/** One de-multiplexed Docker log frame: [streamType] 1=stdout/2=stderr, [payload] as UTF-8 text. */
data class RawLogFrame(
    val streamType: Int,
    val payload: String,
)
