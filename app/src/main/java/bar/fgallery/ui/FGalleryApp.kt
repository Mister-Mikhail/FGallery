package bar.fgallery.ui

import android.graphics.Bitmap
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bar.fgallery.data.MediaFolder
import bar.fgallery.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Text = Color(0xFFF1F1F1); private val Secondary = Color(0xFFAAAAAA)
@Composable fun FGalleryApp() {
    MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
        val context = LocalContext.current; var folders by remember { mutableStateOf(emptyList<MediaFolder>()) }
        LaunchedEffect(Unit) { folders = withContext(Dispatchers.IO) { val repo = MediaRepository(context); repo.folders(repo.loadMedia()) } }
        Surface(Modifier.fillMaxSize(), color = Color.Black) { FolderScreen(folders) }
    }
}
@Composable private fun FolderScreen(folders: List<MediaFolder>) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Menu, "Меню", tint = Text, modifier = Modifier.size(32.dp)); Text("Папки", color = Text, fontSize = 30.sp, modifier = Modifier.padding(start = 24.dp).weight(1f)); Icon(Icons.Default.Sort, "Сортировка", tint = Text, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(22.dp)); Icon(Icons.Default.Search, "Поиск", tint = Text, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(16.dp)); Icon(Icons.Default.MoreVert, "Ещё", tint = Text, modifier = Modifier.size(30.dp))
        }
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { items(folders, key = { it.id }) { FolderTile(it) } }
    }
}
@Composable private fun FolderTile(folder: MediaFolder) {
    val context = LocalContext.current; var bitmap by remember(folder.cover?.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(folder.cover?.uri) { bitmap = folder.cover?.let { item -> withContext(Dispatchers.IO) { runCatching { context.contentResolver.loadThumbnail(item.uri, Size(720, 720), null) }.getOrNull() } } }
    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF050505)).clickable { }) {
        bitmap?.let { Image(it.asImageBitmap(), folder.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 8.dp)) { Column { Text(folder.name, color = Text, fontSize = 20.sp, maxLines = 1); Text(folder.count.toString(), color = Secondary, fontSize = 15.sp, fontWeight = FontWeight.Normal) } }
    }
}
