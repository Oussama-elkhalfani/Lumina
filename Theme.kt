package com.lumina.tv

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Pal {
    val Bg = Color(0xFF090909)
    val Surface = Color(0xFF151515)
    val Accent = Color(0xFF0A84FF)
    val Sub = Color(0xFF9A9AA0)
    val Border = Color(0x1AFFFFFF)
}

private val PALETTE = listOf(
    Color(0xFF1B2A49) to Color(0xFF0A0A12),
    Color(0xFF3A1F3D) to Color(0xFF0B0A10),
    Color(0xFF16363A) to Color(0xFF080C0D),
    Color(0xFF3B2A1B) to Color(0xFF0D0A08),
    Color(0xFF22284F) to Color(0xFF09090F),
    Color(0xFF2F2F34) to Color(0xFF0A0A0B)
)

fun gradientFor(id: Int): Brush {
    val p = PALETTE[Math.floorMod(id, PALETTE.size)]
    return Brush.linearGradient(listOf(p.first, p.second))
}
