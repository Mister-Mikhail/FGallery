package com.mistermikhail.fgallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mistermikhail.fgallery.data.MediaDetails
import com.mistermikhail.fgallery.data.MediaDetailsRepository
import com.mistermikhail.fgallery.data.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsSheet(
    item: MediaItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        MediaDetailsRepository(context.applicationContext)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val details by produceState<MediaDetails?>(
        initialValue = null,
        key1 = item.id,
    ) {
        value = repository.load(item)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 14.dp),
            )

            val loaded = details
            if (loaded == null) {
                Text(
                    text = "Чтение данных…",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                loaded.sections.forEachIndexed { sectionIndex, section ->
                    if (sectionIndex > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        )
                    }

                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    section.fields.forEach { field ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = field.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                                modifier = Modifier.weight(0.42f),
                            )
                            Text(
                                text = field.value,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.58f),
                            )
                        }
                    }
                }
            }
        }
    }
}
