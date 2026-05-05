package com.ua.astrumon.common.util

fun String.escapeHtml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
