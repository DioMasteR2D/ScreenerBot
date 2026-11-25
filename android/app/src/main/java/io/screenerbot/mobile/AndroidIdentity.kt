package io.screenerbot.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the stable wallet-encryption identity encrypted by a non-exportable
 * Android Keystore AES key. Copying app files alone cannot recover the identity.
 */
internal object AndroidIdentity {
    private const val KEY_ALIAS = "screenerbot.wallet.identity.v1"
    private const val FILE_NAME = "device-id.keystore"

    fun getOrCreateMachineIdentifier(context: Context): String {
        val key = getOrCreateKey()
        val file = File(context.filesDir, "screenerbot/$FILE_NAME")
        if (file.exists()) {
            val lines = file.readLines()
            require(lines.size == 2) { "Stored device identity is malformed; wallet data was not changed." }
            val iv = Base64.decode(lines[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(lines[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
                .also { require(it.isNotBlank()) { "Stored device identity is empty." } }
        }

        val identifier = UUID.randomUUID().toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(identifier.toByteArray(StandardCharsets.UTF_8))
        file.parentFile?.mkdirs()
        file.writeText(
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "\n" +
                Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
        return identifier
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }
}
