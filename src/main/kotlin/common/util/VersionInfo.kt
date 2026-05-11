package com.ua.astrumon.common.util

object VersionInfo {
    const val VERSION = "1.3.1"
    const val BOT_NAME = "Spovishun"
    fun getFullVersion(): String = BOT_NAME + " v" + VERSION
}