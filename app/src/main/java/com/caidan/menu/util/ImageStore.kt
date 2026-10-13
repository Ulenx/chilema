package com.caidan.menu.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 图片落地与编码工具。
 * 用户选中的图片会复制进应用私有目录 `filesDir/photos`，
 * 这样即使原图被删除或相册权限变化，App 内仍能正常显示。
 */
object ImageStore {

    private const val DIR = "photos"
    /** 传给多模态模型前的最大边长，控制 base64 体积与请求耗时。 */
    private const val MAX_EDGE = 1568

    fun photoDir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** 把相册/相机返回的 Uri 复制进私有目录，返回文件绝对路径。 */
    fun copyIntoStore(context: Context, uri: Uri): String? {
        return runCatching {
            val target = File(photoDir(context), "img_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)!!.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            target.absolutePath
        }.getOrNull()
    }

    /** 读取本地图片并编码为 `data:image/jpeg;base64,...`，供多模态模型使用。 */
    fun toDataUrl(path: String): String {
        val bitmap = decodeScaled(path)
            ?: throw IllegalArgumentException("图片读取失败，请重新选择。")
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
        bitmap.recycle()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    /** 按最大边长等比缩放解码，避免大图直接进内存导致 OOM。 */
    private fun decodeScaled(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= MAX_EDGE) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)
    }
}
