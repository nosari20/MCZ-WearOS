package com.mczadvanced.wear

import java.util.Locale

fun formatTemp(celsius: Float?): String =
    celsius?.let { String.format(Locale.ROOT, "%.1f°", it) } ?: "--"

fun formatAge(updatedAt: Long?, now: Long): String {
    if (updatedAt == null) return "never"
    val minutes = (now - updatedAt) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ago"
    }
}
