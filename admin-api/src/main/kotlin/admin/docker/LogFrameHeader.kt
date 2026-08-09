package com.ua.astrumon.admin.docker

/**
 * Shared reader for Docker's 8-byte log stream frame header (spovishun-111).
 *
 * With TTY disabled, Docker prefixes each chunk with `[streamType, 0, 0, 0, size(uint32 big-endian)]`.
 * Centralizes the bit-level parsing so both the buffered [DockerResponseMapper.deframeLogs] and the
 * streaming [LogStreamDeframer] read frames the same way (DRY).
 */
internal object LogFrameHeader {
    const val HEADER_SIZE = 8
    private const val MAX_STREAM_TYPE = 2

    // A valid frame header has a known stream type in byte 0 and zero padding in bytes 1..3.
    fun isHeaderAt(
        raw: ByteArray,
        offset: Int,
    ): Boolean = (raw[offset].toInt() and 0xFF) <= MAX_STREAM_TYPE &&
        raw[offset + 1].toInt() == 0 &&
        raw[offset + 2].toInt() == 0 &&
        raw[offset + 3].toInt() == 0

    fun streamTypeAt(
        raw: ByteArray,
        offset: Int,
    ): Int = raw[offset].toInt() and 0xFF

    fun sizeAt(
        raw: ByteArray,
        offset: Int,
    ): Int = ((raw[offset + 4].toInt() and 0xFF) shl 24) or
        ((raw[offset + 5].toInt() and 0xFF) shl 16) or
        ((raw[offset + 6].toInt() and 0xFF) shl 8) or
        (raw[offset + 7].toInt() and 0xFF)
}
