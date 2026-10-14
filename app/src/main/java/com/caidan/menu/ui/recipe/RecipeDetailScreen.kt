@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.recipe

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caidan.menu.AppContainer
import com.caidan.menu.data.local.entity.CookRecord
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.ui.AppViewModelProvider
import com.caidan.menu.ui.components.CoverImage
import com.caidan.menu.ui.components.EmptyState
import com.caidan.menu.ui.components.SectionTitle
import com.caidan.menu.ui.components.StarRating
import com.caidan.menu.ui.components.StarRatingInput
import com.caidan.menu.ui.components.Tag
import com.caidan.menu.util.ImageStore
import com.caidan.menu.util.formatAmount
import com.caidan.menu.util.formatDateTime
import com.caidan.menu.util.formatRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecipeDetailScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val viewModel: RecipeDetailViewModel = viewModel(factory = AppViewModelProvider.recipeDetail(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCookDialog by remember { mutableStateOf(false) }
    // 正在编辑的成品记录；null 表示编辑框关着
    var editingRecord by remember { mutableStateOf<CookRecord?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.deleted.collect { onBack() }
    }

    val recipe = state.recipe
    if (state.loaded && recipe == null) {
        // 记录已被删除
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.name ?: "详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (recipe != null) {
                        IconButton(onClick = { onEdit(recipe.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("删除这道菜") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (recipe == null) return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                CoverImage(
                    path = recipe.coverPhotoPath,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(recipe.name, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag(if (recipe.type == Recipe.TYPE_DESSERT) "甜点" else "家常菜")
                        if (recipe.category.isNotBlank()) Tag(recipe.category)
                        Tag(if (recipe.cookCount > 0) "做过 ${recipe.cookCount} 次" else "还没做过")
                    }

                    // 一次都没打过分的就明说，免得五颗灰星挂在那儿像坏了
                    Spacer(Modifier.height(8.dp))
                    val avg = state.rating
                    if (avg != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StarRating(avg, starSize = 18.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${formatRating(avg)} · ${state.ratedCount} 次评分",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            "还没评分",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (recipe.lastCookedAt != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "最近一次：${formatDateTime(recipe.lastCookedAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (recipe.type == Recipe.TYPE_DESSERT &&
                        (recipe.bakingTempC != null || recipe.bakingTimeMin != null || recipe.servings != null)
                    ) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            recipe.bakingTempC?.let { Tag("$it °C") }
                            recipe.bakingTimeMin?.let { Tag("$it 分钟") }
                            recipe.servings?.let { Tag("$it 人份") }
                        }
                    }

                    if (recipe.description.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(recipe.description, style = MaterialTheme.typography.bodyLarge)
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showCookDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("我做了一次")
                    }

                    // 原材料
                    if (state.ingredients.isNotEmpty()) {
                        SectionTitle("原材料")
                        state.ingredients.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    formatAmount(item.quantity, item.unit),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // 视频链接
                    if (state.videos.isNotEmpty()) {
                        SectionTitle("视频教学")
                        state.videos.forEach { link ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val ok = runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                            )
                                        }.isSuccess
                                        if (!ok) {
                                            scope.launch { snackbar.showSnackbar("无法打开该链接") }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        link.title.ifBlank { link.url },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (link.title.isNotBlank()) {
                                        Text(
                                            link.url,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 做法步骤
                    if (recipe.steps.isNotBlank()) {
                        SectionTitle("做法步骤")
                        Text(recipe.steps, style = MaterialTheme.typography.bodyLarge)
                    }

                    // 成品照片
                    SectionTitle("成品记录")
                }
            }

            if (state.cookRecords.isEmpty()) {
                item {
                    EmptyState(
                        text = "还没有记录，做完记得拍张照",
                        modifier = Modifier.height(160.dp),
                    )
                }
            } else {
                item {
                    CookRecordList(
                        records = state.cookRecords,
                        coverPath = recipe.coverPhotoPath,
                        onSetCover = viewModel::setCoverFromRecord,
                        onEdit = { editingRecord = it },
                        onDelete = viewModel::deleteRecord,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    if (showDeleteDialog && recipe != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除这道菜？") },
            text = { Text("「${recipe.name}」及其食材、视频链接和做菜记录都会被删除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteRecipe()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }

    // 「我做了一次」对话框：可填备注、选成品照片
    if (showCookDialog) {
        CookDialog(
            defaultSetAsCover = recipe?.coverPhotoPath.isNullOrBlank(),
            onDismiss = { showCookDialog = false },
            onConfirm = { note, photoPath, setAsCover, rating ->
                showCookDialog = false
                viewModel.recordCook(note, photoPath, setAsCover, rating)
            },
        )
    }

    // 补/换照片、补打分、改备注 —— 老记录也能事后完善
    editingRecord?.let { record ->
        CookRecordEditDialog(
            record = record,
            isCover = !record.photoPath.isNullOrBlank() && record.photoPath == recipe?.coverPhotoPath,
            onDismiss = { editingRecord = null },
            onSave = { note, rating, photoPath, setAsCover ->
                editingRecord = null
                viewModel.updateRecord(record, note, rating, photoPath, setAsCover)
            },
        )
    }

    // 上一步确认后，如果冰箱里有能扣的，接着弹这一层
    state.deductPrompt?.let { prompt ->
        DeductDialog(
            prompt = prompt,
            onToggle = viewModel::toggleDeductLine,
            onConfirm = viewModel::confirmCook,
            onDismiss = viewModel::cancelCook,
        )
    }
}

/** 扣减确认框：逐条列出这次用掉的库存，勾掉不想扣的再确认。 */
@Composable
private fun DeductDialog(
    prompt: DeductPrompt,
    onToggle: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从冰箱里扣掉这些？") },
        text = {
            // AlertDialog 的 text 槽不滚动，食材一多确认按钮会被顶出屏幕
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "勾选这次用掉的食材，确认后从库存里减掉。用不完的可以取消勾选。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                prompt.lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = line.deductible && line.checked,
                            onCheckedChange = { onToggle(line.item.id) },
                            enabled = line.deductible,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                line.item.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = deductLineText(line),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (line.deductible) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("扣减并记录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun deductLineText(line: DeductLine): String {
    val reason = line.blockReason
    if (reason != null) {
        // 库存为 0 时 formatAmount 会把数量吞成空串，拼出「库存 个 · 已经用完了」这种半截话；
        // 何况「库存 0」本身也没信息量，这一档直接让原因自己说话
        return if (line.item.quantity <= 0.0) {
            reason
        } else {
            "库存 ${formatAmount(line.item.quantity, line.item.unit)} · $reason"
        }
    }
    val left = line.item.quantity - line.deduct
    // 扣完还剩 0 时不能走 formatAmount：它会把 0 显示成空串，只剩一个孤零零的单位
    val leftText = if (left <= 0.0) "用完" else "剩 ${formatAmount(left, line.item.unit)}"
    return "用 ${formatAmount(line.deduct, line.item.unit)} · 库存 " +
        "${formatAmount(line.item.quantity, line.item.unit)} → $leftText"
}

@Composable
private fun CookRecordList(
    records: List<CookRecord>,
    coverPath: String?,
    onSetCover: (CookRecord) -> Unit,
    onEdit: (CookRecord) -> Unit,
    onDelete: (CookRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        records.forEach { record ->
            val photo = record.photoPath
            val isCover = !photo.isNullOrBlank() && photo == coverPath
            Row(
                // 整行可点进编辑框；行尾的「设为封面」和删除是内层可点区，不冲突
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(record) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!photo.isNullOrBlank()) {
                    CoverImage(
                        path = photo,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(formatDateTime(record.cookedAt), style = MaterialTheme.typography.bodyLarge)
                    if (record.note.isNotBlank()) {
                        Text(
                            record.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val stars = record.rating ?: 0
                    if (stars > 0) {
                        Spacer(Modifier.height(2.dp))
                        StarRating(stars.toDouble(), starSize = 14.dp)
                    }
                }
                // 几次做菜的照片里挑一张当封面
                if (!photo.isNullOrBlank()) {
                    if (isCover) {
                        Tag("当前封面")
                    } else {
                        TextButton(onClick = { onSetCover(record) }) { Text("设为封面") }
                    }
                } else {
                    // 没照片的行给个明确入口：老记录补照片是本轮的重点，光靠「整行可点」没人猜得到
                    TextButton(onClick = { onEdit(record) }) { Text("加照片") }
                }
                IconButton(onClick = { onDelete(record) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除这条记录")
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun CookDialog(
    defaultSetAsCover: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Boolean, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var setAsCover by remember { mutableStateOf(defaultSetAsCover) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { ImageStore.copyIntoStore(context, uri) }
            if (path != null) photoPath = path
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录这次做菜") },
        text = {
            Column {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注（可选）") },
                    placeholder = { Text("咸淡、改进点…") },
                    minLines = 2,
                )
                Spacer(Modifier.height(12.dp))
                Text("这次做得怎么样", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                StarRatingInput(rating = rating, onChange = { rating = it })
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (photoPath != null) {
                        CoverImage(
                            path = photoPath,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    OutlinedButton(onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Text(if (photoPath == null) "添加成品照片" else "更换照片")
                    }
                }
                if (photoPath != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { setAsCover = !setAsCover },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = setAsCover, onCheckedChange = { setAsCover = it })
                        Text("把这张设为封面", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 没选照片就没有封面可设
                onConfirm(note, photoPath, setAsCover && photoPath != null, rating)
            }) { Text("记录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 编辑一条已有的成品记录：补/换/清照片、补打分、改备注。
 *
 * 和 CookDialog 的区别：这里只是改内容，不扣库存、不算「又做了一次」，
 * 所以没有扣减确认那一层，保存就直接落库。
 */
@Composable
private fun CookRecordEditDialog(
    record: CookRecord,
    isCover: Boolean,
    onDismiss: () -> Unit,
    onSave: (note: String, rating: Int, photoPath: String?, setAsCover: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 按 id 记忆：换一条记录编辑时要把输入重置成那条的内容
    var note by remember(record.id) { mutableStateOf(record.note) }
    var rating by remember(record.id) { mutableStateOf(record.rating ?: 0) }
    var photoPath by remember(record.id) { mutableStateOf(record.photoPath) }
    var setAsCover by remember(record.id) { mutableStateOf(isCover) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { ImageStore.copyIntoStore(context, uri) }
            if (path != null) photoPath = path
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑这条记录") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注（可选）") },
                    minLines = 2,
                )
                Spacer(Modifier.height(12.dp))
                Text("这次做得怎么样", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                StarRatingInput(rating = rating, onChange = { rating = it })
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (photoPath != null) {
                        CoverImage(
                            path = photoPath,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    OutlinedButton(onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Text(if (photoPath == null) "加张照片" else "换一张")
                    }
                    if (photoPath != null) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { photoPath = null }) {
                            Icon(Icons.Filled.Clear, contentDescription = "移除照片")
                        }
                    }
                }
                // 已经是封面的就没必要再问一遍
                if (photoPath != null && !isCover) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { setAsCover = !setAsCover },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = setAsCover, onCheckedChange = { setAsCover = it })
                        Text("把这张设为封面", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(note, rating, photoPath, setAsCover && photoPath != null)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
