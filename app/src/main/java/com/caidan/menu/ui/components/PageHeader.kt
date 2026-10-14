package com.caidan.menu.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 各页统一的标题栏：一行大标题 + 一行小字，右边可以挂点东西（比如冰箱的过期提醒）。
 *
 * 没用 Material 的 `TopAppBar`：它固定 64dp 高、内容上下居中，手机上标题头顶会
 * 空出一大块，四页看着都不够紧凑。这里手工排，只留状态栏 + 一点点内边距，
 * 标题就顶上去了。
 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 这里**不要**再写 statusBarsPadding()：Scaffold 已经把状态栏 inset
            // 算进 topBar 的高度里了。再加一层等于算两遍，白空出 25dp ——
            // 之前标题迟迟顶不上去就是这个原因。
            .padding(start = 16.dp, end = 8.dp, top = 0.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationIcon()
        Column(modifier = Modifier.weight(1f)) {
            // 两行的行高都收紧一点。默认的 28/20 是给西文留的，中文用不到那么多
            // 上下留白 —— 一个字号差不多就是这么攒出来的
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(lineHeight = 26.sp),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        actions()
    }
}
