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

/**
 * 加密的 Cookie 存储实现
 *
 * 优化点：
 * 1. 使用 Android Keystore 安全存储密钥
 * 2. 支持凭证过期检测
 * 3. 记录凭证存储时间
 * 4. 增强错误处理
 */
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
        preferences.edit {
            putString(keyFor(source), payload)
            putLong(timestampKeyFor(source), System.currentTimeMillis())
        }
    }

    override fun clearCookie(source: String) {
        preferences.edit {
            remove(keyFor(source))
            remove(timestampKeyFor(source))
        }
    }

    /**
     * 获取凭证存储时间戳
     */
    fun getCredentialTimestamp(source: String): Long? {
        val timestamp = preferences.getLong(timestampKeyFor(source), 0L)
        return if (timestamp > 0) timestamp else null
    }

    /**
     * 检查凭证是否可能已过期
     *
     * 优化：基于存储时间和常见过期策略判断
     */
    override fun isCredentialLikelyExpired(source: String): Boolean {
        // 先检查基础过期检测
        if (super.isCredentialLikelyExpired(source)) return true

        // 检查存储时间（默认 7 天过期）
        val storedAt = getCredentialTimestamp(source) ?: return true
        val elapsed = System.currentTimeMillis() - storedAt
        val maxAge = 7 * 24 * 60 * 60 * 1000L // 7 天

        return elapsed > maxAge
    }

    /**
     * 获取凭证状态信息（增强版）
     */
    override fun getCredentialStatus(source: String): CredentialStatus {
        val cookie = getCookie(source)
        if (cookie == null || cookie.isBlank()) {
            return CredentialStatus.NOT_SET
        }

        val storedAt = getCredentialTimestamp(source)
        if (storedAt == null) {
            return CredentialStatus.LIKELY_EXPIRED
        }

        val elapsed = System.currentTimeMillis() - storedAt
        val warningThreshold = 5 * 24 * 60 * 60 * 1000L // 5 天警告
        val maxAge = 7 * 24 * 60 * 60 * 1000L // 7 天过期

        return when {
            elapsed > maxAge -> CredentialStatus.LIKELY_EXPIRED
            elapsed > warningThreshold -> CredentialStatus.VALID // 可以添加 WARNING 状态
            else -> CredentialStatus.VALID
        }
    }

    private fun keyFor(source: String): String = "cookie.$source"

    private fun timestampKeyFor(source: String): String = "cookie.timestamp.$source"

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
