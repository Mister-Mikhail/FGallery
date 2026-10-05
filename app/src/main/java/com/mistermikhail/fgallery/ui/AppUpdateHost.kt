package com.mistermikhail.fgallery.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mistermikhail.fgallery.data.AppUpdates
import com.mistermikhail.fgallery.data.AvailableUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppUpdateHost(checkRequests: Int) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val repository = remember { AppUpdates(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloaded by remember { mutableLongStateOf(0L) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var installerFile by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var resume by remember { mutableIntStateOf(0) }
    var consumedCheck by remember { mutableIntStateOf(0) }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            .onFailure { error = "Не удалось открыть установку: ${it.localizedMessage}" }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = installerFile
        installerFile = null
        if (file != null && context.packageManager.canRequestPackageInstalls()) install(file)
        else if (file != null) error = "Установка отменена. Разрешение можно выдать при следующей попытке обновления."
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resume++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(checkRequests, resume) {
        val manual = checkRequests != consumedCheck
        consumedCheck = checkRequests
        if (checking || downloading) return@LaunchedEffect
        checking = manual
        try {
            val found = repository.check(manual)
            if (found != null) update = found
            else if (manual) Toast.makeText(context, "Обновлений нет", Toast.LENGTH_SHORT).show()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (manual) error = e.localizedMessage ?: "Не удалось проверить обновления" }
        finally { checking = false }
    }
    if (checking) AlertDialog(
        onDismissRequest = {}, title = { Text("Проверка обновлений") },
        text = { LinearProgressIndicator() }, confirmButton = {},
    )
    update?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (downloading) downloadJob?.cancel() else { repository.decline(candidate); update = null } },
            title = { Text(if (downloading) "Загрузка обновления" else "FGallery ${candidate.version}") },
            text = {
                if (downloading) Column {
                    if (candidate.size > 0) LinearProgressIndicator(progress = { (downloaded.toFloat() / candidate.size).coerceIn(0f, 1f) })
                    else LinearProgressIndicator()
                    Text("${downloaded / (1024 * 1024)} МБ${if (candidate.size > 0) " / ${candidate.size / (1024 * 1024)} МБ" else ""}")
                } else Text("Доступно обновление приложения.")
            },
            confirmButton = {
                if (!downloading) TextButton(onClick = {
                    downloading = true; downloaded = 0
                    downloadJob = scope.launch {
                        try {
                            val file = repository.download(candidate) { count, _ -> downloaded = count }
                            update = null
                            if (context.packageManager.canRequestPackageInstalls()) install(file)
                            else {
                                installerFile = file
                                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                            }
                        } catch (e: CancellationException) { update = null; throw e }
                        catch (e: Exception) { update = null; error = e.localizedMessage ?: "Не удалось скачать обновление" }
                        finally { downloading = false; downloadJob = null }
                    }
                }) { Text("Скачать и обновить") }
            },
            dismissButton = { TextButton(onClick = {
                if (downloading) downloadJob?.cancel() else { repository.decline(candidate); update = null }
            }) { Text(if (downloading) "Отмена" else "Позже") } },
        )
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Обновление") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Закрыть") } }) }
}
