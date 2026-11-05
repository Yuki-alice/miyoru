package app.miyoru.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

/**
 * 封面图（P0）：远端 URL 直载；无封面时占位。需鉴权头的源在 C2d
 * 通过 ImageRequestFactory 注入 headers（当前传入空 headers 即可工作）。
 */
@Composable
fun CoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    height: Dp = 56.dp,
) {
    if (url.isNullOrBlank()) {
        Box(
            modifier.then(Modifier.size(width, height).background(MaterialTheme.colorScheme.secondaryContainer)),
            contentAlignment = Alignment.Center,
        ) {
            Text("封", style = MaterialTheme.typography.titleMedium)
        }
    } else {
        val context = LocalPlatformContext.current
        val model = remember(url) {
            ImageRequest.Builder(context).data(url).build()
        }
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.then(Modifier.size(width, height)),
        )
    }
}
