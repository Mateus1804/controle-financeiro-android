package com.mateuswilliam.financas

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateuswilliam.financas.notifications.ReviewNotifications
import com.mateuswilliam.financas.ui.FinanceApp
import com.mateuswilliam.financas.ui.FinanceTheme

class MainActivity : ComponentActivity() {
    private var resumeCount by mutableIntStateOf(0)
    private var draftFromNotification by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        draftFromNotification = intent.getStringExtra(ReviewNotifications.EXTRA_DRAFT_ID)
        val model = ViewModelProvider(this)[FinanceViewModel::class.java]
        setContent {
            val settings by model.preferences.state.collectAsStateWithLifecycle()
            val dark = settings.appearance.usesDarkTheme(isSystemInDarkTheme())
            SideEffect {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            FinanceTheme(darkTheme = dark) {
                FinanceApp(model, resumeCount, draftFromNotification) { draftFromNotification = null }
            }
        }
    }

    override fun onResume() { super.onResume(); resumeCount++ }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        draftFromNotification = intent.getStringExtra(ReviewNotifications.EXTRA_DRAFT_ID)
    }
}
