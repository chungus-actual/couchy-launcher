package com.conreo.couchytv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/**
 * Fork: one line for the status bar from a feed server's `GET <url>/ambient`: the weather over the
 * feed's top item. The same feed the Nightdrop screensaver reads (its docs/feed.md has the shape).
 * Read only: nothing is sent but the request line and, when one is set, a bearer token.
 *
 * Where the feed is ([feedSource]): LauncherConfig.feedUrl (Settings > Status bar > Feed address, or a
 * loaded configuration file, which can also carry feedCa and feedToken); else `assets/feed.json`,
 * baked in at build time with -Pbake=<dir>; else nowhere, and the line never shows. With a CA, that CA
 * is the only one trusted for the feed.
 */
data class FeedLine(val weather: String, val kicker: String, val headline: String, val tone: String)

data class FeedSource(val url: String, val caPem: String, val token: String)

private const val EVERY_MS = 60_000L
private const val STALE_MS = 30 * 60_000L

fun feedSource(context: Context, config: LauncherConfig): FeedSource? {
    if (config.feedUrl.isNotBlank()) {
        return FeedSource(config.feedUrl.trim().trimEnd('/'), config.feedCa.trim(), config.feedToken.trim())
    }
    val baked = runCatching { context.assets.open("feed.json").bufferedReader().use { it.readText() } }
        .getOrNull() ?: return null
    // a CA that is named but missing turns the feed off rather than falling back to the system store
    return runCatching {
        val o = JSONObject(baked)
        val ca = o.optString("ca").trim()
        val pem = when {
            ca.isEmpty() -> ""
            ca.contains("-----BEGIN") -> ca
            else -> context.assets.open(ca).bufferedReader().use { it.readText() }
        }
        FeedSource(o.optString("url").trim().trimEnd('/'), pem, o.optString("token").trim())
    }.getOrNull()?.takeIf { it.url.isNotEmpty() }
}

/** Polls while collected (the launcher collects it only while visible). A failed read keeps the last
 *  answer for STALE_MS, then the line goes away: an hour-old calendar row is worse than none. */
fun feedLineFlow(source: FeedSource?): Flow<FeedLine?> = if (source == null) flowOf(null) else flow {
    var last: FeedLine? = null
    var lastAt = 0L
    while (true) {
        val got = runCatching { fetch(source) }.getOrNull()
        if (got != null) { last = got; lastAt = System.currentTimeMillis() }
        emit(last?.takeIf { System.currentTimeMillis() - lastAt < STALE_MS })
        delay(EVERY_MS)
    }
}.flowOn(Dispatchers.IO).distinctUntilChanged()

private fun fetch(src: FeedSource): FeedLine? {
    val c = URL(src.url + "/ambient").openConnection() as HttpURLConnection
    if (c is HttpsURLConnection && src.caPem.isNotEmpty()) c.sslSocketFactory = trusting(src.caPem)
    if (src.token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer ${src.token}")
    c.connectTimeout = 4000
    c.readTimeout = 30000      // a feed server may gather its answer on the request; the first can be slow
    try {
        if (c.responseCode != 200) return null
        val o = JSONObject(c.inputStream.bufferedReader().readText())
        val wx = o.optJSONObject("weather")
        val weather = if (wx != null && wx.has("temp") && !wx.isNull("temp"))
            "${wx.optInt("temp")}°" + str(wx, "sky").let { if (it.isEmpty()) "" else " ${it.lowercase()}" }
        else ""
        val top = o.optJSONArray("items")?.optJSONObject(0)
        return FeedLine(
            weather = weather,
            kicker = top?.let { str(it, "kicker") }.orEmpty(),
            headline = top?.let { str(it, "headline") }.orEmpty(),
            tone = top?.let { str(it, "tone") }.orEmpty(),
        )
    } finally {
        c.disconnect()
    }
}

/** A string field; "" when missing or JSON null (optString would say "null"). */
private fun str(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key)

@Volatile private var tls: Pair<String, SSLSocketFactory>? = null

/** A socket factory that trusts the certificates in [pem] and nothing else. Built once per PEM. */
private fun trusting(pem: String): SSLSocketFactory {
    tls?.takeIf { it.first == pem }?.let { return it.second }
    val certs = CertificateFactory.getInstance("X.509").generateCertificates(pem.byteInputStream())
    val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
        certs.forEachIndexed { i, c -> setCertificateEntry("ca$i", c) }
    }
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
    val f = SSLContext.getInstance("TLS").apply { init(null, tmf.trustManagers, null) }.socketFactory
    tls = pem to f
    return f
}
