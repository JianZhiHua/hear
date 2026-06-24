package com.qingyi.hear.storage

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.resume

/**
 * 通过 Shizuku 读取 QQ 音乐 / 网易云音乐的 SharedPreferences，自动拼装 Cookie。
 *
 * 前提：用户已安装并激活 Shizuku（ADB 或 Root 模式均可）。
 */
object ShizukuCookieExtractor {

    private const val TAG = "ShizukuCookie"

    // QQ 音乐
    private const val QQ_PKG = "com.tencent.qqmusic"
    private const val QQ_SP_DIR = "/data/data/$QQ_PKG/shared_prefs"
    private val QQ_SP_FILES = listOf(
        "QQMusicPreferences.xml",
        "login_info.xml",
        "sp_qqmusic_settings.xml",
    )
    private val QQ_COOKIE_FIELDS = listOf("uin", "qqmusic_uin", "p_uin", "qqmusic_key", "p_skey", "skey")

    // 网易云音乐
    private const val NE_PKG = "com.netease.cloudmusic"
    private const val NE_SP_DIR = "/data/data/$NE_PKG/shared_prefs"
    private val NE_SP_FILES = listOf(
        "cookie_prefs.xml",
        "user_info.xml",
        "play_prefs.xml",
    )
    private val NE_COOKIE_FIELDS = listOf("MUSIC_U", "MUSIC_A", "__csrf", "NTESPCID", "deviceId")

    /**
     * 检查 Shizuku 是否可用且已授权
     */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查 Shizuku 是否已安装（不论是否授权）
     */
    fun isInstalled(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 请求 Shizuku 权限，返回是否授权成功
     */
    suspend fun requestPermission(): Boolean {
        if (isAvailable()) return true
        return suspendCancellableCoroutine { cont ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            cont.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
            Shizuku.requestPermission(0)
        }
    }

    /**
     * 从 QQ 音乐提取 Cookie
     */
    suspend fun extractQQCookie(): Result<String> = runCatching {
        if (!isAvailable()) throw IllegalStateException("Shizuku 未就绪")
        val values = readSpFields(QQ_SP_DIR, QQ_SP_FILES, QQ_COOKIE_FIELDS)
        if (values.isEmpty()) throw IllegalStateException("未读取到 QQ 音乐登录信息，请确认已登录")
        val uin = values["uin"] ?: values["qqmusic_uin"] ?: values["p_uin"]
            ?: throw IllegalStateException("QQ 音乐 Cookie 缺少 uin 字段")
        buildString {
            append("uin=o").append(uin.removePrefix("o"))
            values["qqmusic_key"]?.let { append("; qqmusic_key=").append(it) }
            values["p_skey"]?.let { append("; p_skey=").append(it) }
            values["skey"]?.let { append("; skey=").append(it) }
        }
    }

    /**
     * 从网易云音乐提取 Cookie
     */
    suspend fun extractNetEaseCookie(): Result<String> = runCatching {
        if (!isAvailable()) throw IllegalStateException("Shizuku 未就绪")
        val values = readSpFields(NE_SP_DIR, NE_SP_FILES, NE_COOKIE_FIELDS)
        if (values.isEmpty()) throw IllegalStateException("未读取到网易云登录信息，请确认已登录")
        val musicU = values["MUSIC_U"]
            ?: throw IllegalStateException("网易云 Cookie 缺少 MUSIC_U 字段，请确认已登录")
        buildString {
            append("MUSIC_U=").append(musicU)
            values["MUSIC_A"]?.let { append("; MUSIC_A=").append(it) }
            values["__csrf"]?.let { append("; __csrf=").append(it) }
            values["NTESPCID"]?.let { append("; NTESPCID=").append(it) }
            values["deviceId"]?.let { append("; deviceId=").append(it) }
        }
    }

    /**
     * 通过 Shizuku 执行 shell 命令读取 SharedPreferences XML 文件，提取指定字段值。
     */
    private suspend fun readSpFields(
        spDir: String,
        spFiles: List<String>,
        fields: List<String>,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        // 尝试所有可能的 SP 文件
        for (file in spFiles) {
            val path = "$spDir/$file"
            val xml = shizukuCat(path) ?: continue
            // 解析 SharedPreferences XML: <string name="key">value</string>
            for (field in fields) {
                if (result.containsKey(field)) continue
                val pattern = Regex("""<string\s+name="$field"[^>]*>([^<]*)</string>""")
                pattern.find(xml)?.groupValues?.get(1)?.let { result[field] = it }
                // 也尝试 boolean/int 格式
                val boolPattern = Regex("""<boolean\s+name="$field"[^>]*value="([^"]*)"""")
                boolPattern.find(xml)?.groupValues?.get(1)?.let { result[field] = it }
            }
            // 如果已经找到所有字段，提前返回
            if (fields.all { result.containsKey(it) }) break
        }

        // 如果 SP 文件没找到，尝试列出目录
        if (result.isEmpty()) {
            val lsResult = shizukuLs(spDir)
            Log.d(TAG, "SP 目录内容: $lsResult")
        }

        result
    }

    /**
     * 通过 Shizuku 执行 cat 命令读取文件内容
     */
    private fun shizukuCat(path: String): String? {
        return try {
            val process = Shizuku.newProcess(arrayOf("cat", path), null, null)
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotBlank()) output else null
        } catch (e: Exception) {
            Log.d(TAG, "shizukuCat($path) 失败: ${e.message}")
            null
        }
    }

    /**
     * 通过 Shizuku 列出目录内容（调试用）
     */
    private fun shizukuLs(path: String): String? {
        return try {
            val process = Shizuku.newProcess(arrayOf("ls", "-la", path), null, null)
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.waitFor()
            output.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
