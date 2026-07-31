package com.ordia.app.backup

import org.junit.Assert.assertEquals
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
        assertTrue(BackupSecurityRules.supportsVersion(4))
        assertFalse(BackupSecurityRules.supportsVersion(1))
        assertFalse(BackupSecurityRules.supportsVersion(5))
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

    @Test fun sha256MatchesKnownVectors() {
        // Vectores estándar NIST del SHA-256.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            BackupSecurityRules.sha256Hex("abc".toByteArray(Charsets.UTF_8))
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            BackupSecurityRules.sha256Hex(ByteArray(0))
        )
    }

    @Test fun sha256IsDeterministicAndContentSensitive() {
        val content = "{\"tasks\":[{\"id\":1}]}"
        val bytes = content.toByteArray(Charsets.UTF_8)
        assertEquals(BackupSecurityRules.sha256Hex(bytes), BackupSecurityRules.sha256Hex(bytes))
        assertFalse(
            BackupSecurityRules.sha256Hex(bytes) ==
                BackupSecurityRules.sha256Hex("{\"tasks\":[{\"id\":2}]}".toByteArray(Charsets.UTF_8))
        )
    }

    @Test fun checksumFormatAcceptsOnlyLowercaseHex64() {
        assertTrue(BackupSecurityRules.isValidChecksumFormat("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
        assertFalse(BackupSecurityRules.isValidChecksumFormat("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"))
        assertFalse(BackupSecurityRules.isValidChecksumFormat("abc"))
        assertFalse(BackupSecurityRules.isValidChecksumFormat(""))
        assertFalse(BackupSecurityRules.isValidChecksumFormat("z".repeat(64)))
        assertFalse(BackupSecurityRules.isValidChecksumFormat("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015a"))
    }
}
