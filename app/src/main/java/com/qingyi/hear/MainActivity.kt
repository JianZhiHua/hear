package com.qingyi.hear

import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qingyi.hear.data.HearNotificationListener
import com.qingyi.hear.ui.HearApp
import com.qingyi.hear.ui.theme.HearTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HearTheme {
                HearApp()
            }
        }
    }

    /**
     * 检查通知监听权限
     */
    fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (flat.isNullOrBlank()) return false
        val cn = ComponentName(this, HearNotificationListener::class.java).flattenToString()
        return flat.split(":").any { TextUtils.equals(it, cn) }
    }
}
