package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.domain.model.AnalyticsDashboardData
import com.example.domain.model.AnalyticsFilter
import com.example.domain.model.CrashReport
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private fun getReportsDir(context: Context): File {
        val dir = File(context.cacheDir, "reports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun exportToCsv(context: Context, crashes: List<CrashReport>): File {
        val dir = getReportsDir(context)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_report_$timestamp.csv")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        file.bufferedWriter().use { writer ->
            // CSV Header
            writer.write("ID,Timestamp,Date,Exception Type,Severity,App Version,OS Version,Device Model,User ID,Status,Message\n")
            crashes.forEach { crash ->
                val dateStr = dateFormat.format(Date(crash.timestamp))
                val escapedMsg = crash.message.replace("\"", "\"\"")
                val resolvedStr = if (crash.isResolved) "Resolved" else "Open"
                writer.write("\"${crash.id}\",${crash.timestamp},\"$dateStr\",\"${crash.exceptionType}\",\"${crash.severity.name}\",\"${crash.appVersion}\",\"${crash.osVersion}\",\"${crash.deviceModel}\",\"${crash.userId}\",\"$resolvedStr\",\"$escapedMsg\"\n")
            }
        }
        return file
    }

    fun exportToJson(context: Context, crashes: List<CrashReport>, analyticsData: AnalyticsDashboardData?): File {
        val dir = getReportsDir(context)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_report_$timestamp.json")

        val root = JSONObject()
        root.put("generatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
        root.put("totalCrashes", crashes.size)

        analyticsData?.let { stats ->
            val summary = JSONObject()
            summary.put("affectedUsers", stats.affectedUsersCount)
            summary.put("crashFreeUsersPercentage", stats.stabilityMetrics.crashFreeUsersPercentage)
            summary.put("stabilityNote", stats.stabilityMetrics.methodologyNote)
            root.put("analyticsSummary", summary)
        }

        val crashArray = JSONArray()
        crashes.forEach { crash ->
            val obj = JSONObject()
            obj.put("id", crash.id)
            obj.put("timestamp", crash.timestamp)
            obj.put("exceptionType", crash.exceptionType)
            obj.put("message", crash.message)
            obj.put("appVersion", crash.appVersion)
            obj.put("osVersion", crash.osVersion)
            obj.put("deviceModel", crash.deviceModel)
            obj.put("severity", crash.severity.name)
            obj.put("userId", crash.userId)
            obj.put("isResolved", crash.isResolved)
            crashArray.put(obj)
        }
        root.put("crashes", crashArray)

        file.bufferedWriter().use { writer ->
            writer.write(root.toString(2))
        }
        return file
    }

    fun exportToPdf(
        context: Context,
        crashes: List<CrashReport>,
        analyticsData: AnalyticsDashboardData,
        filter: AnalyticsFilter
    ): File {
        val dir = getReportsDir(context)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_report_$timestamp.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 portrait
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paintTitle = Paint().apply {
            color = Color.parseColor("#1A237E")
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val paintSub = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
            isAntiAlias = true
        }

        val paintHeader = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val paintBody = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }

        val paintBox = Paint().apply {
            color = Color.parseColor("#E8EAF6")
            style = Paint.Style.FILL
        }

        val paintBorder = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        var y = 40f

        // Document Header
        canvas.drawText("Crash Intelligence - Analytics & Stability Report", 40f, y, paintTitle)
        y += 20f
        val genDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        canvas.drawText("Generated on: $genDate | Period: ${filter.timePeriod.name}", 40f, y, paintSub)
        y += 25f

        // Summary Card
        canvas.drawRect(40f, y, 555f, y + 65f, paintBox)
        canvas.drawRect(40f, y, 555f, y + 65f, paintBorder)

        canvas.drawText("Total Crashes: ${crashes.size}", 55f, y + 25f, paintHeader)
        canvas.drawText("Affected Users: ${analyticsData.affectedUsersCount}", 220f, y + 25f, paintHeader)
        val stabilityStr = String.format(Locale.US, "%.1f%%", analyticsData.stabilityMetrics.crashFreeUsersPercentage)
        canvas.drawText("Crash-Free Users: $stabilityStr", 380f, y + 25f, paintHeader)

        canvas.drawText("Telemetry Status: Ingested error telemetry. Real-time active session pings require live SDK sync.", 55f, y + 50f, paintSub)
        y += 85f

        // Breakdown Summary Section
        canvas.drawText("Top Exception Types", 40f, y, paintHeader)
        y += 18f
        analyticsData.exceptionDistribution.take(4).forEach { item ->
            val pctStr = String.format(Locale.US, "%.1f%%", item.percentage)
            canvas.drawText("• ${item.name}: ${item.count} ($pctStr)", 50f, y, paintBody)
            y += 14f
        }
        y += 10f

        canvas.drawText("OS Breakdown: Android: ${analyticsData.osDistribution.find { it.name.contains("Android", true) }?.count ?: 0} | iOS: ${analyticsData.osDistribution.find { it.name.contains("iOS", true) }?.count ?: 0}", 40f, y, paintSub)
        y += 25f

        // Crash Table Header
        canvas.drawText("Crash Incident Log (Recent ${crashes.take(20).size})", 40f, y, paintHeader)
        y += 16f

        canvas.drawRect(40f, y - 12f, 555f, y + 4f, paintBox)
        canvas.drawText("Exception", 45f, y, paintHeader)
        canvas.drawText("Severity", 200f, y, paintHeader)
        canvas.drawText("Version", 280f, y, paintHeader)
        canvas.drawText("Device & OS", 360f, y, paintHeader)
        canvas.drawText("User", 490f, y, paintHeader)
        y += 16f

        val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        crashes.take(22).forEach { crash ->
            val exc = if (crash.exceptionType.length > 22) crash.exceptionType.take(20) + ".." else crash.exceptionType
            val dev = if (crash.deviceModel.length > 18) crash.deviceModel.take(16) + ".." else crash.deviceModel
            canvas.drawText(exc, 45f, y, paintBody)
            canvas.drawText(crash.severity.name, 200f, y, paintBody)
            canvas.drawText(crash.appVersion, 280f, y, paintBody)
            canvas.drawText("$dev (${crash.osVersion})", 360f, y, paintBody)
            canvas.drawText(crash.userId.take(8), 490f, y, paintBody)
            y += 15f
        }

        document.finishPage(page)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Crash Intelligence Export - ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share Crash Report")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
