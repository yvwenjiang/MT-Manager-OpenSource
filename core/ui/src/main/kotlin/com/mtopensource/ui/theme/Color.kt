// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// 亮色主题
// ---------------------------------------------------------------------------
val LightPrimary = Color(0xFF1565C0)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD6E4FF)
val LightOnPrimaryContainer = Color(0xFF001B3F)

val LightSecondary = Color(0xFF00897B)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFB2DFDB)
val LightOnSecondaryContainer = Color(0xFF00201C)

val LightTertiary = Color(0xFF6A4C93)
val LightOnTertiary = Color(0xFFFFFFFF)

val LightBackground = Color(0xFFF7F9FC)
val LightOnBackground = Color(0xFF1A1C1E)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1A1C1E)
val LightSurfaceVariant = Color(0xFFE3E6EB)
val LightOnSurfaceVariant = Color(0xFF44474C)
val LightOutline = Color(0xFFC4C6CF)

val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFF9DEDC)

// ---------------------------------------------------------------------------
// 暗色主题
// ---------------------------------------------------------------------------
val DarkPrimary = Color(0xFFA5C8FF)
val DarkOnPrimary = Color(0xFF003062)
val DarkPrimaryContainer = Color(0xFF00458C)
val DarkOnPrimaryContainer = Color(0xFFD6E4FF)

val DarkSecondary = Color(0xFF4DB6AC)
val DarkOnSecondary = Color(0xFF003731)
val DarkSecondaryContainer = Color(0xFF005048)
val DarkOnSecondaryContainer = Color(0xFFB2DFDB)

val DarkTertiary = Color(0xFFCEB6F0)
val DarkOnTertiary = Color(0xFF3A1D5C)

val DarkBackground = Color(0xFF121316)
val DarkOnBackground = Color(0xFFE3E2E6)
val DarkSurface = Color(0xFF1B1C1F)
val DarkOnSurface = Color(0xFFE3E2E6)
val DarkSurfaceVariant = Color(0xFF44474C)
val DarkOnSurfaceVariant = Color(0xFFC4C6CF)
val DarkOutline = Color(0xFF8E9099)

val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF8C1D18)

// ---------------------------------------------------------------------------
// 编辑器专用配色（代码高亮 / 差异对比）
// ---------------------------------------------------------------------------
object EditorColors {
    // 亮色
    val LightKeyword = Color(0xFF0033B3)
    val LightType = Color(0xFF00627A)
    val LightString = Color(0xFF067D17)
    val LightNumber = Color(0xFF1750EB)
    val LightComment = Color(0xFF8C8C8C)
    val LightAnnotation = Color(0xFF9E880D)
    val LightFunction = Color(0xFF7A3E9D)
    val LightOperator = Color(0xFF333333)
    val LightGutter = Color(0xFFF0F0F0)
    val LightGutterText = Color(0xFF9AA0A6)
    val LightCurrentLine = Color(0x14000000)
    val LightSearchHighlight = Color(0xFFFFE082)
    val LightSelection = Color(0x332196F3)

    // 暗色
    val DarkKeyword = Color(0xFFCF8E6D)
    val DarkType = Color(0xFF56A8F5)
    val DarkString = Color(0xFF6AAB73)
    val DarkNumber = Color(0xFF2AACB8)
    val DarkComment = Color(0xFF7A7E85)
    val DarkAnnotation = Color(0xFFB3AE60)
    val DarkFunction = Color(0xFFC77DBB)
    val DarkOperator = Color(0xFFBCBEC4)
    val DarkGutter = Color(0xFF24252A)
    val DarkGutterText = Color(0xFF6F737A)
    val DarkCurrentLine = Color(0x14FFFFFF)
    val DarkSearchHighlight = Color(0x66FFD54F)
    val DarkSelection = Color(0x332196F3)

    // 差异对比
    val DiffAdded = Color(0x332E7D32)
    val DiffRemoved = Color(0x33C62828)
    val DiffModified = Color(0x33F9A825)
}
