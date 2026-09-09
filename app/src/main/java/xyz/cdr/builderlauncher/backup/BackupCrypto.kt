package xyz.cdr.builderlauncher.backup

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    const val MAGIC = "BLB1"
    const val ITERATIONS = 120_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_LEN_BITS = 256
    private const val TAG_BITS = 128

    fun encrypt(plain: ByteArray, passphrase: String, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.isNotEmpty()) { "Encryption key is required" }
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val key = derive(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        return MAGIC.toByteArray(Charsets.US_ASCII) + salt + iv + ct
    }

    fun decrypt(blob: ByteArray, passphrase: String): ByteArray {
        require(passphrase.isNotEmpty()) { "Encryption key is required" }
        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        if (blob.size < magic.size + SALT_LEN + IV_LEN + 16 || !blob.copyOfRange(0, magic.size).contentEquals(magic)) {
            throw IllegalArgumentException("Not an encrypted Builder Launcher backup")
        }
        var i = magic.size
        val salt = blob.copyOfRange(i, i + SALT_LEN)
        i += SALT_LEN
        val iv = blob.copyOfRange(i, i + IV_LEN)
        i += IV_LEN
        val ct = blob.copyOfRange(i, blob.size)
        val key = derive(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return try {
            cipher.doFinal(ct)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Wrong encryption key")
        }
    }

    private fun derive(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LEN_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
