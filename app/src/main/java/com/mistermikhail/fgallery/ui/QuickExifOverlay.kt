package com.mistermikhail.fgallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mistermikhail.fgallery.data.MediaDetails
import com.mistermikhail.fgallery.data.MediaDetailsRepository
import com.mistermikhail.fgallery.data.MediaItem

@Composable
fun QuickExifOverlay(
    item: MediaItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        MediaDetailsRepository(context.applicationContext)
    }

    val details by produceState<MediaDetails?>(
        initialValue = null,
        key1 = item.id,
    ) {
        value = repository.load(item)
    }

    val loaded = details ?: return
    val values = loaded.sections
        .flatMap { it.fields }
        .associate { it.label to it.value }

    val primary = buildList {
        values["Дата съёмки"]?.let(::add)
        if (isEmpty()) {
            values["Дата"]?.let(::add)
        }

        values["Разрешение"]?.let(::add)
        values["Камера"]?.let(::add)
    }

    val exposure = buildList {
        values["Фокусное расстояние"]?.let(::add)
        values["Диафрагма"]?.let(::add)
        values["Выдержка"]?.let(::add)
        values["ISO"]?.let { add("ISO $it") }
    }

    if (primary.isEmpty() && exposure.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .background(
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (primary.isNotEmpty()) {
            Text(
                text = primary.joinToString("  •  "),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }

        if (exposure.isNotEmpty()) {
            Text(
                text = exposure.joinToString("  •  "),
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = if (primary.isNotEmpty()) 4.dp else 0.dp),
            )
        }
    }
}
