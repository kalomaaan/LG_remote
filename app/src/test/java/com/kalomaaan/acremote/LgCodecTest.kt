package com.kalomaaan.acremote

import org.junit.Assert.assertEquals
import org.junit.Test

class LgCodecTest {
    @Test fun knownCommandsHaveValidChecksums() {
        listOf(
            LgCodec.CMD_OFF, LgCodec.CMD_LIGHT_TOGGLE, LgCodec.CMD_JET,
            LgCodec.CMD_ENERGY_SAVING_ON, LgCodec.CMD_ENERGY_SAVING_OFF, LgCodec.CMD_SWING_TOGGLE,
        ).forEach { assertEquals(it, LgCodec.withChecksum(it)) }
    }

    @Test fun coolOn24AutoFan() {
        // 0x88 | on-header 0x00 | cool 0 | temp 24-15=9 | fan auto 5 | checksum
        val f = LgCodec.stateFrame(LgCodec.Mode.COOL, 24, LgCodec.Fan.AUTO, turningOn = true)
        assertEquals(0x880095, f ushr 4)
        assertEquals(LgCodec.withChecksum(f), f)
    }

    @Test fun changeWhileOnUsesHeader08() {
        val f = LgCodec.stateFrame(LgCodec.Mode.COOL, 18, LgCodec.Fan.HIGH, turningOn = false)
        assertEquals(0x880834, f ushr 4)
    }

    @Test fun patternLength() {
        assertEquals(2 + 56 + 1, LgCodec.toPattern(LgCodec.CMD_OFF, LgCodec.Timing.TYPE_A).size)
    }
}
