package com.mateuswilliam.financas

import android.content.Intent
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateuswilliam.financas.notifications.ReviewNotifications
import com.mateuswilliam.financas.ui.FinanceApp
import com.mateuswilliam.financas.ui.FinanceTheme
import com.mateuswilliam.financas.ui.LockScreen
import com.mateuswilliam.financas.security.AppLockState
import com.mateuswilliam.financas.security.AuthenticationAction
import com.mateuswilliam.financas.core.SessionPolicy
import com.mateuswilliam.financas.notifications.RecurringReminders
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private var resumeCount by mutableIntStateOf(0)
    private var draftFromNotification by mutableStateOf<String?>(null)
    private var planningFromNotification by mutableStateOf(false)
    private lateinit var session: AppLockState
    private lateinit var financeModel: FinanceViewModel
    private var biometricSignal: CancellationSignal? = null
    private val credential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        completeAuthentication(it.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FinanceApplication
        session = ViewModelProvider(this)[AppLockState::class.java]
        financeModel = ViewModelProvider(this)[FinanceViewModel::class.java]
        updateScreenProtection(app.settings.snapshot().secureScreen)
        enableEdgeToEdge()
        draftFromNotification = intent.getStringExtra(ReviewNotifications.EXTRA_DRAFT_ID)
        planningFromNotification = intent.getBooleanExtra(RecurringReminders.EXTRA_PLANNING, false)
        setContent {
            val settings by financeModel.preferences.state.collectAsStateWithLifecycle()
            val dark = settings.appearance.usesDarkTheme(isSystemInDarkTheme())
            FinanceTheme(darkTheme = dark) {
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    updateScreenProtection(settings.secureScreen)
                    window.setBackgroundDrawable(ColorDrawable(background))
                    val bars = if (dark) SystemBarStyle.dark(background) else SystemBarStyle.light(background, background)
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                }
                val savedUi = rememberSaveableStateHolder()
                if (settings.lockEnabled && !session.authenticated) LockScreen(session.authenticating, session.message) {
                    authenticate(AuthenticationAction.UNLOCK)
                } else savedUi.SaveableStateProvider("finance") {
                    FinanceApp(financeModel, resumeCount, draftFromNotification,
                        consumeDraft = { draftFromNotification = null; intent.removeExtra(ReviewNotifications.EXTRA_DRAFT_ID) },
                        incomingPlanning = planningFromNotification,
                        consumePlanning = { planningFromNotification = false; intent.removeExtra(RecurringReminders.EXTRA_PLANNING) },
                        onLockChange = { authenticate(if (it) AuthenticationAction.ENABLE else AuthenticationAction.DISABLE) })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
        if (::session.isInitialized) {
            val app = application as FinanceApplication
            val keyguard = getSystemService(KeyguardManager::class.java)
            if (SessionPolicy.requiresAuthentication(app.settings.snapshot().lockEnabled, session.authenticated,
                    session.backgroundAt, SystemClock.elapsedRealtime(), keyguard.isDeviceLocked)) session.authenticated = false
            session.backgroundAt = null
            app.background.launch { runCatching { app.repository.notifyDue(LocalDate.now()) { RecurringReminders.show(this@MainActivity, it) } } }
        }
    }
    override fun onStop() {
        if (::session.isInitialized && !isChangingConfigurations) {
            session.backgroundAt = SystemClock.elapsedRealtime()
            val settings = (application as FinanceApplication).settings.snapshot()
            if (settings.lockEnabled && (getSystemService(KeyguardManager::class.java).isDeviceLocked || !getSystemService(PowerManager::class.java).isInteractive))
                session.authenticated = false
        }
        super.onStop()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        draftFromNotification = intent.getStringExtra(ReviewNotifications.EXTRA_DRAFT_ID)
        planningFromNotification = intent.getBooleanExtra(RecurringReminders.EXTRA_PLANNING, false)
    }

    private fun updateScreenProtection(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun authenticate(action: AuthenticationAction) {
        if (session.authenticating) return
        if (!getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            authenticationError("Configure PIN, padrão ou senha no Android para utilizar a proteção.")
            return
        }
        session.action = action
        session.authenticating = true
        session.message = null
        if (Build.VERSION.SDK_INT >= 28) {
            val executor = ContextCompat.getMainExecutor(this)
            val builder = BiometricPrompt.Builder(this).setTitle("Controle Financeiro")
                .setDescription("Confirme a identidade para acessar ou alterar a proteção do aplicativo.")
            if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            else builder.setNegativeButton("Usar PIN, padrão ou senha", executor) { _, _ -> launchCredential() }
            biometricSignal = CancellationSignal()
            builder.build().authenticate(biometricSignal!!, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { completeAuthentication(true) }
                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (isChangingConfigurations || isDestroyed) return
                    if (session.credentialInFlight) return
                    if (Build.VERSION.SDK_INT < 30 && code !in setOf(BiometricPrompt.BIOMETRIC_ERROR_CANCELED, BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED)) launchCredential()
                    else completeAuthentication(false)
                }
            })
        } else launchCredential()
    }

    private fun launchCredential() {
        if (session.credentialInFlight) return
        val intent = getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent(
            "Controle Financeiro", "Confirme PIN, padrão ou senha para continuar.")
        if (intent == null) { authenticationError("A autenticação do aparelho está indisponível."); session.authenticating = false; session.action = null }
        else {
            session.credentialInFlight = true
            runCatching { credential.launch(intent) }.onFailure { completeAuthentication(false) }
        }
    }

    private fun completeAuthentication(success: Boolean) {
        val action = session.action
        session.action = null
        session.credentialInFlight = false
        session.authenticating = false
        if (!success) { authenticationError("Autenticação não concluída. A configuração de proteção foi mantida."); return }
        try {
            when (action) {
                AuthenticationAction.ENABLE -> financeModel.preferences.setLockEnabled(true)
                AuthenticationAction.DISABLE -> financeModel.preferences.setLockEnabled(false)
                AuthenticationAction.UNLOCK -> Unit
                null -> return
            }
        } catch (exception: IllegalStateException) {
            authenticationError(exception.message ?: "A alteração da proteção não foi salva.")
            return
        }
        session.authenticated = true
        session.backgroundAt = null
        session.message = null
    }

    private fun authenticationError(message: String) { session.message = message; financeModel.message.value = message }

    override fun onDestroy() {
        if (isChangingConfigurations && !session.credentialInFlight) { session.authenticating = false; session.action = null }
        biometricSignal?.cancel()
        super.onDestroy()
    }
}
