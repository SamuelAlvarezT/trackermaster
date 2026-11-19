package com.trackermaster.feature.focus

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trackermaster.core.data.progress.FocusAlarmState
import com.trackermaster.core.ui.R as P8R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FocusAlarmDialog(state: FocusAlarmState, onDismiss: () -> Unit, onSave: (String?, Boolean) -> Unit) {
    val context = LocalContext.current
    var uri by remember { mutableStateOf(state.soundUri) }
    var vibrate by remember { mutableStateOf(state.vibrate) }
    var error by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val selectedName by produceState<String?>(initialValue = null, key1 = uri) {
        value = null
        value = uri?.let { raw -> withContext(Dispatchers.IO) {
            val selected = Uri.parse(raw)
            val fileName = runCatching { context.contentResolver.query(selected, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } }.getOrNull()
            fileName ?: runCatching { RingtoneManager.getRingtone(context, selected)?.getTitle(context) }.getOrNull()
        } }
    }
    fun stopPreview() { runCatching { player?.stop() }; player?.release(); player = null }
    DisposableEffect(Unit) { onDispose { runCatching { player?.stop() }; player?.release() } }
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            uri = picked?.toString(); error = false
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) {
            val granted = runCatching { context.contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
            if (granted) { uri = selected.toString(); error = false } else error = true
        }
    }
    AlertDialog(onDismissRequest = { stopPreview(); onDismiss() },
        title = { Text(stringResource(P8R.string.p8_focus_alarm_settings)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (uri == null) stringResource(P8R.string.p8_focus_alarm_default) else selectedName ?: stringResource(P8R.string.p8_focus_alarm_selected))
            OutlinedButton(onClick = {
                stopPreview()
                systemPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri?.let(Uri::parse))
                })
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(P8R.string.p8_focus_alarm_system)) }
            OutlinedButton(onClick = { stopPreview(); filePicker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(P8R.string.p8_focus_alarm_file))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = vibrate, onCheckedChange = { vibrate = it })
                Text(stringResource(P8R.string.p8_focus_alarm_vibration), modifier = Modifier.padding(top = 12.dp))
            }
            OutlinedButton(onClick = {
                stopPreview()
                val selected = uri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                error = runCatching {
                    val next = MediaPlayer()
                    try {
                        next.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                        next.setDataSource(context, selected)
                        next.setOnPreparedListener { ready -> if (player === ready) ready.start() }
                        next.setOnErrorListener { failed, _, _ ->
                            if (player === failed) { player = null; error = true }
                            failed.release(); true
                        }
                        player = next
                        next.prepareAsync()
                    } catch (exception: Exception) { if (player === next) player = null; next.release(); throw exception }
                }.isFailure
            }) { Text(stringResource(P8R.string.p8_focus_alarm_preview)) }
            if (error) Text(stringResource(P8R.string.p8_focus_alarm_preview_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = { stopPreview(); onSave(uri, vibrate); onDismiss() }) { Text(stringResource(P8R.string.p8_focus_alarm_save)) } },
        dismissButton = { TextButton(onClick = { stopPreview(); onDismiss() }) { Text(stringResource(P8R.string.p8_focus_alarm_cancel)) } })
}
