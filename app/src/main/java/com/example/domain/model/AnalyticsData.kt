package com.example.domain.model

enum class TimePeriod(val days: Int) {
    TODAY(1),
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    LAST_90_DAYS(90),
    ALL_TIME(3650)
}

data class AnalyticsFilter(
    val projectId: String? = null,
    val applicationId: String? = null,
    val platform: String? = null, // "Android", "iOS", null = all
    val version: String? = null,
    val exceptionType: String? = null,
    val severity: Severity? = null,
    val timePeriod: TimePeriod = TimePeriod.LAST_30_DAYS
)

data class TimeSeriesPoint(
    val label: String,
    val count: Int,
    val timestamp: Long = 0L
)

data class CategoryCount(
    val name: String,
    val count: Int,
    val percentage: Float,
    val secondaryInfo: String? = null
)

data class PeriodComparison(
    val currentCount: Int,
    val previousCount: Int,
    val changePercentage: Float, // positive = increase in crashes (bad), negative = decrease (good)
    val labelCurrent: String,
    val labelPrevious: String
)

data class StabilityMetrics(
    val totalCrashes: Int,
    val affectedUsers: Int,
    val estimatedUsersWithTelemetry: Int,
    val crashFreeUsersPercentage: Float,
    val hasSessionTelemetry: Boolean,
    val crashFreeSessionsPercentage: Float?,
    val methodologyNote: String,
    val dataSourceNote: String
)

data class AnalyticsDashboardData(
    val totalCrashes: Int = 0,
    val affectedUsersCount: Int = 0,
    val dailyTimeSeries: List<TimeSeriesPoint> = emptyList(),
    val weeklyTimeSeries: List<TimeSeriesPoint> = emptyList(),
    val monthlyTimeSeries: List<TimeSeriesPoint> = emptyList(),
    val osDistribution: List<CategoryCount> = emptyList(),
    val exceptionDistribution: List<CategoryCount> = emptyList(),
    val versionDistribution: List<CategoryCount> = emptyList(),
    val deviceDistribution: List<CategoryCount> = emptyList(),
    val osVersionDistribution: List<CategoryCount> = emptyList(),
    val topFrequentIssues: List<CategoryCount> = emptyList(),
    val todayVsYesterday: PeriodComparison = PeriodComparison(0, 0, 0f, "Today", "Yesterday"),
    val weekVsPreviousWeek: PeriodComparison = PeriodComparison(0, 0, 0f, "This Week", "Last Week"),
    val monthVsPreviousMonth: PeriodComparison = PeriodComparison(0, 0, 0f, "This Month", "Last Month"),
    val stabilityMetrics: StabilityMetrics = StabilityMetrics(
        totalCrashes = 0,
        affectedUsers = 0,
        estimatedUsersWithTelemetry = 0,
        crashFreeUsersPercentage = 100f,
        hasSessionTelemetry = false,
        crashFreeSessionsPercentage = null,
        methodologyNote = "",
        dataSourceNote = ""
    )
)
