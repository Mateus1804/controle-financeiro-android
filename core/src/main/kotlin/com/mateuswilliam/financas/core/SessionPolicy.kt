package com.mateuswilliam.financas.core

/** A sessão vive apenas em memória; não representa criptografia do banco. */
object SessionPolicy {
    const val BACKGROUND_TIMEOUT_MILLIS = 30_000L
    fun requiresAuthentication(enabled: Boolean, authenticated: Boolean, backgroundAt: Long?, now: Long, deviceLocked: Boolean): Boolean =
        enabled && (!authenticated || deviceLocked || (backgroundAt != null && now - backgroundAt >= BACKGROUND_TIMEOUT_MILLIS))
}
