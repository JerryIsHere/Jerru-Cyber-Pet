package com.jerru.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JerruStateMachineTest {
    @Test fun emergesAfterContinuousBrightnessAndResetsForDarkZone() {
        val machine = JerruStateMachine(0)
        machine.darkZoneAvailable(false, 1000)
        assertEquals(0f, machine.update(10999).emerge, 0f)
        machine.update(16001)
        assertTrue(machine.update(17001).emerge > 0f)
        machine.darkZoneAvailable(true, 17002)
        assertEquals(0f, machine.update(17003).emerge, 0f)
        machine.darkZoneAvailable(false, 17004)
        assertEquals(0f, machine.update(27003).emerge, 0f)
    }

    @Test fun emergeEndsAndDoesNotRepeatImmediately() {
        val machine = JerruStateMachine(0)
        machine.darkZoneAvailable(false, 1000)
        machine.update(16001)
        assertEquals(0f, machine.update(20501).emerge, 0f)
        assertEquals(0f, machine.update(20502).emerge, 0f)
    }
}
