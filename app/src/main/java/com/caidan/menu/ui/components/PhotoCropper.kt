@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.caidan.menu.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 缩放上限。再大就是在放大像素了，没意义。 */
private const val MAX_ZOOM = 6f

/** 裁完统一缩到这个边长再存 —— 卡片按 1:1 显示，存更大也用不上。 */
private const val OUT_EDGE = 900

/**
 * 1:1 裁剪页：固定一个方形取景框，图可以拖动、双指缩放，调好了点确定。
 *
 * 裁剪框就是整个可见区域（不是浮在暗背景上的一个框）—— 少一层遮罩和坐标换算，
 * 手机上这么用也符合头像裁剪的直觉。所以下面那行提示不是装饰：
 * 看不到框外，就得告诉用户「图是能拖的」。
 */
@Composable
fun PhotoCropper(
    path: String,
    onCancel: () -> Unit,
    /** 裁好的图。调用方负责存盘和回收。 */
    onCropped: (Bitmap) -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { ImageStore.loadScaled(path) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("裁剪封面") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "取消")
                    }
                },
            )
        },
    ) { padding ->
        val bmp = bitmap
        if (bmp == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // 取景框的边长（像素）、用户额外放的倍数、以及图相对框中心的偏移。
        // 三者都是 remember(bmp)：换一张图就得从头开始，不能带着上一张的构图
        var frame by remember(bmp) { mutableStateOf(0f) }
        var zoom by remember(bmp) { mutableFloatStateOf(1f) }
        var offset by remember(bmp) { mutableStateOf(Offset.Zero) }

        // 让图刚好吃满取景框的基准缩放；用户看到的「1 倍」就是它
        fun fitScale(): Float =
            if (frame <= 0f) 1f else maxOf(frame / bmp.width, frame / bmp.height)

        /** 把偏移夹在「图不能露出框」的范围内。 */
        fun limitAt(scale: Float): Pair<Float, Float> {
            val halfW = maxOf(0f, (bmp.width * scale - frame) / 2f)
            val halfH = maxOf(0f, (bmp.height * scale - frame) / 2f)
            return halfW to halfH
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = "拖动调整位置，双指缩放。框里的部分会成为封面。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clipToBounds()
                    .onSizeChanged { frame = it.width.toFloat() }
                    .pointerInput(bmp) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            if (frame <= 0f) return@detectTransformGestures
                            val next = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                            val scale = fitScale() * next
                            val (halfW, halfH) = limitAt(scale)
                            zoom = next
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-halfW, halfW),
                                (offset.y + pan.y).coerceIn(-halfH, halfH),
                            )
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val scale = fitScale() * zoom
                    val drawW = bmp.width * scale
                    val drawH = bmp.height * scale
                    val left = size.width / 2f + offset.x - drawW / 2f
                    val top = size.height / 2f + offset.y - drawH / 2f
                    drawImage(
                        image = bmp.asImageBitmap(),
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }
                // 取景框的边线。半透明白 + 外圈深色描边，深浅图片上都看得见
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(1.dp, Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.85f))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("取消") }
                Box(Modifier.weight(1f))
                Button(
                    enabled = frame > 0f,
                    onClick = {
                        val scale = fitScale() * zoom
                        val side = frame / scale
                        // 取景框左上角落在图片坐标系里的位置
                        val left = (bmp.width / 2f - (frame / 2f + offset.x) / scale)
                            .coerceIn(0f, (bmp.width - side).coerceAtLeast(0f))
                        val top = (bmp.height / 2f - (frame / 2f + offset.y) / scale)
                            .coerceIn(0f, (bmp.height - side).coerceAtLeast(0f))

                        // 四舍五入之后「起点 + 边长」可能比图宽出 1 像素，
                        // 那样 createBitmap 会直接抛异常，所以宽度也得夹一次
                        val x = left.roundToInt().coerceIn(0, bmp.width - 1)
                        val y = top.roundToInt().coerceIn(0, bmp.height - 1)
                        val w = side.roundToInt().coerceIn(1, bmp.width - x)
                        val h = side.roundToInt().coerceIn(1, bmp.height - y)

                        val square = Bitmap.createBitmap(bmp, x, y, w, h)
                        // 统一缩到固定边长：圆整后未必正好是正方形，
                        // 归一化一下，存出来的封面就都是同一个尺寸
                        val out = Bitmap.createScaledBitmap(square, OUT_EDGE, OUT_EDGE, true)
                        if (out !== square) square.recycle()
                        onCropped(out)
                    },
                ) { Text("确定") }
            }
        }
    }
}
