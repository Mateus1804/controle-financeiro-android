package com.mateuswilliam.financas.core

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupAuthenticationException : IllegalArgumentException("Senha incorreta ou arquivo alterado. Os dados locais foram preservados.")

/** Envelope portátil. O cabeçalho também é autenticado; parâmetros recebidos não controlam o custo da KDF. */
object BackupCrypto {
    const val VERSION = 1
    const val ITERATIONS = 600_000
    const val MAX_PAYLOAD_BYTES = 16 * 1024 * 1024
    const val HEADER_BYTES = 44
    const val MAX_FILE_BYTES = MAX_PAYLOAD_BYTES + HEADER_BYTES + 16
    private val magic = byteArrayOf(67, 70, 65, 66, 75, 80, 13, 10)

    fun passwordError(password: CharArray): String? = when {
        password.size < 12 -> "Use uma senha com pelo menos 12 caracteres."
        password.size > 256 -> "Use uma senha com até 256 caracteres."
        else -> null
    }

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        passwordError(password)?.let { throw IllegalArgumentException(it) }
        require(plaintext.isNotEmpty() && plaintext.size <= MAX_PAYLOAD_BYTES) { "O backup excede o limite de 16 MiB." }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(magic).putInt(VERSION).putInt(ITERATIONS).put(salt).put(nonce).array()
        return header + crypt(Cipher.ENCRYPT_MODE, plaintext, password, salt, nonce, header)
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        require(file.size in (HEADER_BYTES + 17)..MAX_FILE_BYTES) { "Arquivo de backup vazio, incompleto ou maior que 16 MiB." }
        require(password.size in 1..256) { "Informe a senha do backup (até 256 caracteres)." }
        val header = file.copyOfRange(0, HEADER_BYTES)
        val input = ByteBuffer.wrap(header)
        require(ByteArray(8).also(input::get).contentEquals(magic)) { "Este arquivo não é um backup do Controle Financeiro." }
        require(input.int == VERSION) { "Versão de backup não suportada. Nenhum dado foi substituído." }
        require(input.int == ITERATIONS) { "Parâmetros de criptografia não suportados." }
        val salt = ByteArray(16).also(input::get)
        val nonce = ByteArray(12).also(input::get)
        return try {
            crypt(Cipher.DECRYPT_MODE, file.copyOfRange(HEADER_BYTES, file.size), password, salt, nonce, header)
        } catch (_: AEADBadTagException) { throw BackupAuthenticationException() }
    }

    private fun crypt(mode: Int, content: ByteArray, password: CharArray, salt: ByteArray, nonce: ByteArray, header: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(header)
                doFinal(content)
            }
        } finally { key.fill(0) }
    }
}
