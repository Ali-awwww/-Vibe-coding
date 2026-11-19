package com.example.util

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

object PreviewHtmlSecurityPolicy {
    const val CONTENT_SECURITY_POLICY =
        "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; media-src data: blob:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; worker-src 'none'"

    fun baseUrlForWorkspace(workspaceId: String): String {
        val seed = workspaceId.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(StandardCharsets.UTF_8))
        val hostLabel = digest.take(16).joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        return "https://$hostLabel.preview.invalid/"
    }

    fun injectContentSecurityPolicy(html: String): String {
        val meta = """<meta http-equiv="Content-Security-Policy" content="$CONTENT_SECURITY_POLICY">"""
        val headMatch = Regex("(?i)<head(?:\\s[^>]*)?>").find(html)
        if (headMatch != null) {
            val insertionPoint = headMatch.range.last + 1
            return html.substring(0, insertionPoint) + "\n$meta" + html.substring(insertionPoint)
        }
        val htmlMatch = Regex("(?i)<html(?:\\s[^>]*)?>").find(html)
        if (htmlMatch != null) {
            val insertionPoint = htmlMatch.range.last + 1
            return html.substring(0, insertionPoint) + "\n<head>$meta</head>" + html.substring(insertionPoint)
        }
        return "<!doctype html><html><head>$meta</head><body>$html</body></html>"
    }

    fun shouldBlockNavigation(url: String): Boolean = !url.equals("about:blank", ignoreCase = true)

    fun shouldBlockResourceRequest(url: String): Boolean {
        val scheme = runCatching { URI(url).scheme?.lowercase(Locale.ROOT) }.getOrNull()
        return scheme !in setOf("data", "blob", "about")
    }
}
