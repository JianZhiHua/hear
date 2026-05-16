package com.qingyi.hear.providers.netease

import android.annotation.SuppressLint
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonObject

object NetEaseCrypto {
    private const val IV = "0102030405060708"
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val PUBLIC_KEY = """
-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB
-----END PUBLIC KEY-----
"""

    private val random = SecureRandom()

    fun weapi(data: JsonObject, secretKey: String = randomSecret()): Map<String, String> {
        val text = data.toString()
        val first = aesCbcBase64(text, PRESET_KEY, IV)
        return mapOf(
            "params" to aesCbcBase64(first, secretKey, IV),
            "encSecKey" to rsaEncrypt(secretKey.reversed()),
        )
    }

    fun eapi(url: String, data: JsonObject): Map<String, String> {
        val text = data.toString()
        val digest = md5("nobody${url}use${text}md5forencrypt")
        val payload = "$url-36cd479b6b5-$text-36cd479b6b5-$digest"
        return mapOf("params" to aesEcbHex(payload, EAPI_KEY))
    }

    internal fun aesCbcBase64(text: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8)),
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    @SuppressLint("GetInstance")
    internal fun aesEcbHex(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8)).toHex(uppercase = true)
    }

    internal fun rsaEncrypt(text: String): String {
        val key = rsaPublicKey()
        val value = BigInteger(1, text.toByteArray(Charsets.UTF_8))
        val encrypted = value.modPow(key.publicExponent, key.modulus)
        return encrypted.toFixedHex(key.modulus.bitLength() / 8)
    }

    private fun rsaPublicKey(): RSAPublicKey {
        val encoded = PUBLIC_KEY
            .lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
        val spec = X509EncodedKeySpec(Base64.getDecoder().decode(encoded))
        return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
    }

    private fun randomSecret(): String =
        buildString {
            repeat(16) {
                append(BASE62[random.nextInt(BASE62.length)])
            }
        }

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5")
            .digest(text.toByteArray(Charsets.UTF_8))
            .toHex(uppercase = false)

    private fun ByteArray.toHex(uppercase: Boolean): String =
        joinToString("") { "%02x".format(it) }.let { if (uppercase) it.uppercase() else it }

    private fun BigInteger.toFixedHex(bytes: Int): String =
        toString(16).padStart(bytes * 2, '0').takeLast(bytes * 2)
}
