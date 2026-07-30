package com.ordia.app.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSecurityRulesTest {
    @Test fun incompleteBackupsAreRejected() {
        assertFalse(BackupSecurityRules.hasAllCollections(setOf("tasks", "notes")))
        assertTrue(BackupSecurityRules.hasAllCollections(BackupSecurityRules.requiredCollections))
    }

    @Test fun unsupportedVersionsAndOversizedInputsAreRejected() {
        assertTrue(BackupSecurityRules.supportsVersion(2))
        assertTrue(BackupSecurityRules.supportsVersion(3))
        assertFalse(BackupSecurityRules.supportsVersion(1))
        assertFalse(BackupSecurityRules.inputSizeAllowed(BackupSecurityRules.MAX_UTF8_BYTES + 1))
        assertFalse(BackupSecurityRules.collectionSizeAllowed(BackupSecurityRules.MAX_ITEMS_PER_COLLECTION + 1))
    }

    @Test fun taskParentCyclesAreRejectedWithoutRecursion() {
        assertFalse(BackupSecurityRules.hasParentCycle(mapOf(1L to null, 2L to 1L, 3L to 2L)))
        assertTrue(BackupSecurityRules.hasParentCycle(mapOf(1L to 2L, 2L to 3L, 3L to 1L)))
        assertTrue(BackupSecurityRules.hasParentCycle(mapOf(1L to 1L)))
    }

    @Test fun duplicateCompoundKeysAreDetected() {
        assertFalse(BackupSecurityRules.hasDuplicatePairs(listOf(1L to 2L, 1L to 3L)))
        assertTrue(BackupSecurityRules.hasDuplicatePairs(listOf(1L to 2L, 1L to 2L)))
    }
    @Test fun duplicateEscapedTopLevelKeysAndDeepJsonAreRejected() {
        assertTrue(BackupSecurityRules.validateJsonEnvelope("{\"tasks\":[],\"\\u0074asks\":[]}") != null)
        assertTrue(BackupSecurityRules.validateJsonEnvelope("[".repeat(65) + "]".repeat(65)) != null)
        assertTrue(BackupSecurityRules.validateJsonEnvelope("{\"tasks\":[]}") == null)
        assertTrue(BackupSecurityRules.validateJsonEnvelope("{\"tasks\":[{\"id\":1,\"id\":2}]}") != null)
    }

    @Test fun malformedUtf8AndTrailingJsonAreRejected() {
        assertTrue(BackupSecurityRules.decodeUtf8Strict(byteArrayOf(0xC3.toByte(), 0x28)) == null)
        assertTrue(BackupSecurityRules.validateJsonEnvelope("{\"tasks\":[]} trailing") != null)
        assertTrue(BackupSecurityRules.validateJsonEnvelope("{}{}") != null)
        assertFalse(BackupSecurityRules.hasValidUnicodeScalars("\uD800"))
        assertTrue(BackupSecurityRules.hasValidUnicodeScalars("Ordia ✨"))
    }

    @Test fun dayListsRejectDuplicatesAndOutOfRangeValues() {
        assertTrue(BackupSecurityRules.parseUniqueDayList("1,3,7", 1..7) == setOf(1, 3, 7))
        assertTrue(BackupSecurityRules.parseUniqueDayList("1,1", 1..7) == null)
        assertTrue(BackupSecurityRules.parseUniqueDayList("8", 1..7) == null)
    }

}
