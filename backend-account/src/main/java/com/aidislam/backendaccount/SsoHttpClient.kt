package com.aidislam.backendaccount

import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class SsoHttpClient(private val config: BackendAccountConfiguration) {
    fun exchangeCode(code: String, verifier: String): AccountSession = requestToken(mapOf(
        "grant_type" to "authorization_code",
        "code" to code,
        "client_id" to config.clientId,
        "redirect_uri" to config.redirectUri,
        "code_verifier" to verifier
    ))

    fun refresh(refreshToken: String): AccountSession = requestToken(mapOf(
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken,
        "client_id" to config.clientId
    ))

    fun userInfo(accessToken: String): String {
        val endpoint = requireNotNull(config.userInfoEndpoint) { "userInfoEndpoint is not configured" }
        val connection = open(endpoint)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        return read(connection)
    }

    private fun requestToken(parameters: Map<String, String>): AccountSession {
        val connection = open(config.tokenEndpoint)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val body = parameters.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val json = JSONObject(read(connection))
        return AccountSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").ifBlank { null },
            tokenType = json.optString("token_type", "Bearer"),
            expiresIn = if (json.has("expires_in")) json.getLong("expires_in") else null
        )
    }

    private fun open(endpoint: String): HttpURLConnection =
        (URL(Uri.parse(endpoint).toString()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }

    private fun read(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        if (connection.responseCode !in 200..299) error("SSO HTTP ${connection.responseCode}: $body")
        return body
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
