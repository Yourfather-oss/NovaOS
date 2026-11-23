package com.nova.os.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class Tile(val label: String, val status: String, val active: Boolean, val action: () -> Unit)

private fun openSystem(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun requestWriteSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun Modifier.closeGestures(onClose: () -> Unit): Modifier = this
    .pointerInput(Unit) { detectTapGestures { onClose() } }
    .pointerInput(Unit) {
        var dy = 0f
        detectDragGestures(
            onDragStart = { dy = 0f },
            onDrag = { _, d -> dy += d.y },
            onDragEnd = { if (dy < -80f) onClose() }
        )
    }

@Composable
fun ControlCenterScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val resolver = context.contentResolver

    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val wifi = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val bluetooth = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    val camera = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val torchId = remember {
        runCatching {
            camera.cameraIdList.firstOrNull { id ->
                val c = camera.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull()
    }

    var torchOn by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchId) torchOn = enabled
            }
        }
        camera.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { camera.unregisterTorchCallback(callback) }
    }

    var wifiOn by remember { mutableStateOf(false) }
    var btOn by remember { mutableStateOf(false) }
    var airplane by remember { mutableStateOf(false) }
    var locationOn by remember { mutableStateOf(false) }
    var rotationAuto by remember { mutableStateOf(true) }
    var canWrite by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        while (true) {
            wifiOn = runCatching { wifi.isWifiEnabled }.getOrDefault(false)
            btOn = runCatching { bluetooth?.isEnabled == true }.getOrDefault(false)
            airplane = Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            locationOn = runCatching { locationManager.isLocationEnabled }.getOrDefault(false)
            rotationAuto = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
            canWrite = Settings.System.canWrite(context)
            delay(1000)
        }
    }

    var brightness by remember {
        mutableFloatStateOf(Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
    }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()) }

    val tiles = listOf(
        Tile("Wi-Fi", if (wifiOn) "On" else "Off", wifiOn) {
            openSystem(context, Settings.Panel.ACTION_INTERNET_CONNECTIVITY); onClose()
        },
        Tile("Bluetooth", if (btOn) "On" else "Off", btOn) {
            openSystem(context, Settings.ACTION_BLUETOOTH_SETTINGS); onClose()
        },
        Tile("Data", "Open", false) {
            openSystem(context, Settings.Panel.ACTION_INTERNET_CONNECTIVITY); onClose()
        },
        Tile("Airplane", if (airplane) "On" else "Off", airplane) {
            openSystem(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS); onClose()
        },
        Tile("Torch", if (torchOn) "On" else "Off", torchOn) {
            if (torchId != null) runCatching { camera.setTorchMode(torchId, !torchOn) }
        },
        Tile("Rotation", if (rotationAuto) "Auto" else "Locked", rotationAuto) {
            if (canWrite) {
                runCatching {
                    Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (rotationAuto) 0 else 1)
                }
                rotationAuto = !rotationAuto
            } else {
                requestWriteSettings(context)
            }
        },
        Tile("Location", if (locationOn) "On" else "Off", locationOn) {
            openSystem(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS); onClose()
        },
        Tile("Sound", "Open", false) {
            openSystem(context, Settings.ACTION_SOUND_SETTINGS); onClose()
        }
    )

    Column(Modifier.fillMaxSize().background(Color(0x99000000))) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.verticalGradient(listOf(Obsidian.copy(alpha = 0.97f), DeepSpace.copy(alpha = 0.97f))))
                .blockTouches()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("CONTROL CENTRE", color = TextMuted, fontSize = 11.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(14.dp))

            tiles.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { t ->
                        ControlTile(t.label, t.status, t.active, Modifier.weight(1f).height(76.dp), t.action)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            if (canWrite) {
                SliderRow("BRIGHTNESS", brightness) {
                    brightness = it
                    runCatching {
                        Settings.System.putInt(
                            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                        )
                        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (it * 254f).roundToInt() + 1)
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .glass(RoundedCornerShape(24.dp))
                        .pointerInput(Unit) { detectTapGestures { requestWriteSettings(context) } },
                    contentAlignment = Alignment.Center
                ) { Text("Allow NOVA to control brightness", color = TextPrimary, fontSize = 14.sp) }
            }
            Spacer(Modifier.height(8.dp))
            SliderRow("VOLUME", volume) {
                volume = it
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVolume).roundToInt(), 0) }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .closeGestures(onClose),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(GlassBorder))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().closeGestures(onClose))
    }
}

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
        Slider(
            value = value,
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color(0x22FFFFFF)
            )
        )
    }
}

@Composable
private fun ControlTile(label: String, status: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(20.dp)
    val surface = if (active) {
        Modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), Nebula.copy(alpha = 0.75f))))
            .border(1.dp, Color(0x66FFFFFF), shape)
    } else {
        Modifier.glass(shape)
    }
    Column(
        modifier
            .bounceClick(onClick = onClick)
            .then(surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(status, color = if (active) TextPrimary else TextMuted, fontSize = 11.sp)
    }
}
