package com.aidislam.backendaccount

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.UUID

/** Entry point for the provider-neutral OAuth 2.0/OIDC client. */
class BackendAccount(
    context: Context,
    private val configuration: BackendAccountConfiguration,
    private val tokenStore: TokenStore = SharedPreferencesTokenStore(context.applicationContext)
) {
    private val appContext = context.applicationContext

    fun createAuthorizationRequest(): AuthorizationRequest {
        val state = randomValue()
        val verifier = Pkce.generateVerifier()
        val challenge = Pkce.challenge(verifier)
        tokenStore.savePendingState(state, verifier)
        val url = Uri.parse(configuration.authorizationEndpoint).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", configuration.clientId)
            .appendQueryParameter("redirect_uri", configuration.redirectUri)
            .appendQueryParameter("scope", configuration.scopes.joinToString(" "))
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
        return AuthorizationRequest(url, state)
    }

    fun browserIntent(): Intent = Intent(Intent.ACTION_VIEW, createAuthorizationRequest().url)

    fun parseCallback(uri: Uri): AuthorizationCallback {
        val error = uri.getQueryParameter("error")
        require(error == null) { "SSO authorization failed: $error" }
        val code = requireNotNull(uri.getQueryParameter("code")) { "Missing authorization code" }
        val state = requireNotNull(uri.getQueryParameter("state")) { "Missing authorization state" }
        val pending = requireNotNull(tokenStore.pendingState()) { "No pending authorization request" }
        require(state == pending.state) { "Invalid authorization state" }
        return AuthorizationCallback(code, state, pending.verifier)
    }

    fun exchangeCode(callback: AuthorizationCallback): AccountSession {
        require(callback.verifier == tokenStore.pendingState()?.verifier) { "Invalid PKCE verifier" }
        val session = SsoHttpClient(configuration).exchangeCode(callback.code, callback.verifier)
        tokenStore.save(session)
        tokenStore.clearPendingState()
        return session
    }

    fun refresh(session: AccountSession = requireNotNull(tokenStore.load()) { "No saved session" }): AccountSession {
        val refreshed = SsoHttpClient(configuration).refresh(session.refreshToken ?: error("No refresh token"))
        tokenStore.save(refreshed)
        return refreshed
    }

    fun userInfo(session: AccountSession = requireNotNull(tokenStore.load()) { "No saved session" }): String =
        SsoHttpClient(configuration).userInfo(session.accessToken)

    fun currentSession(): AccountSession? = tokenStore.load()
    fun logout() = tokenStore.clear()

    private fun randomValue() = UUID.randomUUID().toString().replace("-", "")
}

data class BackendAccountConfiguration(
    val clientId: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val userInfoEndpoint: String? = null,
    val redirectUri: String,
    val scopes: Set<String> = setOf("openid", "profile", "email")
)

data class AuthorizationRequest(val url: Uri, val state: String)
data class AuthorizationCallback(val code: String, val state: String, internal val verifier: String)
data class AccountSession(val accessToken: String, val refreshToken: String?, val tokenType: String, val expiresIn: Long?)
