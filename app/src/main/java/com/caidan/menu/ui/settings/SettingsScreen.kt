@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caidan.menu.AppContainer
import com.caidan.menu.data.repo.SettingsRepository
import com.caidan.menu.ui.AppViewModelProvider
import com.caidan.menu.ui.components.SectionTitle

@Composable
fun SettingsScreen(container: AppContainer) {
    val viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.settings(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var keyField by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    // 首次加载时把已保存的 Key 填进输入框
    LaunchedEffect(state.apiKey) {
        if (keyField == null) keyField = state.apiKey
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionTitle("通义千问 API Key")
                Text(
                    "在阿里云百炼（DashScope）控制台创建 API Key 后粘贴到这里。Key 只保存在本机。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedTextField(
                    value = keyField.orEmpty(),
                    onValueChange = { keyField = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("sk-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.saveApiKey(keyField.orEmpty()) }) {
                        Text("保存 Key")
                    }
                    OutlinedButton(onClick = {
                        keyField = ""
                        viewModel.saveApiKey("")
                    }) {
                        Text("清除")
                    }
                }
            }

            item {
                SectionTitle("文字解析模型")
                Text(
                    "用于「AI 解析食材」，qwen-plus 速度与效果较均衡。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ChipGroup(
                    options = SettingsRepository.TEXT_MODELS,
                    selected = state.textModel,
                    onSelect = viewModel::setTextModel,
                )
            }

            item {
                SectionTitle("图片识别模型")
                Text(
                    "用于「从截图生成菜谱」，需要多模态模型。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ChipGroup(
                    options = SettingsRepository.VISION_MODELS,
                    selected = state.visionModel,
                    onSelect = viewModel::setVisionModel,
                )
            }

            item { SectionTitle("数据") }
            item {
                Text(
                    "所有菜谱、照片都保存在本机，不会上传到任何服务器（仅 AI 解析时会把文字或截图发给通义千问）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showClearDialog = true }) {
                    Text("清空全部菜谱数据")
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空全部数据？") },
            text = { Text("所有菜谱、甜点、食材、视频链接和做菜记录都会被删除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearAllData()
                }) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ChipGroup(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option) },
            )
        }
    }
}
