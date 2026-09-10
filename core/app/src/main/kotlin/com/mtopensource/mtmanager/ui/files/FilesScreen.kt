// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.mtmanager.ui.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtopensource.filemanager.model.FileItem
import com.mtopensource.filemanager.model.SortBy
import com.mtopensource.plugin.api.PluginMenuItem
import com.mtopensource.ui.component.EmptyState
import com.mtopensource.ui.component.FileIcon

/**
 * 文件浏览主界面。
 *
 * 交互约定：
 * - 单击进入目录 / 打开文件
 * - 长按进入多选模式
 * - 多选模式下单击切换选中状态
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    onOpenFile: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 各对话框的显示状态
    var showSortMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var contextMenuItem by remember { mutableStateOf<FileItem?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.isSelectionMode) {
                SelectionTopBar(
                    count = state.selectionCount,
                    onClear = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onDelete = viewModel::deleteSelected,
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = state.currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        if (state.canGoUp) {
                            IconButton(onClick = viewModel::navigateUp) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "上级目录")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }

                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                SortBy.entries.forEach { sortBy ->
                                    DropdownMenuItem(
                                        text = {
                                            val marker = if (state.sortConfig.sortBy == sortBy) {
                                                if (state.sortConfig.ascending) " ↑" else " ↓"
                                            } else {
                                                ""
                                            }
                                            Text(sortBy.displayName() + marker)
                                        },
                                        onClick = {
                                            viewModel.setSortBy(sortBy)
                                            showSortMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "操作")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("新建文件夹") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showCreateFolderDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("新建文件") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showCreateFileDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (state.sortConfig.showHidden) "隐藏隐藏文件" else "显示隐藏文件")
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.toggleShowHidden()
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                state.error != null -> EmptyState(
                    message = "无法打开此目录",
                    description = state.error,
                )

                state.items.isEmpty() -> EmptyState(
                    message = "此文件夹为空",
                    description = "点击右上角可新建文件或文件夹",
                )

                else -> LazyColumn(state = listState) {
                    items(state.items, key = { it.path }) { item ->
                        FileRow(
                            item = item,
                            isSelectionMode = state.isSelectionMode,
                            isSelected = item.path in state.selectedPaths,
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.toggleSelection(item.path)
                                } else {
                                    viewModel.open(item, onOpenFile)
                                }
                            },
                            onLongClick = {
                                // 长按进入多选并选中当前项
                                viewModel.enterSelectionMode(item.path)
                            },
                            onShowContextMenu = { contextMenuItem = item },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // 对话框
    // ---------------------------------------------------------------------

    renameTarget?.let { target ->
        TextInputDialog(
            title = "重命名",
            initialValue = target.name,
            confirmText = "确定",
            onConfirm = { newName ->
                viewModel.rename(target, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    if (showCreateFolderDialog) {
        TextInputDialog(
            title = "新建文件夹",
            initialValue = "",
            confirmText = "创建",
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false },
        )
    }

    if (showCreateFileDialog) {
        TextInputDialog(
            title = "新建文件",
            initialValue = "",
            confirmText = "创建",
            onConfirm = { name ->
                viewModel.createFile(name)
                showCreateFileDialog = false
            },
            onDismiss = { showCreateFileDialog = false },
        )
    }

    // 条目上下文菜单：合并内置操作与插件注入的操作
    contextMenuItem?.let { item ->
        val pluginMenus = viewModel.applicablePluginMenus(item)
        ItemContextMenu(
            item = item,
            pluginMenus = pluginMenus,
            onDismiss = { contextMenuItem = null },
            onRename = {
                contextMenuItem = null
                renameTarget = item
            },
            onDelete = {
                contextMenuItem = null
                viewModel.enterSelectionMode(item.path)
                viewModel.deleteSelected()
            },
            onInvokePlugin = { menu ->
                contextMenuItem = null
                viewModel.invokePluginMenu(menu, listOf(item.path))
            },
        )
    }
}

/**
 * 多选模式下的顶部工具栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text("已选择 $count 项") },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "取消选择")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = "全选")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

/**
 * 单行文件条目。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowContextMenu: () -> Unit,
) {
    val background = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = if (isSelected) "已选择" else "未选择",
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
        } else {
            FileIcon(
                item = item,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.size(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.isDirectory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (item.isDirectory) "文件夹" else item.readableSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.lastModified > 0) {
                    Text(
                        text = item.readableModified,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 非多选模式下提供「更多」入口，避免必须长按才能发现插件功能
        if (!isSelectionMode) {
            IconButton(onClick = onShowContextMenu) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 条目操作菜单。
 *
 * 内置操作（重命名、删除）与插件注入的操作在此合并展示，
 * 插件项带「插件：」前缀以区分来源。
 */
@Composable
private fun ItemContextMenu(
    item: FileItem,
    pluginMenus: List<PluginMenuItem>,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onInvokePlugin: (PluginMenuItem) -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider()

        DropdownMenuItem(text = { Text("重命名") }, onClick = onRename)
        DropdownMenuItem(
            text = { Text("删除") },
            onClick = onDelete,
        )

        if (pluginMenus.isNotEmpty()) {
            HorizontalDivider()
            pluginMenus.sortedBy { it.order }.forEach { menu ->
                DropdownMenuItem(
                    text = { Text(menu.title) },
                    onClick = { onInvokePlugin(menu) },
                )
            }
        }
    }
}

/**
 * 通用文本输入对话框（新建 / 重命名共用）。
 */
@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 排序字段的中文名称。 */
private fun SortBy.displayName(): String = when (this) {
    SortBy.NAME -> "按名称"
    SortBy.SIZE -> "按大小"
    SortBy.MODIFIED -> "按修改时间"
    SortBy.TYPE -> "按类型"
}
