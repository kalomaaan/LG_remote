package com.kalomaaan.acremote

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Bg = Color(0xFF0E1116)
private val Card = Color(0xFF1A1F27)
private val Key = Color(0xFF252B35)
private val KeyText = Color(0xFFE6E9EF)
private val Accent = Color(0xFF42A5F5)
private val PowerOn = Color(0xFFFF6D2E)
private val LcdBg = Color(0xFF9DB89A)
private val LcdInk = Color(0xFF1B2A1A)
private val LcdDim = Color(0x331B2A1A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Card)) {
                RemoteScreen()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteScreen() {
    val ctx = LocalContext.current
    val ir = remember { IrSender(ctx) }
    var state by remember { mutableStateOf(Store.load(ctx)) }
    var presets by remember { mutableStateOf(Store.loadPresets(ctx)) }
    var status by remember { mutableStateOf(if (ir.available) "Ready" else "No IR blaster detected on this phone") }
    var flash by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            if (state.offTimerAt != 0L && now >= state.offTimerAt) {
                // Receiver already sent OFF; refresh from storage.
                state = Store.load(ctx)
            }
            delay(15_000)
        }
    }

    fun update(s: AcState) {
        state = s
        Store.save(ctx, s)
    }

    fun send(frame: Int, label: String) {
        val err = ir.send(frame, LgCodec.variant(state.variant))
        buzz(ctx)
        status = err ?: "Sent: $label  (${LgCodec.hex(frame)})"
    }

    fun sendState(s: AcState, label: String) {
        update(s)
        if (s.power) send(LgCodec.stateFrame(s.mode, s.temp, s.fan, turningOn = false), label)
        else status = "AC is off in the app. Tap power to send."
    }

    fun togglePower() {
        if (state.power) {
            send(LgCodec.CMD_OFF, "Power OFF")
            OffTimerReceiver.cancel(ctx)
            update(state.copy(power = false, offTimerAt = 0L))
        } else {
            val s = state.copy(power = true)
            send(LgCodec.stateFrame(s.mode, s.temp, s.fan, turningOn = true), "Power ON")
            update(s)
        }
        flash = ""
    }

    fun setOffTimer(minutes: Int) {
        if (minutes <= 0) {
            OffTimerReceiver.cancel(ctx)
            update(state.copy(offTimerAt = 0L))
            status = "Off-timer cancelled"
            return
        }
        val at = System.currentTimeMillis() + minutes * 60_000L
        OffTimerReceiver.schedule(ctx, at)
        update(state.copy(offTimerAt = at))
        now = System.currentTimeMillis()
        status = "AC will turn off in ${formatMins(minutes)}"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AC Remote", color = KeyText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("LG window aircon · no ads", color = KeyText.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = KeyText)
            }
        }

        Spacer(Modifier.height(14.dp))
        Lcd(state, flash, now)
        Spacer(Modifier.height(8.dp))
        Text(
            status, color = if (ir.available) KeyText.copy(alpha = 0.55f) else Color(0xFFFF8A80),
            fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))

        // Row 1: Comfort/Swing | POWER | Light
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PillKey("COMFORT\nAIR") {
                send(LgCodec.CMD_SWING_TOGGLE, "Comfort Air / Swing"); flash = "AIR"
            }
            PowerKey(on = state.power) { togglePower() }
            PillKey("LIGHT\nOFF") { send(LgCodec.CMD_LIGHT_TOGGLE, "Light toggle"); flash = "LIGHT" }
        }
        Spacer(Modifier.height(18.dp))

        // Row 2: Mode | Temp up | Fan
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RoundKey(text = "MODE") {
                val modes = LgCodec.Mode.entries
                val next = modes[(state.mode.ordinal + 1) % modes.size]
                flash = ""
                sendState(state.copy(mode = next), "Mode ${next.label}")
            }
            RoundKey(icon = Icons.Filled.KeyboardArrowUp, desc = "Temperature up") {
                flash = ""
                sendState(state.copy(temp = (state.temp + 1).coerceAtMost(LgCodec.MAX_TEMP)), "Temp up")
            }
            RoundKey(text = "FAN\nSPEED") {
                val fans = LgCodec.Fan.entries
                val next = fans[(state.fan.ordinal + 1) % fans.size]
                flash = ""
                sendState(state.copy(fan = next), "Fan ${next.label}")
            }
        }
        Text("TEMP", color = KeyText.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 6.dp))

        // Row 3: Energy | Temp down | Jet
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RoundKey(text = "ENERGY\nSAVING", active = state.energySaving) {
                val on = !state.energySaving
                send(if (on) LgCodec.CMD_ENERGY_SAVING_ON else LgCodec.CMD_ENERGY_SAVING_OFF,
                    "Energy saving ${if (on) "ON" else "OFF"}")
                update(state.copy(energySaving = on))
                flash = ""
            }
            RoundKey(icon = Icons.Filled.KeyboardArrowDown, desc = "Temperature down") {
                flash = ""
                sendState(state.copy(temp = (state.temp - 1).coerceAtLeast(LgCodec.MIN_TEMP)), "Temp down")
            }
            RoundKey(text = "JET\nMODE") {
                send(LgCodec.CMD_JET, "Jet mode")
                update(state.copy(mode = LgCodec.Mode.COOL, temp = 18, fan = LgCodec.Fan.HIGH))
                flash = "JET"
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionTitle("Presets", "tap to apply · hold to save current")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            presets.forEachIndexed { i, p ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Card)
                        .combinedClickable(
                            onClick = {
                                val s = state.copy(power = true, mode = p.mode, temp = p.temp, fan = p.fan)
                                val wasOff = !state.power
                                update(s)
                                send(LgCodec.stateFrame(s.mode, s.temp, s.fan, turningOn = wasOff), "Preset ${p.name}")
                                flash = ""
                            },
                            onLongClick = {
                                val np = p.copy(mode = state.mode, temp = state.temp, fan = state.fan)
                                Store.savePreset(ctx, i, np)
                                presets = Store.loadPresets(ctx)
                                buzz(ctx)
                                status = "Saved current settings to \"${p.name}\""
                            },
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(p.name, color = KeyText, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${if (p.mode.hasTemp) "${p.temp}°" else p.mode.label} · ${p.fan.label}",
                            color = KeyText.copy(alpha = 0.55f), fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionTitle("Off timer", "phone sends OFF when time is up")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 120, 240).forEach { m ->
                Chip(formatMins(m), Modifier.weight(1f)) { setOffTimer(m) }
            }
            if (state.offTimerAt != 0L) Chip("Cancel", Modifier.weight(1f), danger = true) { setOffTimer(0) }
        }
        Spacer(Modifier.height(30.dp))
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            irInfo = ir.info(),
            onUseVariant = { i ->
                update(state.copy(variant = i))
                status = "Using signal #${i + 1}: ${LgCodec.variant(i).label}"
            },
            onTry = { i, on ->
                val v = LgCodec.variant(i)
                val frame = if (on) LgCodec.stateFrame(state.mode, state.temp, state.fan, turningOn = true) else LgCodec.CMD_OFF
                val err = ir.send(frame, v)
                buzz(ctx)
                status = err ?: "Tried #${i + 1} ${if (on) "ON" else "OFF"} (${LgCodec.hex(frame)})"
                err ?: "Sent #${i + 1} ${if (on) "ON" else "OFF"}"
            },
            onSync = { power ->
                update(state.copy(power = power))
                status = "App now thinks the AC is ${if (power) "ON" else "OFF"}"
            },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun Lcd(state: AcState, flash: String, now: Long) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black)
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(LcdBg, Color(0xFF86A383))))
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LgCodec.Mode.entries.forEach { m ->
                    Text(
                        m.label.uppercase(),
                        color = if (state.power && m == state.mode) LcdInk else LcdDim,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(if (state.power) "ON" else "OFF", color = LcdInk, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(LcdInk.copy(alpha = 0.5f)))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    val big = when {
                        !state.power -> "--"
                        flash == "JET" -> "JET"
                        !state.mode.hasTemp -> state.mode.label.uppercase()
                        else -> "${state.temp}°"
                    }
                    Text(big, color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                    val extras = buildList {
                        if (state.energySaving) add("ENERGY SAVE")
                        if (state.offTimerAt > now) add("OFF IN ${formatMins(((state.offTimerAt - now) / 60_000L).toInt() + 1)}")
                        if (flash == "LIGHT") add("LIGHT")
                        if (flash == "AIR") add("COMFORT AIR")
                    }
                    Text(extras.joinToString("  ·  ").ifEmpty { " " }, color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("FAN", color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    if (state.fan == LgCodec.Fan.AUTO) {
                        Text("AUTO", color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    } else {
                        (3 downTo 1).forEach { lvl ->
                            Box(
                                Modifier
                                    .padding(top = 3.dp)
                                    .width((26 + lvl * 14).dp)
                                    .height(7.dp)
                                    .background(if (lvl <= state.fan.bars) LcdInk else LcdDim)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerKey(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 96.dp, height = 70.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) PowerOn else Color(0xFFB8471C))
            .combinedClickableCompat(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(34.dp)) {
            val w = size.width
            val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
            drawArc(Color.White, startAngle = -60f, sweepAngle = 300f, useCenter = false, style = stroke)
            drawLine(Color.White, Offset(w / 2, 0f), Offset(w / 2, w * 0.45f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun PillKey(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 96.dp, height = 58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(Key)
            .combinedClickableCompat(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = KeyText, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
    }
}

@Composable
private fun RoundKey(
    text: String? = null,
    icon: ImageVector? = null,
    desc: String? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(86.dp)
            .clip(CircleShape)
            .background(Key)
            .then(if (active) Modifier.border(2.dp, Accent, CircleShape) else Modifier)
            .combinedClickableCompat(onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, contentDescription = desc, tint = KeyText, modifier = Modifier.size(42.dp))
        else Text(text ?: "", color = KeyText, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 15.sp)
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (danger) Color(0xFF4A2323) else Card)
            .combinedClickableCompat(onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (danger) Color(0xFFFF8A80) else KeyText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionTitle(title: String, hint: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, color = KeyText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text(hint, color = KeyText.copy(alpha = 0.45f), fontSize = 11.sp)
    }
}

@Composable
private fun SettingsDialog(
    state: AcState,
    irInfo: String,
    onUseVariant: (Int) -> Unit,
    onTry: (Int, Boolean) -> String,
    onSync: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var lastResult by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Find a working signal", fontWeight = FontWeight.Bold)
                Text(
                    "1. Turn the AC ON with the real remote or the other app.\n" +
                        "2. Point the phone at the AC from about 1 m.\n" +
                        "3. Tap OFF on each row, one by one, waiting 3 s between.\n" +
                        "4. When the AC turns off, tap USE on that row.",
                    fontSize = 12.sp, color = KeyText.copy(alpha = 0.7f),
                )
                if (lastResult.isNotEmpty()) {
                    Text(lastResult, fontSize = 12.sp, color = Accent, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(6.dp))
                LgCodec.VARIANTS.forEachIndexed { i, v ->
                    val selected = state.variant == i
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Accent.copy(alpha = 0.18f) else Color.Transparent)
                            .padding(start = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("#${i + 1}${if (selected) "  ✓ in use" else ""}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(v.label, fontSize = 11.sp, color = KeyText.copy(alpha = 0.6f))
                        }
                        TextButton(onClick = { lastResult = onTry(i, false) }) { Text("OFF") }
                        TextButton(onClick = { lastResult = onTry(i, true) }) { Text("ON") }
                        TextButton(onClick = { onUseVariant(i) }, enabled = !selected) { Text("USE") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Out of sync?", fontWeight = FontWeight.Bold)
                Text("If you used the real remote, tell the app what the AC is doing now (sends nothing).",
                    fontSize = 12.sp, color = KeyText.copy(alpha = 0.6f))
                Row {
                    TextButton(onClick = { onSync(true) }) { Text("AC is ON") }
                    TextButton(onClick = { onSync(false) }) { Text("AC is OFF") }
                }
                Spacer(Modifier.height(10.dp))
                Text("Phone IR info", fontWeight = FontWeight.Bold)
                Text(irInfo, fontSize = 11.sp, color = KeyText.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

private fun formatMins(m: Int): String = when {
    m < 60 -> "${m}m"
    m % 60 == 0 -> "${m / 60}h"
    else -> "${m / 60}h ${m % 60}m"
}

private fun buzz(ctx: Context) {
    val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    v?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
}
