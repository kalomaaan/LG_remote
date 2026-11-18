package com.kalomaaan.acremote

import android.content.Context

data class AcState(
    val power: Boolean = false,
    val mode: LgCodec.Mode = LgCodec.Mode.COOL,
    val temp: Int = 24,
    val fan: LgCodec.Fan = LgCodec.Fan.AUTO,
    val energySaving: Boolean = false,
    val timing: LgCodec.Timing = LgCodec.Timing.TYPE_A,
    val offTimerAt: Long = 0L,
)

data class Preset(val name: String, val mode: LgCodec.Mode, val temp: Int, val fan: LgCodec.Fan)

object Store {
    private const val FILE = "ac_state"

    fun load(ctx: Context): AcState {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return AcState(
            power = p.getBoolean("power", false),
            mode = enumOr(p.getString("mode", null), LgCodec.Mode.COOL),
            temp = p.getInt("temp", 24),
            fan = enumOr(p.getString("fan", null), LgCodec.Fan.AUTO),
            energySaving = p.getBoolean("energy", false),
            timing = enumOr(p.getString("timing", null), LgCodec.Timing.TYPE_A),
            offTimerAt = p.getLong("offTimerAt", 0L),
        )
    }

    fun save(ctx: Context, s: AcState) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("power", s.power)
            .putString("mode", s.mode.name)
            .putInt("temp", s.temp)
            .putString("fan", s.fan.name)
            .putBoolean("energy", s.energySaving)
            .putString("timing", s.timing.name)
            .putLong("offTimerAt", s.offTimerAt)
            .apply()
    }

    fun loadPresets(ctx: Context): List<Preset> {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return (0 until 3).map { i ->
            val d = defaultPresets[i]
            Preset(
                name = p.getString("preset${i}_name", d.name) ?: d.name,
                mode = enumOr(p.getString("preset${i}_mode", null), d.mode),
                temp = p.getInt("preset${i}_temp", d.temp),
                fan = enumOr(p.getString("preset${i}_fan", null), d.fan),
            )
        }
    }

    fun savePreset(ctx: Context, index: Int, preset: Preset) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("preset${index}_name", preset.name)
            .putString("preset${index}_mode", preset.mode.name)
            .putInt("preset${index}_temp", preset.temp)
            .putString("preset${index}_fan", preset.fan.name)
            .apply()
    }

    private val defaultPresets = listOf(
        Preset("Night", LgCodec.Mode.COOL, 25, LgCodec.Fan.LOW),
        Preset("Day", LgCodec.Mode.COOL, 24, LgCodec.Fan.AUTO),
        Preset("Blast", LgCodec.Mode.COOL, 18, LgCodec.Fan.HIGH),
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
