package com.example.lr_1

import com.example.lr_1.helpers.ble.SmartBlindGattProfile
import com.example.lr_1.protocol.BlindCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleProtocolTest {
    @Test
    fun openingCommandUsesStableWireFormat() {
        assertEquals("SET_OPENING|0", BlindCommand.SetOpening(0).encode())
        assertEquals("SET_OPENING|50", BlindCommand.SetOpening(50).encode())
        assertEquals("SET_OPENING|100", BlindCommand.SetOpening(100).encode())
    }

    @Test
    fun readCommandUsesStableWireFormat() {
        assertEquals("GET_STATUS", BlindCommand.ReadStatus.encode())
    }

    @Test
    fun invalidOpeningIsRejected() {
        listOf(-1, 101).forEach { percent ->
            assertTrue(
                runCatching { BlindCommand.SetOpening(percent) }.exceptionOrNull()
                    is IllegalArgumentException
            )
        }
    }

    @Test
    fun gattUuidsAreUniqueAndShareVariantPrefix() {
        val service = SmartBlindGattProfile.SERVICE_UUID
        val status = SmartBlindGattProfile.STATUS_CHARACTERISTIC_UUID
        val command = SmartBlindGattProfile.COMMAND_CHARACTERISTIC_UUID
        assertNotEquals(service, status)
        assertNotEquals(service, command)
        assertNotEquals(status, command)
        listOf(service, status, command).forEach {
            assertTrue(it.toString().endsWith("e8f2-537e-4f6c-d104768a1214"))
        }
    }
}
