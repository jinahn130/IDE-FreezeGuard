package com.github.jinahn130.intellijfreezeguard

object Bytes {
    fun human(n: Long): String {
        val units = arrayOf("B","KiB","MiB","GiB","TiB")
        var v = kotlin.math.abs(n.toDouble())
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        val sign = if (n < 0) "-" else ""
        return "%s%.1f %s".format(sign, v, units[i])
    }
}
