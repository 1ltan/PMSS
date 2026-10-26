package com.example.lr_1

import com.example.lr_1.repository.RemoteCommandPayload
import com.example.lr_1.repository.toDomainCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteCommandTest {
    @Test fun parsesBlindCommandsAndClampsOpening() {
        assertEquals(50, RemoteCommandPayload(command = "SET_OPENING|50").toDomainCommand().openingPercent)
        assertEquals(100, RemoteCommandPayload(openingPercent = 150).toDomainCommand().openingPercent)
        assertEquals(15, RemoteCommandPayload(command = "CLOSE_BLINDS").toDomainCommand().openingPercent)
    }

    @Test fun mapsGenericTodoToVirtualFlashlight() {
        val command = RemoteCommandPayload(id = 19, title = "demo", completed = true).toDomainCommand()
        assertEquals(true, command.flashlightEnabled)
        assertNull(command.openingPercent)
    }
}
