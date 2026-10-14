@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.fridge

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caidan.menu.AppContainer
import com.caidan.menu.data.local.entity.FridgeItem
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.ui.AppViewModelProvider
import com.caidan.menu.ui.components.ChipRow
import com.caidan.menu.ui.components.CoverImage
import com.caidan.menu.ui.components.LoadingOverlay
import com.caidan.menu.ui.components.PageHeader
import com.caidan.menu.ui.components.PhotoCropper
import com.caidan.menu.ui.components.SectionTitle
import com.caidan.menu.util.ImageStore
import com.caidan.menu.util.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 冰箱条目的新增/编辑页。
 * 新增时可以先用小票或买菜截图让 AI 认名称和数量，再统一补分类和保质期；
 * 也可以完全手动填。
 */
@Composable
fun FridgeEditScreen(
    container: AppContainer,
    onClose: () -> Unit,
) {
    val viewModel: FridgeEditViewModel =
        viewModel(factory = AppViewModelProvider.fridgeEdit(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 这张图是给谁用的。拍照得先备好中转 URI 再喊相机，回来时要知道往哪儿送，
    // 所以相册和相机两条路都认它。
    var target by remember { mutableStateOf(PhotoTarget.RECEIPT) }
    // 相机的输出位置：拍之前就定好，拍完从这儿读回来
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    // 正在问「拍照还是从相册选」
    var askingSource by remember { mutableStateOf(false) }
    // 已经拿到图、正等用户在裁剪页里定构图。null = 没在裁
    var croppingPath by remember { mutableStateOf<String?>(null) }

    val hasCamera = remember { ImageStore.canTakePhoto(context) }

    fun usePhoto(path: String) {
        when (target) {
            PhotoTarget.RECEIPT -> viewModel.importFromImage(path, FridgeItem.SOURCE_RECEIPT)
            PhotoTarget.SCREENSHOT -> viewModel.importFromImage(path, FridgeItem.SOURCE_SCREENSHOT)
            // 条目照片先进 1:1 裁剪页，让用户自己定构图
            PhotoTarget.ITEM -> croppingPath = path
        }
    }

    /**
     * @param tempSource 这张图是不是我们自己 FileProvider 里的中转文件（相机拍的）。
     *   是的话复制完就把它删掉，免得 cache 越堆越多。相册来的 content:// 绝不能碰。
     */
    fun acceptUri(uri: Uri, tempSource: Boolean = false) {
        scope.launch {
            val path = withContext(Dispatchers.IO) { ImageStore.copyIntoStore(context, uri) }
            // 必须在复制**之后**删：copyIntoStore 是挂起的，先删就把源文件抽走了
            if (tempSource) {
                withContext(Dispatchers.IO) { runCatching { uri.path?.let { File(it).delete() } } }
            }
            if (path == null) {
                snackbar.showSnackbar("图片读取失败，请重新选一张")
            } else {
                usePhoto(path)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(::acceptUri) }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = captureUri
        captureUri = null
        // 用户在相机里按了取消：什么都没拍，别去读那个空文件
        if (ok && uri != null) acceptUri(uri, tempSource = true)
    }

    fun pickFor(t: PhotoTarget) {
        target = t
        // 这台设备没相机应用就直接走相册，别让人白点一下
        if (hasCamera) askingSource = true else picker.launch(PICK_IMAGE_ONLY)
    }

    fun takePhoto() {
        askingSource = false
        val uri = runCatching { ImageStore.newCaptureUri(context) }.getOrNull()
        if (uri == null) {
            scope.launch { snackbar.showSnackbar("没法准备拍照文件，改用相册选图") }
            picker.launch(PICK_IMAGE_ONLY)
            return
        }
        captureUri = uri
        runCatching { camera.launch(uri) }.onFailure {
            captureUri = null
            scope.launch { snackbar.showSnackbar("这台设备没有可用的相机应用") }
        }
    }

    if (askingSource) {
        AlertDialog(
            onDismissRequest = { askingSource = false },
            title = { Text("图片来源") },
            text = { Text("现拍一张，或从相册 / 截图里挑一张。") },
            confirmButton = {
                TextButton(onClick = ::takePhoto) { Text("拍照") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        askingSource = false
                        picker.launch(PICK_IMAGE_ONLY)
                    }
                ) { Text("从相册选") }
            },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.saved.collect { onClose() }
    }

    val kindLabel = FridgeItem.bigCategoryLabel(state.bigCategory)

    // 裁剪是个整屏的模态步骤，直接盖掉下面整套界面。
    // 取消时不删已落地的原图 —— 相机拍的那张系统相册里没有，删了就真没了
    val pendingCrop = croppingPath
    if (pendingCrop != null) {
        PhotoCropper(
            path = pendingCrop,
            onCancel = { croppingPath = null },
            onCropped = { cropped ->
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        ImageStore.saveBitmap(context, cropped, "cover")
                    }
                    cropped.recycle()
                    croppingPath = null
                    // 存不下就退回用没裁的原图，别让用户白裁一遍
                    viewModel.onPhotoSelected(saved ?: pendingCrop)
                }
            },
        )
        return
    }

    Scaffold(
        topBar = {
            // 和外面五个主页用同一个 PageHeader，免得进编辑页时头顶跳一下
            PageHeader(
                title = if (state.isEditing) "编辑$kindLabel" else "添加$kindLabel",
                subtitle = "填完点右上角保存",
                navigationIcon = {
                    IconButton(onClick = onClose) {
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
                if (!state.isEditing) {
                    item {
                        AiImportCard(
                            loading = state.loading,
                            onPickReceipt = { pickFor(PhotoTarget.RECEIPT) },
                            onPickScreenshot = { pickFor(PhotoTarget.SCREENSHOT) },
                        )
                    }
                }

                if (state.batching) {
                    item {
                        DraftListCard(
                            drafts = state.drafts,
                            onUpdate = viewModel::updateDraft,
                            onRemove = viewModel::removeDraft,
                            onAdd = viewModel::addDraft,
                            onClearAll = viewModel::clearDrafts,
                        )
                    }
                } else {
                    item {
                        Column {
                            OutlinedTextField(
                                value = state.name,
                                onValueChange = viewModel::onNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("名称") },
                                placeholder = { Text("牛肉 / 生抽 / 西兰花") },
                                singleLine = true,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = state.quantity,
                                    onValueChange = viewModel::onQuantityChange,
                                    modifier = Modifier.weight(1f),
                                    label = { Text("数量") },
                                    placeholder = { Text("500") },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = state.unit,
                                    onValueChange = viewModel::onUnitChange,
                                    modifier = Modifier.weight(1f),
                                    label = { Text("单位") },
                                    placeholder = { Text("克 / 个 / 瓶") },
                                    singleLine = true,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            // 批量整理时多条共用一张图没意义，这行只在单条模式出现
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PhotoSlot(
                                    path = state.photoPath,
                                    onClick = { pickFor(PhotoTarget.ITEM) },
                                )
                                if (!state.photoPath.isNullOrBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(onClick = { viewModel.onPhotoSelected(null) }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "去掉图片")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        SectionTitle("归类", modifier = Modifier.padding(top = 0.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            val options = FridgeItem.BIG_CATEGORIES
                            options.forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    selected = state.bigCategory == value,
                                    onClick = { viewModel.onBigCategoryChange(value) },
                                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                                ) { Text(label) }
                            }
                        }
                        if (state.isIngredient) {
                            Spacer(Modifier.height(8.dp))
                            ChipRow(
                                options = FridgeItem.CATEGORIES,
                                selected = state.category.takeIf { it.isNotBlank() },
                                onSelect = { viewModel.onCategoryChange(it.orEmpty()) },
                                contentPadding = PaddingValues(0.dp),
                            )
                        }
                    }
                }

                item {
                    Column {
                        SectionTitle("保质期", modifier = Modifier.padding(top = 0.dp))
                        Text(
                            text = "过期日期用来做临期提醒，不填就不会提醒。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        DateField(
                            label = "购买日期",
                            value = state.purchaseDate,
                            onChange = viewModel::onPurchaseDateChange,
                        )
                        Spacer(Modifier.height(8.dp))
                        DateField(
                            label = "过期日期",
                            value = state.expiryDate,
                            onChange = viewModel::onExpiryDateChange,
                        )
                    }
                }

                item {
                    RecipePicker(
                        recipes = recipes,
                        selectedId = state.recipeId,
                        onSelect = viewModel::onRecipeChange,
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = viewModel::onNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注（可选）") },
                        placeholder = { Text("放在冷冻层 / 开了封…") },
                        minLines = 2,
                    )
                }

                item {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave && !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.batching) "保存 ${state.drafts.size} 条" else "保存")
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            if (state.loading) {
                LoadingOverlay(message = state.loadingMessage)
            }
        }
    }
}

/** 小票 / 买菜截图的识别入口。识别只给出名称和数量，分类与保质期由用户填。 */
@Composable
private fun AiImportCard(
    loading: Boolean,
    onPickReceipt: () -> Unit,
    onPickScreenshot: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("AI 录入", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "现拍张小票、或从相册选一张买菜订单截图，自动认出名称和数量；" +
                    "分区和保质期你自己填。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickReceipt, enabled = !loading) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("小票识别")
                }
                OutlinedButton(onClick = onPickScreenshot, enabled = !loading) {
                    Icon(
                        Icons.Filled.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("买菜截图")
                }
            }
        }
    }
}

/**
 * 条目的照片位。空着的时候它本身就是「加张图」按钮，点一下去选；
 * 有图了就显示图，再点还是去换。
 *
 * 原来是「一个灰占位图 + 旁边一个『加张图』按钮」，中间还隔着 12dp ——
 * 那个占位图本来就是给这张照片留的位置，入口塞进它里面更直接，
 * 也省掉一行白白占的宽度。
 */
@Composable
private fun PhotoSlot(path: String?, onClick: () -> Unit) {
    val hasPhoto = !path.isNullOrBlank()
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (hasPhoto) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .border(
                width = 1.dp,
                color = if (hasPhoto) Color.Transparent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (hasPhoto) {
            CoverImage(path = path, modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "加张图",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 这次选的图是给谁用的：给小票识别、给买菜截图识别，还是当条目自己的照片。 */
private enum class PhotoTarget { RECEIPT, SCREENSHOT, ITEM }

/** 相册选图固定只要图片，抽出来免得三处各写一遍。 */
private val PICK_IMAGE_ONLY =
    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

/** 识别回来的多条食材，逐条核对、改数量、删掉不想要的。 */
@Composable
private fun DraftListCard(
    drafts: List<DraftItem>,
    onUpdate: (Long, String, String, String) -> Unit,
    onRemove: (Long) -> Unit,
    onAdd: () -> Unit,
    onClearAll: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("识别结果（${drafts.size} 条）", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClearAll) { Text("改手动录入") }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "下面的归类、保质期和备注会套用到这 ${drafts.size} 条上。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            drafts.forEach { draft ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { onUpdate(draft.localId, it, draft.quantity, draft.unit) },
                        modifier = Modifier.weight(2f),
                        placeholder = { Text("名称") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.quantity,
                        onValueChange = { onUpdate(draft.localId, draft.name, it, draft.unit) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("数量") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.unit,
                        onValueChange = { onUpdate(draft.localId, draft.name, draft.quantity, it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("单位") },
                        singleLine = true,
                    )
                    IconButton(onClick = { onRemove(draft.localId) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删掉这条")
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("再加一条")
            }
        }
    }
}

/** 一个可清空的日期选择按钮。 */
@Composable
private fun DateField(
    label: String,
    value: Long?,
    onChange: (Long?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = if (value == null) "选择$label" else "$label：${formatDate(value)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (value != null) {
            IconButton(onClick = { onChange(null) }) {
                Icon(Icons.Filled.Clear, contentDescription = "清除$label")
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = value)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(toLocalDayStart(it)) }
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** 可选的「关联菜谱」下拉；选中的菜谱会显示在库存条目上。 */
@Composable
private fun RecipePicker(
    recipes: List<Recipe>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = recipes.firstOrNull { it.id == selectedId }?.name

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = selectedName?.let { "关联菜谱：$it" } ?: "关联菜谱（可选）",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("不关联") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            recipes.forEach { recipe ->
                DropdownMenuItem(
                    text = { Text(recipe.name) },
                    onClick = {
                        onSelect(recipe.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * DatePicker 给的是 UTC 当天 0 点，这里换成本地时区的当天 0 点，
 * 否则在 UTC 以西的时区里，「还剩几天」会算少一天。
 */
private fun toLocalDayStart(utcMillis: Long): Long =
    Instant.ofEpochMilli(utcMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
