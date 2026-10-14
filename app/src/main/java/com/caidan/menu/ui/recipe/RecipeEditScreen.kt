@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.recipe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caidan.menu.AppContainer
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.ui.AppViewModelProvider
import com.caidan.menu.ui.components.CoverImage
import com.caidan.menu.ui.components.LoadingOverlay
import com.caidan.menu.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecipeEditScreen(
    container: AppContainer,
    onDone: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: RecipeEditViewModel = viewModel(factory = AppViewModelProvider.recipeEdit(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.saved.collect { onDone(it) }
    }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { ImageStore.copyIntoStore(context, uri) }
            path?.let(viewModel::onCoverSelected)
        }
    }

    val screenshotPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { ImageStore.copyIntoStore(context, uri) }
            path?.let(viewModel::parseScreenshot)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.recipeId > 0) "编辑菜谱" else "新建菜谱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = state.canSave && !state.loading,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.initialized) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 类型选择
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("类型", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(12.dp))
                        FilterChip(
                            selected = !state.isDessert,
                            onClick = { viewModel.onTypeChange(Recipe.TYPE_DISH) },
                            label = { Text("家常菜") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = state.isDessert,
                            onClick = { viewModel.onTypeChange(Recipe.TYPE_DESSERT) },
                            label = { Text("甜点") },
                        )
                    }
                }

                // AI 录入区
                item {
                    AiToolsCard(
                        pasteText = state.pasteText,
                        linkText = state.linkText,
                        loading = state.loading,
                        onPasteChange = viewModel::onPasteTextChange,
                        onLinkChange = viewModel::onLinkChange,
                        onParseText = viewModel::parsePastedText,
                        onImportUrl = viewModel::importFromUrl,
                        onPickScreenshot = {
                            screenshotPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    )
                }

                // 基本信息
                item {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("菜名 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.category,
                        onValueChange = viewModel::onCategoryChange,
                        label = { Text("分类（如：川菜、快手菜、蛋糕）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = viewModel::onDescriptionChange,
                        label = { Text("简介 / 特色说明") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }

                // 甜点专属字段
                if (state.isDessert) {
                    item {
                        Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )) {
                            Column(Modifier.padding(12.dp)) {
                                Text("烘焙参数（可选）", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NumberField("温度 °C", state.bakingTemp, viewModel::onBakingTempChange, Modifier.weight(1f))
                                    NumberField("时间 分钟", state.bakingTime, viewModel::onBakingTimeChange, Modifier.weight(1f))
                                    NumberField("份量", state.servings, viewModel::onServingsChange, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // 封面
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoverImage(
                            path = state.coverPhotoPath,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = {
                            coverPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.coverPhotoPath.isNullOrBlank()) "选择封面" else "更换封面")
                        }
                    }
                }

                item { SectionHeader("原材料", hint = "AI 识别后请核对用量") }

                items(state.ingredients, key = { it.localId }) { item ->
                    IngredientRow(
                        draft = item,
                        onChange = { n, q, u -> viewModel.updateIngredient(item.localId, n, q, u) },
                        onRemove = { viewModel.removeIngredient(item.localId) },
                    )
                }
                item {
                    TextButton(onClick = viewModel::addIngredient) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加食材")
                    }
                }

                item { SectionHeader("做法步骤") }
                item {
                    OutlinedTextField(
                        value = state.steps,
                        onValueChange = viewModel::onStepsChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        placeholder = { Text("1. 热锅下油…\n2. …") },
                    )
                }

                item { SectionHeader("视频教学链接") }
                items(state.videos, key = { it.localId }) { item ->
                    VideoRow(
                        draft = item,
                        onChange = { t, u -> viewModel.updateVideo(item.localId, t, u) },
                        onRemove = { viewModel.removeVideo(item.localId) },
                    )
                }
                item {
                    TextButton(onClick = viewModel::addVideo) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加视频链接")
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }

            if (state.loading) {
                LoadingOverlay(message = state.loadingMessage)
            }
        }
    }
}

@Composable
private fun AiToolsCard(
    pasteText: String,
    linkText: String,
    loading: Boolean,
    onPasteChange: (String) -> Unit,
    onLinkChange: (String) -> Unit,
    onParseText: () -> Unit,
    onImportUrl: () -> Unit,
    onPickScreenshot: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "AI 快速录入",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "粘贴菜谱文字、菜谱网页链接，或直接选一张菜谱截图，让 AI 帮你整理。" +
                    "在别的 App 里看到菜谱，也可以直接「分享」到本应用。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pasteText,
                onValueChange = onPasteChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                placeholder = { Text("在此粘贴菜谱文字…") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = linkText,
                onValueChange = onLinkChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("菜谱网页链接") },
                placeholder = { Text("https://…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                trailingIcon = {
                    if (linkText.isNotEmpty()) {
                        IconButton(onClick = { onLinkChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清空链接")
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            // 三个按钮在窄屏上放不下，允许横向滑动
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onImportUrl, enabled = !loading) {
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("从链接导入")
                }
                OutlinedButton(onClick = onParseText, enabled = !loading) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AI 整理菜谱")
                }
                OutlinedButton(onClick = onPickScreenshot, enabled = !loading) {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("从截图生成")
                }
            }
        }
    }
}

@Composable
private fun IngredientRow(
    draft: IngredientDraft,
    onChange: (String, String, String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onChange(it, draft.quantity, draft.unit) },
            modifier = Modifier.weight(1.4f),
            placeholder = { Text("食材") },
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.quantity,
            onValueChange = { onChange(draft.name, it, draft.unit) },
            modifier = Modifier.weight(0.8f),
            placeholder = { Text("用量") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = draft.unit,
            onValueChange = { onChange(draft.name, draft.quantity, it) },
            modifier = Modifier.weight(0.7f),
            placeholder = { Text("单位") },
            singleLine = true,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "删除食材")
        }
    }
}

@Composable
private fun VideoRow(
    draft: VideoDraft,
    onChange: (String, String) -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { onChange(it, draft.url) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("标题（可选）") },
                singleLine = true,
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "删除视频链接")
            }
        }
        OutlinedTextField(
            value = draft.url,
            onValueChange = { onChange(draft.title, it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("链接地址 https://…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun SectionHeader(text: String, hint: String? = null) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

