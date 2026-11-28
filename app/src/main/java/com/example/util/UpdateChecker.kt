package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * فحص التحديث داخل التطبيق من واجهة معرض التطبيقات.
 *
 * - فحص صامت في الخلفية عند فتح التطبيق: مرة واحدة في اليوم كحد أقصى عبر الشبكة،
 *   ولا يوقف التشغيل أبداً (أي خطأ يعيد null بصمت).
 * - مقارنة رقمية للإصدار جزءاً بجزء (1.0.10 أكبر من 1.0.9)، مع تجاهل 'v' في البداية.
 * - المصدر: GET https://apps-showcase.pages.dev/api/latest والبحث عن العنصر
 *   الذي فيه repo == "-Vibe-coding"، وأخذ tag وapkUrl وbody منه.
 */
object UpdateChecker {

    /**
     * الإصدار الحالي للتطبيق.
     * يجب رفع هذا الرقم مع كل إصدار جديد (ومعه versionName في app/build.gradle.kts).
     */
    const val CURRENT_VERSION = "1.0.7"

    /** اسم المستودع كما يظهر في حقل repo ضمن واجهة أحدث الإصدارات. */
    const val REPO_SLUG = "-Vibe-coding"

    private const val LATEST_URL = "https://apps-showcase.pages.dev/api/latest"
    private const val FETCH_TIMEOUT_MS = 8000
    private const val PREFS_NAME = "app_update_check"
    private const val KEY_LAST_CHECK_DATE = "last_check_date"
    private const val KEY_DISMISSED_DATE = "dismissed_date"
    private const val KEY_DISMISSED_VERSION = "dismissed_version"
    private const val KEY_CACHED_VERSION = "cached_version"
    private const val KEY_CACHED_TAG = "cached_tag"
    private const val KEY_CACHED_APK_URL = "cached_apk_url"
    private const val KEY_CACHED_BODY = "cached_body"

    data class UpdateInfo(
        val version: String,
        val tag: String,
        val apkUrl: String,
        val body: String
    )

    /** تحويل "v1.0.10" إلى [1, 0, 10]؛ تُتجاهل 'v' في البداية والأجزاء غير الرقمية. */
    fun normalizeVersion(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .split(".")
            .mapNotNull { it.toIntOrNull() }

    /**
     * مقارنة رقمية جزءاً بجزء: true إذا كان [latest] أحدث من [current].
     * مثال: 1.0.10 أكبر من 1.0.9، ولا مقارنة نصية أبداً.
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = normalizeVersion(latest)
        val currentParts = normalizeVersion(current)
        val length = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until length) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    /** السطر الأول غير الفارغ من ملاحظات الإصدار. */
    fun firstNewsLine(body: String): String =
        body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** هل طُلب تجاهل هذا الإصدار اليوم؟ */
    fun isDismissedToday(context: Context, version: String): Boolean {
        val prefs = prefs(context)
        return prefs.getString(KEY_DISMISSED_VERSION, null) == version &&
            prefs.getString(KEY_DISMISSED_DATE, null) == today()
    }

    /** تجاهل هذا الإصدار حتى اليوم التالي فقط. */
    fun dismissUpdateForToday(context: Context, version: String) {
        prefs(context).edit()
            .putString(KEY_DISMISSED_VERSION, version)
            .putString(KEY_DISMISSED_DATE, today())
            .apply()
    }

    /** آخر تحديث محفوظ، إن كان أحدث من الإصدار الحالي ولم يُتجاهل اليوم. */
    fun getCachedUpdate(context: Context): UpdateInfo? {
        val prefs = prefs(context)
        val version = prefs.getString(KEY_CACHED_VERSION, null) ?: return null
        if (!isNewerVersion(version, CURRENT_VERSION)) return null
        if (isDismissedToday(context, version)) return null
        return UpdateInfo(
            version = version,
            tag = prefs.getString(KEY_CACHED_TAG, null).orEmpty(),
            apkUrl = prefs.getString(KEY_CACHED_APK_URL, null).orEmpty(),
            body = prefs.getString(KEY_CACHED_BODY, null).orEmpty()
        )
    }

    private fun saveCachedUpdate(context: Context, update: UpdateInfo) {
        prefs(context).edit()
            .putString(KEY_CACHED_VERSION, update.version)
            .putString(KEY_CACHED_TAG, update.tag)
            .putString(KEY_CACHED_APK_URL, update.apkUrl)
            .putString(KEY_CACHED_BODY, update.body)
            .apply()
    }

    private fun parseLatestUpdate(json: String): UpdateInfo? {
        val apps = JSONObject(json).optJSONArray("apps") ?: return null
        for (index in 0 until apps.length()) {
            val app = apps.optJSONObject(index) ?: continue
            if (app.optString("repo") != REPO_SLUG) continue
            val tag = app.optString("tag")
            val apkUrl = app.optString("apkUrl")
            if (tag.isBlank() || apkUrl.isBlank()) return null
            val version = tag.trim().removePrefix("v").removePrefix("V")
            if (!isNewerVersion(version, CURRENT_VERSION)) return null
            return UpdateInfo(
                version = version,
                tag = tag,
                apkUrl = apkUrl,
                body = app.optString("body")
            )
        }
        return null
    }

    /**
     * فحص الشبكة: يجلب أحدث الإصدارات ويعيد التحديث إن وُجد إصدار أحدث.
     * صامت تماماً: أي خطأ (بلا إنترنت، مهلة، رد غير صالح) يعيد null دون رمي استثناء.
     */
    suspend fun fetchLatestUpdate(context: Context): UpdateInfo? {
        return try {
            val json: String? = withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    connection = URL(LATEST_URL).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = FETCH_TIMEOUT_MS
                    connection.readTimeout = FETCH_TIMEOUT_MS
                    connection.connect()
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        null
                    } else {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    }
                } catch (e: Exception) {
                    null
                } finally {
                    connection?.disconnect()
                }
            }
            val jsonText = json ?: return null
            parseLatestUpdate(jsonText)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * فحص الإطلاق الصامت: مرة واحدة في اليوم كحد أقصى عبر الشبكة، ولا يوقف التشغيل أبداً.
     * في باقي مرات الفتح خلال اليوم يعيد التحديث المحفوظ (إن وُجد) لتظهر البطاقة عند كل فتح.
     */
    suspend fun checkUpdateForLaunch(context: Context): UpdateInfo? {
        val prefs = prefs(context)
        if (prefs.getString(KEY_LAST_CHECK_DATE, null) == today()) {
            return getCachedUpdate(context)
        }
        // تسجيل تاريخ الفحص قبل طلب الشبكة حتى لا يتكرر الطلب خلال اليوم حتى عند الفشل.
        prefs.edit().putString(KEY_LAST_CHECK_DATE, today()).apply()
        val latest = fetchLatestUpdate(context) ?: return getCachedUpdate(context)
        saveCachedUpdate(context, latest)
        return getCachedUpdate(context)
    }

    /** فتح رابط ملف APK لبدء التحديث. */
    fun openUpdate(context: Context, update: UpdateInfo) {
        if (update.apkUrl.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.apkUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
