package com.caidan.menu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.caidan.menu.ui.navigation.MenuNavGraph
import com.caidan.menu.ui.theme.MenuTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MenuApp).container
        // 冷启动：从分享面板直接进来时，intent 在这里
        container.pendingImport.offer(sharedText(intent))
        setContent {
            MenuTheme {
                MenuNavGraph(container)
            }
        }
    }

    /** 应用已经在运行时又分享进来一次（launchMode=singleTop 会走到这里）。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 不 setIntent 的话，getIntent() 会一直是启动时那一个
        setIntent(intent)
        (application as MenuApp).container.pendingImport.offer(sharedText(intent))
    }

    /**
     * 取出分享过来的文字，不是分享进来的就返回空串。
     *
     * 用 getCharSequenceExtra 而不是 getStringExtra：Chrome 这类发送方
     * 放的是 SpannableString，getStringExtra 会因为类型对不上取不到。
     */
    private fun sharedText(intent: Intent?): String {
        if (intent?.action != Intent.ACTION_SEND) return ""
        if (intent.type?.startsWith("text/") != true) return ""
        return intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
    }
}
