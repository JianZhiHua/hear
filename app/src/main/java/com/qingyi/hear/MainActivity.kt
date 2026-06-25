package com.qingyi.hear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.qingyi.hear.ui.HearApp
import com.qingyi.hear.ui.theme.HearTheme
import com.qingyi.hear.widget.HearWidgetReceiver
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "未授予通知权限，后台播放控制可能不可见", Toast.LENGTH_LONG).show()
        }
    }

    // ---- Shizuku 权限请求（Activity 级别） ----

    /** 存放当前等待结果的权限回调 */
    private var shizukuPermissionCallback: ((Boolean) -> Unit)? = null

    /** Activity 级别的 Shizuku 权限结果监听器 */
    private val shizukuPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "Shizuku 权限结果: requestCode=$requestCode granted=$granted")
            val callback = shizukuPermissionCallback
            shizukuPermissionCallback = null
            callback?.invoke(granted)
        }

    /** Activity 级别的 Shizuku Binder 接收监听器 */
    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku Binder 已接收")
    }

    /**
     * 从 Activity 上下文发起 Shizuku 权限请求。
     * 返回 [Boolean] 表示是否授权成功。
     */
    fun requestShizukuPermission(callback: (Boolean) -> Unit) {
        Log.i(TAG, "requestShizukuPermission: 从 Activity 发起权限请求")
        shizukuPermissionCallback = callback
        try {
            Shizuku.requestPermission(0)
        } catch (e: Exception) {
            Log.e(TAG, "requestShizukuPermission: Shizuku.requestPermission 异常: ${e.message}", e)
            shizukuPermissionCallback = null
            callback(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleWidgetIntent(intent)

        // 注册 Activity 级别的 Shizuku 监听器
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
            Shizuku.addBinderReceivedListener(shizukuBinderReceivedListener)
            Log.i(TAG, "Shizuku 监听器已注册")
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku 监听器注册失败（可能未安装）: ${e.message}")
        }

        setContent {
            HearTheme {
                HearApp(
                    onRequestShizukuPermission = { callback ->
                        requestShizukuPermission(callback)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理 Shizuku 监听器
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        } catch (_: Exception) {
            // Shizuku 未安装，忽略
        }
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val container = (application as HearApplication).container
        val manager = container.playbackManager
        when (action) {
            HearWidgetReceiver.ACTION_TOGGLE -> manager.toggle()
            HearWidgetReceiver.ACTION_PREVIOUS -> manager.previous()
            HearWidgetReceiver.ACTION_NEXT -> manager.next()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
