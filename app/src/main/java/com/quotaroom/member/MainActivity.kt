package com.quotaroom.member

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.MaterialTheme.colorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0E1117)
private val CardBg = Color(0xFF171B24)
private val Accent = Color(0xFF6D8DFF)
private val Ok = Color(0xFF34D399)
private val Warn = Color(0xFFFBBF24)
private val Bad = Color(0xFFF87171)

class MainActivity : ComponentActivity() {
    private val prefs: SharedPreferences by lazy { getSharedPreferences("room", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent, onPrimary = Bg, background = Bg, surface = CardBg,
                    onBackground = Color(0xFFE6E9F2), onSurface = Color(0xFFE6E9F2),
                ),
            ) { App() }
        }
    }

    @Composable
    private fun App() {
        val ctx = this
        val scope = rememberCoroutineScope()
        val rs by RoomService.state.collectAsState()
        var token by remember { mutableStateOf(prefs.getString("token", null)) }
        var base by remember { mutableStateOf(prefs.getString("base", "") ?: "") }
        var name by remember { mutableStateOf(prefs.getString("name", "") ?: "") }
        var code by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        var gated by remember { mutableStateOf<(() -> Unit)?>(null) }
        var showPinSetup by remember { mutableStateOf(false) }

        fun guard(action: () -> Unit) {
            if (PinStore.isSet(ctx)) gated = action else action()
        }

        fun startRoom(t: String) {
            val i = Intent(ctx, RoomService::class.java)
                .putExtra(RoomService.EXTRA_BASE, base).putExtra(RoomService.EXTRA_TOKEN, t)
            startForegroundService(i)
        }

        val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            token?.let { startRoom(it) }
        }

        fun startWithConsent(t: String) {
            val consent = VpnService.prepare(ctx)
            if (consent != null) {
                vpnLauncher.launch(consent)
            } else {
                startRoom(t)
            }
        }

        fun stopRoom() {
            startService(Intent(ctx, RoomService::class.java).setAction(RoomService.ACTION_STOP))
        }

        Column(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF151B2B), Bg)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Quota Room", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(if (token == null) "Join your room" else "You're in", color = Color(0xFF8891A6))

            if (token == null) {
                Card(colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(base, { base = it }, label = { Text("Admin address (e.g. http://192.168.1.5:8765)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Room code") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(name, { name = it }, label = { Text("Your name") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        error?.let { Text(it, color = Bad) }
                        Button(
                            onClick = {
                                busy = true; error = null
                                scope.launch {
                                    try {
                                        val r = withContext(Dispatchers.IO) { RoomApi.join(base.trim(), code.trim(), name.trim()) }
                                        prefs.edit().putString("token", r.token).putString("base", base.trim())
                                            .putString("name", name.trim()).apply()
                                        token = r.token
                                        startWithConsent(r.token)
                                    } catch (e: Exception) {
                                        error = e.message ?: "Could not join"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy && base.isNotBlank() && code.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (busy) "Joining..." else "Join") }
                    }
                }
            } else {
                val pct = if (rs.quota > 0) (rs.used * 100 / rs.quota).toInt() else 0
                val frac = if (rs.quota > 0) (rs.used.toFloat() / rs.quota).coerceIn(0f, 1f) else 0f
                val color = when {
                    rs.blocked -> Bad
                    pct >= 80 -> Warn
                    else -> Ok
                }
                Card(colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    rs.blocked -> "Internet cut"
                                    rs.connected -> "Online"
                                    else -> "Connecting..."
                                },
                                color = color, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                            )
                        }
                        Text("$pct%", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = color)
                        LinearProgressIndicator(progress = { frac }, color = color,
                            modifier = Modifier.fillMaxWidth().height(8.dp))
                        Text("${fmt(rs.used)} / ${fmt(rs.quota)}", color = Color(0xFF8891A6))
                        rs.error?.let { Text("Can't reach admin: $it", color = Warn, fontSize = 12.sp) }
                    }
                }

                Button(onClick = { guard { stopRoom() } },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.fillMaxWidth()) { Text("Stop app") }
                OutlinedButton(onClick = { guard { stopRoom(); prefs.edit().remove("token").apply(); token = null } },
                    modifier = Modifier.fillMaxWidth()) { Text("Leave room") }

                Spacer(Modifier.height(6.dp))
                Text("Security", color = Color(0xFF8891A6), fontSize = 13.sp)
                OutlinedButton(onClick = {
                    if (PinStore.isSet(ctx)) guard { showPinSetup = true } else showPinSetup = true
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (PinStore.isSet(ctx)) "Change PIN" else "Set PIN")
                }
                if (PinStore.isSet(ctx)) {
                    TextButton(onClick = { guard { PinStore.clear(ctx); gated = null } }) {
                        Text("Remove PIN", color = Bad)
                    }
                }
            }
        }

        gated?.let { action ->
            PinDialog(
                title = "Enter PIN",
                onSubmit = { pin ->
                    if (PinStore.check(ctx, pin)) { gated = null; action(); true } else false
                },
                onDismiss = { gated = null },
            )
        }

        if (showPinSetup) {
            PinSetupDialog(onDone = { pin ->
                PinStore.set(ctx, pin)
                showPinSetup = false
            }, onDismiss = { showPinSetup = false })
        }
    }
}

@Composable
private fun PinDialog(title: String, onSubmit: (String) -> Boolean, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8); wrong = false },
                singleLine = true, label = { Text("PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                supportingText = { if (wrong) Text("Wrong PIN", color = Bad) })
        },
        confirmButton = { TextButton(onClick = { if (!onSubmit(pin)) wrong = true }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PinSetupDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, singleLine = true,
                    label = { Text("New PIN (4 to 8 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(again, { again = it.filter(Char::isDigit).take(8) }, singleLine = true,
                    label = { Text("Repeat PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = {
            TextButton(enabled = pin.length in 4..8 && pin == again, onClick = { onDone(pin) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun fmt(bytes: Long): String {
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return if (gb >= 1) "%.2f GB".format(gb) else "%.0f MB".format(bytes / 1024.0 / 1024.0)
}
