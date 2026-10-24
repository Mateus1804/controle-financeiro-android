package com.mateuswilliam.financas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
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
            FinanceTheme {
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
