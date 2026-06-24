package com.qingyi.hear.storage

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
    private val QQ_SP_DIR = "/data/data/$QQ_PKG/shared_prefs"
    // 覆盖多个版本的 SP 文件名
    private val QQ_SP_FILES = listOf(
        "QQMusicPreferences.xml",
        "login_info.xml",
        "sp_qqmusic_settings.xml",
        "qqmusic_prefs.xml",
        "user_info.xml",
        "config.xml",
        "pref_login.xml",
        "sp_login.xml",
        "qqmusic_login.xml",
    )
    // 覆盖多种字段名变体
    private val QQ_COOKIE_FIELDS = listOf(
        "uin", "qqmusic_uin", "p_uin", "login_uin",
        "qqmusic_key", "p_skey", "skey", "p_lskey",
        "authst", "qqmusic_auth",
    )

    // 网易云音乐
    private const val NE_PKG = "com.netease.cloudmusic"
    private val NE_SP_DIR = "/data/data/$NE_PKG/shared_prefs"
    private val NE_SP_FILES = listOf(
        "cookie_prefs.xml",
        "user_info.xml",
        "play_prefs.xml",
        "cloudmusic_prefs.xml",
    )
    private val NE_COOKIE_FIELDS = listOf("MUSIC_U", "MUSIC_A", "__csrf", "NTESPCID", "deviceId")

    /** 提取结果，携带诊断信息 */
    data class ExtractResult(
        val cookie: String?,
        val error: String? = null,
        val diagnostics: String? = null,
    )

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
     * 从 QQ 音乐提取 Cookie（带诊断）
     */
    suspend fun extractQQCookieWithDiag(): ExtractResult {
        if (!isAvailable()) return ExtractResult(null, "Shizuku 未就绪")
        val readResult = readSpFieldsWithDiag(QQ_SP_DIR, QQ_SP_FILES, QQ_COOKIE_FIELDS)
        if (readResult.values.isEmpty()) {
            val diag = buildDiagnostics(QQ_SP_DIR, readResult, QQ_COOKIE_FIELDS)
            return ExtractResult(null, "未读取到 QQ 音乐登录信息，请确认已登录", diag)
        }
        val uin = readResult.values["uin"]
            ?: readResult.values["qqmusic_uin"]
            ?: readResult.values["p_uin"]
            ?: readResult.values["login_uin"]
        if (uin == null) {
            val diag = buildDiagnostics(QQ_SP_DIR, readResult, QQ_COOKIE_FIELDS)
            return ExtractResult(null, "QQ 音乐 Cookie 缺少 uin 字段", diag)
        }
        val cookie = buildString {
            append("uin=o").append(uin.removePrefix("o"))
            readResult.values["qqmusic_key"]?.let { append("; qqmusic_key=").append(it) }
            readResult.values["p_skey"]?.let { append("; p_skey=").append(it) }
            readResult.values["skey"]?.let { append("; skey=").append(it) }
            readResult.values["p_lskey"]?.let { append("; p_lskey=").append(it) }
            readResult.values["authst"]?.let { append("; authst=").append(it) }
            readResult.values["qqmusic_auth"]?.let { append("; qqmusic_auth=").append(it) }
        }
        val diag = buildDiagnostics(QQ_SP_DIR, readResult, QQ_COOKIE_FIELDS)
        Log.i(TAG, "QQ Cookie 提取成功: $diag")
        return ExtractResult(cookie, diagnostics = diag)
    }

    /**
     * 从 QQ 音乐提取 Cookie（兼容旧接口）
     */
    suspend fun extractQQCookie(): Result<String> = runCatching {
        val result = extractQQCookieWithDiag()
        result.cookie ?: throw IllegalStateException(result.error ?: "提取失败")
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
     * 运行诊断，列出指定 SP 目录下的文件和可读取的内容
     */
    suspend fun runQQDiagnostics(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.appendLine("=== QQ 音乐 SP 诊断 ===")
        sb.appendLine("目标目录: $QQ_SP_DIR")

        // 1. 列出目录内容
        val lsOutput = shizukuLs(QQ_SP_DIR)
        if (lsOutput != null) {
            sb.appendLine("目录内容:")
            sb.appendLine(lsOutput)
        } else {
            sb.appendLine("无法列出目录（权限不足或目录不存在）")
            // 尝试 ls 父目录
            val parentLs = shizukuLs("/data/data/$QQ_PKG")
            if (parentLs != null) {
                sb.appendLine("APP 数据目录内容:")
                sb.appendLine(parentLs)
            }
        }

        // 2. 尝试读取每个 SP 文件
        sb.appendLine("\n--- 逐一尝试读取 ---")
        for (file in QQ_SP_FILES) {
            val path = "$QQ_SP_DIR/$file"
            val content = shizukuCat(path)
            if (content != null) {
                sb.appendLine("✓ $file (${content.length} 字节)")
                // 提取所有字段名
                val keys = Regex("""<string\s+name="([^"]+)"""").findAll(content)
                    .map { it.groupValues[1] }.toList()
                if (keys.isNotEmpty()) {
                    sb.appendLine("  字段: ${keys.take(20).joinToString(", ")}")
                }
            } else {
                sb.appendLine("✗ $file (读取失败)")
            }
        }

        Log.d(TAG, sb.toString())
        sb.toString()
    }

    /**
     * 带诊断的 SP 字段读取
     */
    private data class SpReadResult(
        val values: Map<String, String>,
        val filesRead: List<String>,
        val filesFailed: List<String>,
    )

    private suspend fun readSpFieldsWithDiag(
        spDir: String,
        spFiles: List<String>,
        fields: List<String>,
    ): SpReadResult = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        val filesRead = mutableListOf<String>()
        val filesFailed = mutableListOf<String>()

        for (file in spFiles) {
            val path = "$spDir/$file"
            Log.d(TAG, "尝试读取: $path")
            val xml = shizukuCat(path)
            if (xml == null) {
                Log.d(TAG, "  → 读取失败")
                filesFailed.add(file)
                continue
            }
            Log.d(TAG, "  → 读取成功 (${xml.length} 字节)")
            filesRead.add(file)

            for (field in fields) {
                if (result.containsKey(field)) continue
                // <string name="key">value</string>
                val pattern = Regex("""<string\s+name="$field"[^>]*>([^<]*)</string>""")
                pattern.find(xml)?.groupValues?.get(1)?.let {
                    result[field] = it
                    Log.d(TAG, "  找到字段 $field = ${it.take(20)}...")
                }
                // <boolean name="key" value="val" />
                val boolPattern = Regex("""<boolean\s+name="$field"[^>]*value="([^"]*)"""")
                boolPattern.find(xml)?.groupValues?.get(1)?.let { result[field] = it }
                // <int name="key" value="123" />
                val intPattern = Regex("""<int\s+name="$field"[^>]*value="([^"]*)"""")
                intPattern.find(xml)?.groupValues?.get(1)?.let { result[field] = it }
            }
            if (fields.all { result.containsKey(it) }) break
        }

        // SP 文件全部失败时，尝试列出目录
        if (result.isEmpty() && filesRead.isEmpty()) {
            val lsResult = shizukuLs(spDir)
            Log.d(TAG, "SP 目录内容: $lsResult")
        }

        SpReadResult(result, filesRead, filesFailed)
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
        for (file in spFiles) {
            val path = "$spDir/$file"
            Log.d(TAG, "尝试读取: $path")
            val xml = shizukuCat(path)
            if (xml == null) {
                Log.d(TAG, "  → 读取失败")
                continue
            }
            Log.d(TAG, "  → 读取成功 (${xml.length} 字节)")
            for (field in fields) {
                if (result.containsKey(field)) continue
                val pattern = Regex("""<string\s+name="$field"[^>]*>([^<]*)</string>""")
                pattern.find(xml)?.groupValues?.get(1)?.let { result[field] = it }
                val boolPattern = Regex("""<boolean\s+name="$field"[^>]*value="([^"]*)"""")
                boolPattern.find(xml)?.groupValues?.get(1)?.let { result[field] = it }
                val intPattern = Regex("""<int\s+name="$field"[^>]*value="([^"]*)"""")
                intPattern.find(xml)?.groupValues?.get(1)?.let { result[field] = it }
            }
            if (fields.all { result.containsKey(it) }) break
        }

        if (result.isEmpty()) {
            val lsResult = shizukuLs(spDir)
            Log.d(TAG, "SP 目录内容: $lsResult")
        }

        result
    }

    /**
     * 构建诊断摘要
     */
    private fun buildDiagnostics(
        spDir: String,
        readResult: SpReadResult,
        requiredFields: List<String>,
    ): String = buildString {
        append("SP目录: $spDir; ")
        if (readResult.filesRead.isNotEmpty()) {
            append("已读: ${readResult.filesRead.joinToString(", ")}; ")
        }
        if (readResult.filesFailed.isNotEmpty()) {
            append("失败: ${readResult.filesFailed.joinToString(", ")}; ")
        }
        val foundFields = readResult.values.keys.toList()
        val missingFields = requiredFields.filter { !readResult.values.containsKey(it) }
        if (foundFields.isNotEmpty()) {
            append("已获取字段: ${foundFields.joinToString(", ")}; ")
        }
        if (missingFields.isNotEmpty()) {
            append("未找到: ${missingFields.joinToString(", ")}")
        }
    }

    /**
     * 通过 Shizuku 执行 cat 命令读取文件内容
     */
    private fun shizukuCat(path: String): String? {
        return try {
            val process = shizukuExec(arrayOf("cat", path))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val errOutput = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.d(TAG, "shizukuCat($path) exit=$exitCode stderr=$errOutput")
            }
            if (exitCode == 0 && output.isNotBlank()) output else null
        } catch (e: Exception) {
            Log.d(TAG, "shizukuCat($path) 异常: ${e.message}")
            null
        }
    }

    /**
     * 通过 Shizuku 列出目录内容（调试用）
     */
    private fun shizukuLs(path: String): String? {
        return try {
            val process = shizukuExec(arrayOf("ls", "-la", path))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val errOutput = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.d(TAG, "shizukuLs($path) exit=$exitCode stderr=$errOutput")
            }
            output.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.d(TAG, "shizukuLs($path) 异常: ${e.message}")
            null
        }
    }

    /**
     * 通过 Shizuku IPC 执行 shell 命令
     *
     * 优先使用 Shizuku.newProcess() API（v11+），失败时使用反射。
     * 注意：Runtime.exec() 不在此作为 fallback，因为它以 Hear 自身身份运行，
     * 无法读取其他 APP 的数据。
     */
    private fun shizukuExec(command: Array<String>): Process {
        // Shizuku.newProcess 是 private 方法，必须通过反射调用
        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            return method.invoke(null, command, null, null) as Process
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku.newProcess 反射调用失败: ${e.message}")
        }

        // 不使用 Runtime.exec() 作为 fallback —— 它以 Hear 自身身份运行，无法读取其他 APP 数据
        throw IllegalStateException(
            "Shizuku 进程创建失败。请确认 Shizuku 服务正在运行，" +
                "并尝试在 Shizuku APP 中重新激活服务（重启 ADB 或重启手机）。"
        )
    }
}
