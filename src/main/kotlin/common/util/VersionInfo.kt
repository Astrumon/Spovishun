package com.ua.astrumon.common.util

object VersionInfo {
    private const val VERSION = "1.3.0"
    const val BOT_NAME = "Spovishun"
    fun getFullVersion(): String = BOT_NAME + " v" + VERSION
}
