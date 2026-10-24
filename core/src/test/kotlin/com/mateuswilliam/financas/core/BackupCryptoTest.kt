package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class BackupCryptoTest {
    private fun password() = "Senha de teste 2026!".toCharArray()
    @Test fun roundTripPreservesEveryByteAndUsesIndependentSaltAndNonce() {
        val content = "Dados fictícios: 123456789 centavos; João Exemplo".toByteArray()
        val first = BackupCrypto.encrypt(content, password())
        val second = BackupCrypto.encrypt(content, password())
        assertFalse(first.contentEquals(second))
        assertArrayEquals(content, BackupCrypto.decrypt(first, password()))
        assertFalse(first.toString(Charsets.UTF_8).contains("João Exemplo"))
    }
    @Test fun wrongPasswordDoesNotReturnPlaintext() {
        val file = BackupCrypto.encrypt("fictício".toByteArray(), password())
        assertThrows(BackupAuthenticationException::class.java) { BackupCrypto.decrypt(file, "outra senha suficiente".toCharArray()) }
    }
    @Test fun tamperedCiphertextIsRejected() {
        val file = BackupCrypto.encrypt("fictício".toByteArray(), password())
        file[file.lastIndex] = (file.last().toInt() xor 1).toByte()
        assertThrows(BackupAuthenticationException::class.java) { BackupCrypto.decrypt(file, password()) }
    }
    @Test fun tamperedAuthenticatedNonceIsRejected() {
        val file = BackupCrypto.encrypt("fictício".toByteArray(), password())
        file[43] = (file[43].toInt() xor 1).toByte()
        assertThrows(BackupAuthenticationException::class.java) { BackupCrypto.decrypt(file, password()) }
    }
    @Test fun unsupportedVersionAndUnboundedWorkFactorAreRejectedBeforeDerivation() {
        val file = BackupCrypto.encrypt("fictício".toByteArray(), password())
        val version = file.copyOf().also { ByteBuffer.wrap(it).putInt(8, 999) }
        val cost = file.copyOf().also { ByteBuffer.wrap(it).putInt(12, Int.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(version, password()) }
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(cost, password()) }
    }
    @Test fun emptyTruncatedOversizedAndWeakPasswordInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(byteArrayOf(), password()) }
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.encrypt(byteArrayOf(1), "curta".toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.encrypt(ByteArray(BackupCrypto.MAX_PAYLOAD_BYTES + 1), password()) }
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(ByteArray(BackupCrypto.HEADER_BYTES + 16), password()) }
        assertNull(BackupCrypto.passwordError(password()))
    }
    @Test fun portableVersionOneVectorWithUnicodePasswordCanBeOpened() {
        val vector = java.util.Base64.getDecoder().decode("Q0ZBQktQDQoAAAABAAknwAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhuOMJiLVRW1KPociHMYM7y2gIARYBCdokhkFKdsxh6rPg7ZN3WLfT3eeROPPq7CISZ+")
        assertEquals("Exemplo fictício — R$ 1.234,56", BackupCrypto.decrypt(vector, "Senha fictícia de teste 2026!".toCharArray()).toString(Charsets.UTF_8))
    }

}
