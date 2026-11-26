package com.example.util.logcat

import com.example.domain.model.ErrorCategory
import org.junit.Assert.*
import org.junit.Test

class LogcatCrashParserTest {

    @Test
    fun parseFatalExceptionBlock_extractsExceptionTypeMessageStackTraceAndCausedByChain() {
        val logLines = listOf(
            "11-26 10:15:30.100 1000 1000 I ActivityTaskManager: START u0 {act=android.intent.action.MAIN}",
            "11-26 10:15:30.120 1000 1000 I WindowManager: Screen orientation updated",
            "11-26 10:15:32.456 2345 2345 E AndroidRuntime: FATAL EXCEPTION: main",
            "11-26 10:15:32.456 2345 2345 E AndroidRuntime: Process: com.target.demoapp, PID: 2345",
            "11-26 10:15:32.457 2345 2345 E AndroidRuntime: java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.String com.target.User.getName()' on a null object reference",
            "11-26 10:15:32.458 2345 2345 E AndroidRuntime: \tat com.target.demoapp.MainActivity.onCreate(MainActivity.kt:42)",
            "11-26 10:15:32.459 2345 2345 E AndroidRuntime: \tat android.app.Activity.performCreate(Activity.java:8000)",
            "11-26 10:15:32.460 2345 2345 E AndroidRuntime: Caused by: java.lang.IllegalStateException: User database is not initialized",
            "11-26 10:15:32.461 2345 2345 E AndroidRuntime: \tat com.target.demoapp.data.UserDao.loadUser(UserDao.kt:18)",
            "11-26 10:15:32.462 2345 2345 E AndroidRuntime: Caused by: java.io.IOException: Storage connection closed",
            "11-26 10:15:32.463 2345 2345 E AndroidRuntime: \tat com.target.demoapp.data.DatabaseDriver.connect(DatabaseDriver.kt:8)",
            "11-26 10:15:33.001 1000 1000 I ActivityManager: Process com.target.demoapp (pid 2345) has died"
        )

        val monitored = setOf("com.target.demoapp")
        val crashes = LogcatCrashParser.parseLines(logLines, monitored)

        assertEquals(1, crashes.size)
        val crash = crashes.first()

        assertEquals("com.target.demoapp", crash.packageName)
        assertEquals("java.lang.NullPointerException", crash.exceptionType)
        assertEquals(
            "Attempt to invoke virtual method 'java.lang.String com.target.User.getName()' on a null object reference",
            crash.message
        )
        assertEquals(ErrorCategory.NULL_POINTER_EXCEPTION, crash.category)

        assertTrue("Stack trace should contain MainActivity", crash.stackTrace.contains("MainActivity.onCreate"))
        assertTrue("Stack trace should contain UserDao", crash.stackTrace.contains("UserDao.loadUser"))

        assertEquals(2, crash.causedByChain.size)
        assertEquals("java.lang.IllegalStateException: User database is not initialized", crash.causedByChain[0])
        assertEquals("java.io.IOException: Storage connection closed", crash.causedByChain[1])

        assertTrue("Surrounding context should capture earlier lines", crash.fullContext.contains("ActivityTaskManager"))
        assertTrue("Surrounding context should capture later lines", crash.fullContext.contains("has died"))
    }

    @Test
    fun ignoreCrashesFromUnmonitoredPackages() {
        val logLines = listOf(
            "11-26 10:15:32.456 2345 2345 E AndroidRuntime: FATAL EXCEPTION: main",
            "11-26 10:15:32.456 2345 2345 E AndroidRuntime: Process: com.other.unmonitored, PID: 2345",
            "11-26 10:15:32.457 2345 2345 E AndroidRuntime: java.lang.RuntimeException: Unchecked crash",
            "11-26 10:15:32.458 2345 2345 E AndroidRuntime: \tat com.other.unmonitored.App.run(App.kt:10)"
        )

        val monitored = setOf("com.target.demoapp")
        val crashes = LogcatCrashParser.parseLines(logLines, monitored)

        assertTrue("Crashes from unmonitored packages must be ignored", crashes.isEmpty())
    }

    @Test
    fun parseFatalSignalNativeCrash() {
        val logLines = listOf(
            "11-26 10:16:01.000 3000 3000 I ActivityManager: Starting native task",
            "11-26 10:16:02.123 3000 3000 F libc    : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 3000 (com.native.game), pid 3000 (com.native.game)",
            "11-26 10:16:02.124 3000 3000 F DEBUG   : *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
            "11-26 10:16:02.125 3000 3000 F DEBUG   : backtrace:",
            "11-26 10:16:02.126 3000 3000 F DEBUG   :       #00 pc 0000000000052340  /apex/com.android.runtime/lib64/bionic/libc.so (abort+168)",
            "11-26 10:16:02.127 3000 3000 F DEBUG   :       #01 pc 0000000000012345  /data/app/libnative.so (crash_handler+24)"
        )

        val monitored = setOf("com.native.game")
        val crashes = LogcatCrashParser.parseLines(logLines, monitored)

        assertEquals(1, crashes.size)
        val crash = crashes.first()

        assertEquals("com.native.game", crash.packageName)
        assertTrue(crash.exceptionType.contains("SIGSEGV"))
        assertEquals(ErrorCategory.NATIVE_CRASH, crash.category)
        assertTrue(crash.stackTrace.contains("crash_handler"))
    }

    @Test
    fun parseAnrEvent() {
        val logLines = listOf(
            "11-26 10:17:10.500 1000 1050 E ActivityManager: ANR in com.slow.responsive.app (com.slow.responsive.app/.MainActivity)",
            "11-26 10:17:10.501 1000 1050 E ActivityManager: PID: 4000",
            "11-26 10:17:10.502 1000 1050 E ActivityManager: Reason: Input dispatching timed out (Waiting because no focused window)",
            "11-26 10:17:10.503 1000 1050 E ActivityManager: Load: 1.2 / 0.8 / 0.5"
        )

        val monitored = setOf("com.slow.responsive.app")
        val crashes = LogcatCrashParser.parseLines(logLines, monitored)

        assertEquals(1, crashes.size)
        val crash = crashes.first()

        assertEquals("com.slow.responsive.app", crash.packageName)
        assertEquals(ErrorCategory.ANR, crash.category)
        assertTrue(crash.message.contains("Input dispatching timed out"))
    }
}
