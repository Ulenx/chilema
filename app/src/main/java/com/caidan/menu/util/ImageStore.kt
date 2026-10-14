package com.caidan.menu.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
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

    /**
     * 给相机应用备一个写临时照片的位置，返回它的 content:// URI。
     *
     * 相机应用是另一个进程，没法直接往我们的私有目录写文件，所以得走
     * FileProvider 授一个 URI 给它。拍完的图我们会立刻 [copyIntoStore] 收进
     * `photos/`，这一步纯粹是中转。
     */
    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** 这台设备上有没有能接 ACTION_IMAGE_CAPTURE 的相机应用。 */
    fun canTakePhoto(context: Context): Boolean =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .resolveActivity(context.packageManager) != null

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

    /**
     * 给需要编辑的界面读一张图，已经缩到 [MAX_EDGE] 以内。
     *
     * 裁剪页拿它当工作图：这个尺寸放大到屏幕上完全够看，
     * 又不至于让一张四千像素的原图把内存吃满。
     */
    fun loadScaled(path: String): Bitmap? = decodeScaled(path)

    /** 把一张位图存进私有目录，返回文件绝对路径。 */
    fun saveBitmap(context: Context, bitmap: Bitmap, prefix: String = "img"): String? =
        runCatching {
            val target = File(photoDir(context), "${prefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            target.absolutePath
        }.getOrNull()

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
