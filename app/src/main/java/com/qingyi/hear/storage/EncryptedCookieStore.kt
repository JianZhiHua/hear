package com.qingyi.hear.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedCookieStore(context: Context) : CredentialStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("hear.credentials", Context.MODE_PRIVATE)

    override fun getCookie(source: String): String? {
        val payload = preferences.getString(keyFor(source), null) ?: return null
        return runCatching {
            val parts = payload.split(":")
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    override fun setCookie(source: String, cookie: String) {
        val trimmed = cookie.trim()
        if (trimmed.isEmpty()) {
            clearCookie(source)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = buildString {
            append(Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            append(":")
            append(Base64.encodeToString(cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
        }
        preferences.edit { putString(keyFor(source), payload) }
    }

    override fun clearCookie(source: String) {
        preferences.edit { remove(keyFor(source)) }
    }

    private fun keyFor(source: String): String = "cookie.$source"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "hear.cookie.store"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
