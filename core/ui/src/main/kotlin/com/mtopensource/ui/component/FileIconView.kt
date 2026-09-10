// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import com.mtopensource.filemanager.model.FileItem

/**
 * 文件类型图标。
 *
 * 采用「彩色圆角底 + 白色图标」的统一视觉，
 * 让不同类型的文件在长列表中仍能被快速区分。
 *
 * @param size 图标的整体尺寸（含底色方块）
 */
@Composable
fun FileIcon(
    item: FileItem,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val category = FileCategoryResolver.resolve(item.extension, item.isDirectory)
    FileIcon(
        category = category,
        modifier = modifier,
        size = size,
        extensionLabel = item.extension,
    )
}

/**
 * 按分类渲染图标。
 */
@Composable
fun FileIcon(
    category: FileCategory,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    extensionLabel: String = "",
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 5))
            .background(category.color),
        contentAlignment = Alignment.Center,
    ) {
        // 对于「其他」类型，展示扩展名比展示通用图标更有信息量
        val label = extensionLabel.lowercase()
        if (category == FileCategory.UNKNOWN && label.isNotEmpty() && label.length <= 4) {
            Text(
                text = label.uppercase(),
                color = Color.White,
                fontSize = (size.value / 3.6f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        } else {
            Icon(
                imageVector = category.icon(),
                contentDescription = category.displayName,
                tint = Color.White,
                modifier = Modifier.size(size * 0.58f),
            )
        }
    }
}

/**
 * 分类到图标的映射。
 *
 * 使用 Material Icons 核心集，避免引入体积庞大的 extended 依赖
 * （仅 `Folder` 等个别图标来自 core，其余均可在 core 集中找到）。
 */
private fun FileCategory.icon(): ImageVector = when (this) {
    FileCategory.DIRECTORY -> Icons.Default.Folder
    FileCategory.APK -> Icons.Default.Android
    FileCategory.ARCHIVE -> Icons.Default.Archive
    FileCategory.IMAGE -> Icons.Default.Image
    FileCategory.VIDEO -> Icons.Default.Videocam
    FileCategory.AUDIO -> Icons.Default.MusicNote
    FileCategory.DOCUMENT -> Icons.Default.Description
    FileCategory.CODE -> Icons.Default.Code
    FileCategory.TEXT -> Icons.Default.Description
    FileCategory.DATABASE -> Icons.Default.Storage
    FileCategory.UNKNOWN -> Icons.Default.Description
}

/** 分类对应的主题色，供外部（如标签）复用。 */
@Composable
fun FileCategory.contentColor(): Color = this.color

/** 列表项次要文字颜色。 */
@Composable
fun secondaryTextColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant
