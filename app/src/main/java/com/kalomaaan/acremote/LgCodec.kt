package com.kalomaaan.acremote

/**
 * Encoder for the 28-bit LG air-conditioner IR protocol.
 *
 * Frame layout (MSB first):
 *   bits 27..20  signature 0x88
 *   bits 19..12  command header (e.g. 0x00 = power on + mode, 0x08 = change while on,
 *                0xC0 = system commands like OFF/light, 0x10 = basic commands like jet/swing)
 *   bits 11..8   temperature - 15   (or command data)
 *   bits 7..4    fan speed          (or command data)
 *   bits 3..0    checksum = sum of the six data nibbles (bits 27..4) & 0xF
 *
 * Protocol is publicly documented by the open-source IRremoteESP8266 and ESPHome projects.
 */
object LgCodec {

    const val CARRIER_HZ = 38_000
    const val MIN_TEMP = 16
    const val MAX_TEMP = 30

    enum class Mode(val nibble: Int, val label: String, val hasTemp: Boolean) {
        COOL(0x0, "Cool", true),
        DRY(0x1, "Dry", false),
        FAN(0x2, "Fan", false),
        AUTO(0x3, "Auto", true),
    }

    enum class Fan(val nibble: Int, val label: String, val bars: Int) {
        LOW(0x0, "Low", 1),
        MEDIUM(0x2, "Medium", 2),
        HIGH(0x4, "High", 3),
        AUTO(0x5, "Auto", 0),
    }

    /** Timing variants. LG ACs use one of these two; the user can switch in Settings. */
    enum class Timing(
        val label: String,
        val headerMark: Int, val headerSpace: Int,
        val bitMark: Int, val oneSpace: Int, val zeroSpace: Int,
    ) {
        TYPE_A("Type A (most LG aircons)", 3200, 9900, 480, 1600, 550),
        TYPE_B("Type B (older LG)", 8500, 4250, 550, 1600, 550),
    }

    // Fixed commands (checksum is recomputed anyway).
    const val CMD_OFF = 0x88C0051
    const val CMD_LIGHT_TOGGLE = 0x88C00A6
    const val CMD_JET = 0x8810089
    const val CMD_ENERGY_SAVING_ON = 0x8810045
    const val CMD_ENERGY_SAVING_OFF = 0x8810056
    const val CMD_SWING_TOGGLE = 0x8810001

    fun checksum(frame: Int): Int {
        var x = frame ushr 4
        var sum = 0
        repeat(6) {
            sum += x and 0xF
            x = x ushr 4
        }
        return sum and 0xF
    }

    fun withChecksum(frame: Int): Int = (frame and 0xFFFFFF0) or checksum(frame)

    /**
     * Build a state frame.
     * @param turningOn true when the AC was off (uses the "power on" header),
     *                  false when changing settings while it's already running.
     */
    fun stateFrame(mode: Mode, tempC: Int, fan: Fan, turningOn: Boolean): Int {
        val header = if (turningOn) 0x00 else 0x08
        val t = if (mode.hasTemp) (tempC.coerceIn(MIN_TEMP, MAX_TEMP) - 15) else 0
        val frame = (0x88 shl 20) or
            (header shl 12) or
            (mode.nibble shl 12) or
            (t shl 8) or
            (fan.nibble shl 4)
        return withChecksum(frame)
    }

    /**
     * One way of putting the frame on the air. Phones' IR drivers and LG units differ
     * slightly, so the app lets the user try several and keep the one that works.
     */
    data class Variant(
        val timing: Timing,
        val freq: Int = CARRIER_HZ,
        val repeats: Int = 1,
        val tailGap: Boolean = false,
    ) {
        val label: String
            get() = buildString {
                append(if (timing == Timing.TYPE_A) "Type A" else "Type B")
                append(" · ${freq / 1000} kHz · x$repeats")
                if (tailGap) append(" · end gap")
            }
    }

    val VARIANTS = listOf(
        Variant(Timing.TYPE_A),
        Variant(Timing.TYPE_B),
        Variant(Timing.TYPE_A, repeats = 2),
        Variant(Timing.TYPE_B, repeats = 2),
        Variant(Timing.TYPE_A, tailGap = true),
        Variant(Timing.TYPE_B, tailGap = true),
        Variant(Timing.TYPE_A, freq = 36_000),
        Variant(Timing.TYPE_B, freq = 36_000),
        Variant(Timing.TYPE_A, freq = 40_000, repeats = 2),
        Variant(Timing.TYPE_B, freq = 40_000, repeats = 2),
        Variant(Timing.TYPE_A, repeats = 3, tailGap = true),
        Variant(Timing.TYPE_B, repeats = 3, tailGap = true),
    )

    fun variant(index: Int): Variant = VARIANTS.getOrElse(index) { VARIANTS[0] }

    private const val FRAME_GAP_US = 40_000

    /** Raw on/off durations in microseconds, as ConsumerIrManager.transmit expects. */
    fun toPattern(frame: Int, timing: Timing): IntArray = toPattern(frame, Variant(timing))

    fun toPattern(frame: Int, v: Variant): IntArray {
        val t = v.timing
        val out = ArrayList<Int>()
        repeat(v.repeats) { r ->
            if (r > 0) out += FRAME_GAP_US
            out += t.headerMark
            out += t.headerSpace
            for (i in 27 downTo 0) {
                out += t.bitMark
                out += if ((frame ushr i) and 1 == 1) t.oneSpace else t.zeroSpace
            }
            out += t.bitMark
        }
        if (v.tailGap) out += FRAME_GAP_US
        return out.toIntArray()
    }

    fun hex(frame: Int): String = "0x" + frame.toString(16).uppercase().padStart(7, '0')
}
