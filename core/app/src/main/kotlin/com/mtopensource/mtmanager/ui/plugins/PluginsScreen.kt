// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.mtmanager.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtopensource.plugin.bridge.PluginRecord
import com.mtopensource.plugin.bridge.PluginState
import com.mtopensource.mtmanager.plugin.isActive
import com.mtopensource.mtmanager.plugin.isBuiltin
import com.mtopensource.ui.component.EmptyState

/**
 * 插件管理界面。
 *
 * 展示全部已发现插件及其状态，支持重新扫描与启停切换。
 * 内置插件默认折叠，避免与用户自行安装的插件混淆。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(
    viewModel: PluginsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("插件管理") },
                actions = {
                    IconButton(onClick = viewModel::rescan) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新扫描")
                    }
                },
            )
        },
    ) { padding ->
        if (!state.hasAnyPlugin && !state.isLoading) {
            EmptyState(
                message = "尚未安装任何插件\n将插件包放入应用外部目录后点击刷新",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "已启用 ${state.activeCount} / ${state.plugins.size} 个插件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(state.visiblePlugins, key = { it.manifest.id }) { record ->
                PluginCard(record = record, onToggle = { viewModel.togglePlugin(record) })
            }

            if (!state.showBuiltin) {
                item {
                    Text(
                        text = "部分内置插件已隐藏，可在设置中查看",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginCard(
    record: PluginRecord,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manifest = record.manifest

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = if (record.isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(Modifier.padding(horizontal = 8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = manifest.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (record.isBuiltin) {
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = "内置",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Text(
                    text = "v${manifest.version} · ${record.state.displayText()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (manifest.description.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = manifest.description,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // 加载失败时展示原因，便于排查
                record.errorMessage?.let { error ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Switch(
                checked = record.isActive,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

/** 插件状态的中文展示文案。 */
private fun PluginState.displayText(): String = when (this) {
    PluginState.DISCOVERED -> "待加载"
    PluginState.LOADING -> "加载中"
    PluginState.ACTIVE -> "已启用"
    PluginState.UNLOADED -> "已停用"
    PluginState.INCOMPATIBLE -> "版本不兼容"
    PluginState.ERROR -> "加载失败"
}
