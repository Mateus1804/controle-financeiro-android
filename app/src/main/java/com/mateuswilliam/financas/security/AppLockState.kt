package com.mateuswilliam.financas.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

enum class AuthenticationAction { UNLOCK, ENABLE, DISABLE }

class AppLockState : ViewModel() {
    var authenticated by mutableStateOf(false)
    var authenticating by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var backgroundAt: Long? = null
    var action: AuthenticationAction? = null
    var credentialInFlight = false
}
