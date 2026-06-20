package com.ua.astrumon.domain.model

data class BirthDate(
    val day: Int,
    val month: Int,
) {
    init {
        require(month in 1..12) { "Month must be 1..12, got $month" }
        val maxDay = when (month) {
            2 -> 29
            4, 6, 9, 11 -> 30
            else -> 31
        }
        require(day in 1..maxDay) { "Day must be 1..$maxDay for month $month, got $day" }
    }

    fun toMmDd(): Int = month * 100 + day

    fun format(): String = "%02d.%02d".format(day, month)

    companion object {
        fun fromMmDd(encoded: Int): BirthDate = BirthDate(day = encoded % 100, month = encoded / 100)

        fun parse(input: String): BirthDate? {
            val parts = input.split('.')
            if (parts.size != 2) return null
            val day = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            return runCatching { BirthDate(day, month) }.getOrNull()
        }
    }
}
