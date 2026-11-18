package com.notifnix.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSecurityTest {
    @Test fun createdPinCanBeVerified() {
        val record = PinSecurity.createRecord("4826")
        assertTrue(PinSecurity.verify("4826", record))
    }

    @Test fun incorrectPinIsRejected() {
        val record = PinSecurity.createRecord("4826")
        assertFalse(PinSecurity.verify("4827", record))
    }

    @Test fun eachRecordUsesAUniqueSalt() {
        assertNotEquals(PinSecurity.createRecord("4826"), PinSecurity.createRecord("4826"))
    }

    @Test fun malformedRecordsAreRejected() {
        assertFalse(PinSecurity.verify("4826", "not-a-valid-record"))
    }
}