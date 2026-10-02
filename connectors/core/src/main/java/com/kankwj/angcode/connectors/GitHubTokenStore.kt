package com.kankwj.angcode.connectors

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class GitHubTokenStore(
    context: Context
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "angcode-github",
        Context.MODE_PRIVATE
    )

    fun save(token: String) {
        val normalized = token.trim()
        require(normalized.length in 20..512) { "Token GitHub inválido" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))

        preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? {
        val ivText = preferences.getString(KEY_IV, null) ?: return null
        val cipherText = preferences.getString(KEY_CIPHERTEXT, null) ?: return null

        return runCatching {
            val iv = Base64.decode(ivText, Base64.NO_WRAP)
            val encrypted = Base64.decode(cipherText, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv)
            )
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    fun hasToken(): Boolean = !load().isNullOrBlank()

    fun clear() {
        preferences.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHERTEXT)
            .apply()
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
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "angcode.github.token.v1"
        private const val KEY_IV = "token_iv"
        private const val KEY_CIPHERTEXT = "token_ciphertext"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
