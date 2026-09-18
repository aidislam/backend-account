package com.aidislam.backendaccount

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

interface TokenStore {
    fun save(session: AccountSession)
    fun load(): AccountSession?
    fun clear()
    fun savePendingState(state: String, verifier: String)
    fun pendingState(): PendingAuthorization?
    fun clearPendingState()
}

data class PendingAuthorization(val state: String, val verifier: String)

/** Simple persistence adapter. Replace with Keystore-backed storage for production. */
class SharedPreferencesTokenStore(context: Context) : TokenStore {
    private val prefs: SharedPreferences = context.getSharedPreferences("backend_account", Context.MODE_PRIVATE)
    override fun save(session: AccountSession) = prefs.edit()
        .putString("access", session.accessToken).putString("refresh", session.refreshToken)
        .putString("type", session.tokenType).putLong("expires", session.expiresIn ?: -1).apply()
    override fun load(): AccountSession? = prefs.getString("access", null)?.let {
        AccountSession(it, prefs.getString("refresh", null), prefs.getString("type", "Bearer")!!,
            prefs.getLong("expires", -1).takeIf { value -> value >= 0 })
    }
    override fun clear() = prefs.edit().clear().apply()
    override fun savePendingState(state: String, verifier: String) = prefs.edit().putString("state", state).putString("verifier", verifier).apply()
    override fun pendingState() = prefs.getString("state", null)?.let { PendingAuthorization(it, prefs.getString("verifier", "")!!) }
    override fun clearPendingState() = prefs.edit().remove("state").remove("verifier").apply()
}

internal object Pkce {
    fun generateVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    fun challenge(verifier: String): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
}
