package com.home.library.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * 도서 표지. 상세/목록 공용.
 * cover_url이 있으면 Coil로 URL 로딩(저장 안 함, 캐시), 없으면 placeholder.
 */
@Composable
fun BookCover(
    coverUrl: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    if (coverUrl.isNullOrBlank()) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("📖", style = MaterialTheme.typography.titleMedium)
        }
    } else {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    }
}
