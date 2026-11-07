package com.example.util

import com.example.domain.model.Application
import com.example.domain.model.CrashReport
import com.example.domain.model.IntegrationType
import com.example.domain.model.Issue
import com.example.domain.model.IssueStatus
import com.example.domain.model.Project
import com.example.domain.model.Severity
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.flow.first

object SeedData {
    suspend fun seed(
        repository: CrashRepository,
        symbolicationRepository: com.example.domain.repository.SymbolicationRepository
    ) {
        val existingProjects = repository.getProjects().first()
        if (existingProjects.isNotEmpty()) return

        // ... existing project seeding ...

        val projectId = "proj_prod_01"
        val project = Project(
            id = projectId,
            name = "SuperApp Production",
            description = "Main production suite containing Android and iOS flagship apps.",
            ownerId = "usr_dev_master",
            dataRetentionDays = 90,
            piiScrubbingEnabled = true,
            createdAt = System.currentTimeMillis() - 45L * 86400000L
        )
        repository.addProject(project)

        // Seed project members with roles
        repository.addProjectMember(projectId, "sarah.qa@crashintelligence.local", com.example.domain.model.Role.DEVELOPER, "usr_dev_master")
        repository.addProjectMember(projectId, "alex.pm@crashintelligence.local", com.example.domain.model.Role.VIEWER, "usr_dev_master")
        repository.addProjectMember(projectId, "devops.admin@crashintelligence.local", com.example.domain.model.Role.ADMIN, "usr_dev_master")

        val androidAppId = "app_android_01"
        val androidApp = Application(
            id = androidAppId,
            projectId = projectId,
            name = "SuperApp Android",
            platform = "Android",
            packageName = "com.superapp.mobile",
            ownerId = "usr_dev_master",
            sdkSetupStatus = com.example.domain.model.SdkSetupStatus.CONFIGURED,
            agentConnectionStatus = com.example.domain.model.AgentConnectionStatus.ONLINE,
            createdAt = System.currentTimeMillis() - 45L * 86400000L
        )
        repository.addApplication(androidApp)

        val iosAppId = "app_ios_01"
        val iosApp = Application(
            id = iosAppId,
            projectId = projectId,
            name = "SuperApp iOS",
            platform = "iOS",
            packageName = "com.superapp.ios",
            ownerId = "usr_dev_master",
            sdkSetupStatus = com.example.domain.model.SdkSetupStatus.CONFIGURED,
            agentConnectionStatus = com.example.domain.model.AgentConnectionStatus.ONLINE,
            createdAt = System.currentTimeMillis() - 40L * 86400000L
        )
        repository.addApplication(iosApp)

        val betaAppId = "app_android_beta"
        val betaApp = Application(
            id = betaAppId,
            projectId = projectId,
            name = "SuperApp Beta",
            platform = "Android",
            packageName = "com.superapp.beta",
            ownerId = "usr_dev_master",
            sdkSetupStatus = com.example.domain.model.SdkSetupStatus.NOT_CONFIGURED,
            agentConnectionStatus = com.example.domain.model.AgentConnectionStatus.OFFLINE,
            createdAt = System.currentTimeMillis() - 10L * 86400000L
        )
        repository.addApplication(betaApp)

        val now = System.currentTimeMillis()
        val hour = 3600000L
        val day = 86400000L

        val crashTemplates = listOf(
            // Today Crashes
            CrashReport(
                id = "crash_001",
                applicationId = androidAppId,
                projectId = projectId,
                exceptionType = "NullPointerException",
                message = "Attempt to invoke virtual method 'int java.lang.String.length()' on a null object reference",
                stackTrace = "java.lang.NullPointerException: Attempt to invoke virtual method 'int java.lang.String.length()' on a null object reference\n\tat com.superapp.mobile.ui.profile.ProfileFragment.bindData(ProfileFragment.kt:74)\n\tat com.superapp.mobile.ui.profile.ProfileFragment.onViewCreated(ProfileFragment.kt:42)",
                deviceModel = "Pixel 7 Pro",
                osVersion = "Android 14",
                appVersion = "1.2.0",
                timestamp = now - 2 * hour,
                severity = Severity.CRITICAL,
                userId = "usr_alpha_101",
                source = IntegrationType.FIREBASE_CRASHLYTICS
            ),
            CrashReport(
                id = "crash_002",
                applicationId = androidAppId,
                projectId = projectId,
                exceptionType = "NullPointerException",
                message = "Null pointer in ProfileFragment user avatar loader",
                stackTrace = "java.lang.NullPointerException\n\tat com.superapp.mobile.ui.profile.ProfileFragment.bindData(ProfileFragment.kt:74)",
                deviceModel = "Samsung Galaxy S23",
                osVersion = "Android 14",
                appVersion = "1.2.0",
                timestamp = now - 5 * hour,
                severity = Severity.CRITICAL,
                userId = "usr_beta_102",
                source = IntegrationType.FIREBASE_CRASHLYTICS
            ),
            CrashReport(
                id = "crash_003",
                applicationId = iosAppId,
                projectId = projectId,
                exceptionType = "NSInvalidArgumentException",
                message = "'-[__NSDictionaryM length]: unrecognized selector sent to instance'",
                stackTrace = "0   CoreFoundation  __exceptionPreprocess\n1   libobjc.A.dylib objc_exception_throw\n2   CoreFoundation  -[NSObject(NSObject) doesNotRecognizeSelector:]\n3   SuperAppIOS     ProfileViewController.viewDidLoad()",
                deviceModel = "iPhone 15 Pro",
                osVersion = "iOS 17.4",
                appVersion = "1.2.0",
                timestamp = now - 8 * hour,
                severity = Severity.HIGH,
                userId = "usr_ios_201",
                source = IntegrationType.SENTRY
            ),

            // Yesterday Crashes
            CrashReport(
                id = "crash_004",
                applicationId = androidAppId,
                projectId = projectId,
                exceptionType = "OutOfMemoryError",
                message = "Failed to allocate a 32MB bitmap buffer with only 4MB heap remaining",
                stackTrace = "java.lang.OutOfMemoryError: Failed to allocate a 33554432 byte allocation with 4194304 free bytes\n\tat dalvik.system.VMRuntime.newNonMovableArray\n\tat android.graphics.BitmapFactory.nativeDecodeStream(BitmapFactory.java)\n\tat com.superapp.mobile.media.GalleryPicker.processImage(GalleryPicker.kt:118)",
                deviceModel = "Xiaomi 13",
                osVersion = "Android 13",
                appVersion = "1.2.0",
                timestamp = now - 1 * day - 3 * hour,
                severity = Severity.CRITICAL,
                userId = "usr_gamma_103",
                source = IntegrationType.BUGSNAG
            ),
            CrashReport(
                id = "crash_005",
                applicationId = androidAppId,
                projectId = projectId,
                exceptionType = "SocketTimeoutException",
                message = "Read timed out after 15000ms connecting to api.superapp.io/v2/feed",
                stackTrace = "java.net.SocketTimeoutException: Read timed out\n\tat okhttp3.internal.connection.RealCall.getResponseWithInterceptorChain(RealCall.kt:205)\n\tat com.superapp.mobile.data.FeedRepository.fetchLatest(FeedRepository.kt:48)",
                deviceModel = "Pixel 6a",
                osVersion = "Android 13",
                appVersion = "1.1.0",
                timestamp = now - 1 * day - 8 * hour,
                severity = Severity.MEDIUM,
                userId = "usr_delta_104",
                source = IntegrationType.FIREBASE_CRASHLYTICS
            ),
            CrashReport(
                id = "crash_006",
                applicationId = iosAppId,
                projectId = projectId,
                exceptionType = "EXC_BAD_ACCESS",
                message = "KERN_INVALID_ADDRESS at 0x0000000000000010 in FeedCacheManager",
                stackTrace = "0   SuperAppIOS     FeedCacheManager.store(data:)\n1   SuperAppIOS     FeedPresenter.didLoadFeed()",
                deviceModel = "iPhone 14",
                osVersion = "iOS 16.5",
                appVersion = "1.1.0",
                timestamp = now - 1 * day - 14 * hour,
                severity = Severity.HIGH,
                userId = "usr_ios_202",
                source = IntegrationType.SENTRY
            ),

            // 2 to 7 days ago
            CrashReport(
                id = "crash_007",
                applicationId = androidAppId,
                projectId = projectId,
                exceptionType = "NullPointerException",
                message = "Attempt to invoke virtual method 'int java.lang.String.length()' on a null object reference",
                stackTrace = "java.lang.NullPointerException\n\tat com.superapp.mobile.ui.profile.ProfileFragment.bindData(ProfileFragment.kt:74)",
                deviceModel = "Samsung Galaxy S22",
                osVersion = "Android 13",
                appVersion = "1.1.0",
                timestamp = now - 3 * day,
                severity = Severity.CRITICAL,
                userId = "usr_eps_105",
                source = IntegrationType.FIREBASE_CRASHLYTICS
            ),
            CrashReport(
                id = "crash_008",
                applicationId = androidAppId,
                projectId = projectId,
                exceptionType = "SQLiteConstraintException",
                message = "UNIQUE constraint failed: cached_articles.guid (code 2067 SQLITE_CONSTRAINT_UNIQUE)",
                stackTrace = "android.database.sqlite.SQLiteConstraintException: UNIQUE constraint failed: cached_articles.guid\n\tat android.database.sqlite.SQLiteConnection.nativeExecute(Native Method)\n\tat com.superapp.mobile.data.ArticleDao_Impl.insert(ArticleDao_Impl.java:54)",
                deviceModel = "Pixel 7",
                osVersion = "Android 14",
                appVersion = "1.1.0",
                timestamp = now - 4 * day,
                severity = Severity.MEDIUM,
                userId = "usr_zeta_106",
                source = IntegrationType.BUGSNAG
            ),
            CrashReport(
                id = "crash_009",
                applicationId = androidAppId,
                projectId = projectId,
                exceptionType = "ApplicationNotResponding",
                message = "ANR: Input dispatching timed out (Waiting to send key event to com.superapp.mobile)",
                stackTrace = "ANR: main thread blocked on synchronized lock in SyncManager.blockingCheck(SyncManager.kt:182)",
                deviceModel = "Xiaomi 13",
                osVersion = "Android 13",
                appVersion = "1.1.0",
                timestamp = now - 5 * day,
                severity = Severity.CRITICAL,
                userId = "usr_eta_107",
                source = IntegrationType.FIREBASE_CRASHLYTICS
            ),
            CrashReport(
                id = "crash_010",
                applicationId = iosAppId,
                projectId = projectId,
                exceptionType = "NSInvalidArgumentException",
                message = "'-[__NSDictionaryM length]: unrecognized selector sent to instance'",
                stackTrace = "0   CoreFoundation  __exceptionPreprocess\n1   SuperAppIOS     ProfileViewController.viewDidLoad()",
                deviceModel = "iPhone 15",
                osVersion = "iOS 17.4",
                appVersion = "1.1.0",
                timestamp = now - 6 * day,
                severity = Severity.HIGH,
                userId = "usr_ios_203",
                source = IntegrationType.SENTRY
            ),

            // 8 to 20 days ago (Version 1.0.0 and 1.1.0)
            CrashReport(
                id = "crash_011",
                applicationId = androidAppId,
                projectId = projectId,
                exceptionType = "IndexOutOfBoundsException",
                message = "Index 5 out of bounds for length 3",
                stackTrace = "java.lang.IndexOutOfBoundsException: Index 5 out of bounds for length 3\n\tat java.util.ArrayList.get(ArrayList.java:433)\n\tat com.superapp.mobile.ui.adapters.FeedAdapter.onBindViewHolder(FeedAdapter.kt:33)",
                deviceModel = "Pixel 5",
                osVersion = "Android 12",
                appVersion = "1.0.0",
                timestamp = now - 10 * day,
                severity = Severity.MEDIUM,
                userId = "usr_theta_108",
                source = IntegrationType.FIREBASE_CRASHLYTICS
            ),
            CrashReport(
                id = "crash_012",
                applicationId = androidAppId,
                projectId = projectId,
                exceptionType = "NullPointerException",
                message = "Attempt to invoke virtual method on null object reference in AuthManager",
                stackTrace = "java.lang.NullPointerException\n\tat com.superapp.mobile.auth.AuthManager.getToken(AuthManager.kt:59)",
                deviceModel = "Samsung Galaxy S21",
                osVersion = "Android 12",
                appVersion = "1.0.0",
                timestamp = now - 14 * day,
                severity = Severity.HIGH,
                userId = "usr_iota_109",
                source = IntegrationType.FIREBASE_CRASHLYTICS
            ),
            CrashReport(
                id = "crash_013",
                applicationId = androidAppId,
                projectId = projectId,
                exceptionType = "FatalException",
                message = "Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR) in libnative_filter.so",
                stackTrace = "backtrace:\n\t#00 pc 00021b44  /data/app/libnative_filter.so\n\t#01 pc 00018a12  /data/app/libnative_filter.so",
                deviceModel = "Samsung Galaxy S22",
                osVersion = "Android 13",
                appVersion = "1.0.0",
                timestamp = now - 18 * day,
                severity = Severity.CRITICAL,
                userId = "usr_kappa_110",
                source = IntegrationType.BUGSNAG
            ),
            CrashReport(
                id = "crash_014",
                applicationId = iosAppId,
                projectId = projectId,
                exceptionType = "EXC_BAD_ACCESS",
                message = "KERN_INVALID_ADDRESS at 0x0 in VideoPlayerEngine",
                stackTrace = "0   SuperAppIOS     VideoPlayerEngine.prepare()\n1   SuperAppIOS     PlayerViewController.viewWillAppear()",
                deviceModel = "iPhone 13",
                osVersion = "iOS 16.5",
                appVersion = "1.0.0",
                timestamp = now - 22 * day,
                severity = Severity.HIGH,
                userId = "usr_ios_204",
                source = IntegrationType.SENTRY
            ),
            CrashReport(
                id = "crash_015",
                applicationId = androidAppId,
                projectId = projectId,
                exceptionType = "SocketTimeoutException",
                message = "Failed to connect to gateway timeout after 15000ms",
                stackTrace = "java.net.SocketTimeoutException\n\tat okhttp3.internal.connection.RealCall.getResponseWithInterceptorChain(RealCall.kt:205)",
                deviceModel = "Pixel 6",
                osVersion = "Android 13",
                appVersion = "1.0.0",
                timestamp = now - 26 * day,
                severity = Severity.LOW,
                userId = "usr_lambda_111",
                source = IntegrationType.FIREBASE_CRASHLYTICS
            )
        )

        crashTemplates.forEach { repository.addCrash(it) }

        // Issues
        val issue1 = Issue(
            id = "issue_npe_profile",
            projectId = projectId,
            applicationId = androidAppId,
            title = "NullPointerException in ProfileFragment",
            subtitle = "ProfileFragment.kt:74",
            fingerprint = "npe_profile_fragment_74",
            severity = Severity.CRITICAL,
            status = IssueStatus.NEW,
            occurrenceCount = 3,
            affectedUsers = 3,
            firstSeen = now - 3 * day,
            lastOccurrence = now - 2 * hour,
            affectedVersions = "1.1.0, 1.2.0",
            source = IntegrationType.FIREBASE_CRASHLYTICS,
            mostAffectedOs = "Android 14",
            mostAffectedDevice = "Pixel 7 Pro",
            trend = 15.4f
        )
        repository.addIssue(issue1)

        val issue2 = Issue(
            id = "issue_ns_invalid_arg",
            projectId = projectId,
            applicationId = iosAppId,
            title = "NSInvalidArgumentException in ProfileViewController",
            subtitle = "ProfileViewController.viewDidLoad()",
            fingerprint = "ns_invalid_arg_profile_vc",
            severity = Severity.HIGH,
            status = IssueStatus.INVESTIGATING,
            occurrenceCount = 2,
            affectedUsers = 2,
            firstSeen = now - 6 * day,
            lastOccurrence = now - 8 * hour,
            affectedVersions = "1.1.0, 1.2.0",
            source = IntegrationType.SENTRY,
            mostAffectedOs = "iOS 17.4",
            mostAffectedDevice = "iPhone 15 Pro",
            trend = -5.0f
        )
        repository.addIssue(issue2)

        val issue3 = Issue(
            id = "issue_oom_gallery",
            projectId = projectId,
            applicationId = androidAppId,
            title = "OutOfMemoryError in GalleryPicker",
            subtitle = "GalleryPicker.kt:118",
            fingerprint = "oom_gallery_picker_118",
            severity = Severity.CRITICAL,
            status = IssueStatus.NEW,
            occurrenceCount = 1,
            affectedUsers = 1,
            firstSeen = now - 1 * day - 3 * hour,
            lastOccurrence = now - 1 * day - 3 * hour,
            affectedVersions = "1.2.0",
            source = IntegrationType.BUGSNAG,
            mostAffectedOs = "Android 13",
            mostAffectedDevice = "Xiaomi 13",
            trend = 100f
        )
        repository.addIssue(issue3)

        // Seed Debug Symbols
        symbolicationRepository.uploadDebugSymbol(
            com.example.domain.model.DebugSymbolFile(
                id = "symbol_001",
                applicationId = androidAppId,
                projectId = projectId,
                type = com.example.domain.model.SymbolFileType.ANDROID_R8,
                appVersion = "1.2.0",
                fileName = "mapping-1.2.0.txt",
                fileSize = 1024,
                status = com.example.domain.model.SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED
            )
        )

        // Seed Repository Mapping for GitHub Integration
        repository.addRepositoryMapping(
            com.example.domain.model.RepositoryMapping(
                applicationId = androidAppId,
                projectId = projectId,
                repoOwner = "crash-intelligence-ai",
                repoName = "superapp-android-demo",
                defaultBranch = "main"
            )
        )

        // Seed a sample GitHub Connection
        repository.saveGitHubConnection(
            com.example.domain.model.GitHubConnection(
                userId = "usr_dev_master",
                accessToken = "gho_sample_token_123456789",
                username = "master_dev",
                avatarUrl = "https://avatars.githubusercontent.com/u/9919?v=4"
            )
        )

        // Seed Sample Fix Suggestion
        val fixSuggestion = com.example.domain.model.FixSuggestion(
            id = "fix_npe_profile_01",
            crashId = "crash_001",
            originalCode = "fun bindData(user: User?) {\n    val len = user!!.displayName.length\n    avatarView.load(user.avatarUrl)\n}",
            suggestedCode = "fun bindData(user: User?) {\n    if (user == null) return\n    val len = user.displayName?.length ?: 0\n    user.avatarUrl?.let { avatarView.load(it) }\n}",
            explanation = "إضافة فحص الأمان لقيم null وتجنب استخدام المعامل الخطير (!!) مع توفير قيمة افتراضية لطول الاسم.",
            potentialSideEffects = "إذا كان المستخدم فارغاً لن يتم عرض الصورة الرمزية حتى تتوفر البيانات، وهو سلوك آمن.",
            filePath = "app/src/main/java/com/superapp/mobile/ui/profile/ProfileFragment.kt",
            branchName = "demo/fix-npe-profile-74",
            githubPrUrl = null,
            status = com.example.domain.model.FixStatus.PROPOSED,
            testResults = null // Sandbox example only; no PR or test run is represented.
        )

        // Sandbox-only verification fixtures: no real test session or outcome was executed.

        repository.addVerificationRecord(
            com.example.domain.model.CrashVerificationRecord(
                id = "verif_001",
                projectId = projectId,
                applicationId = androidAppId,
                crashId = "crash_001",
                issueId = "issue_npe_profile",
                fixSuggestionId = fixSuggestion.id,
                fixCommitSha = null,
                githubPrUrl = null,
                previousAppVersion = "1.2.0",
                newAppVersion = "1.2.1-patch",
                buildId = "demo-build-1042",
                testSessionId = null,
                scenarioName = "Demo fixture — no test executed",
                status = com.example.domain.model.VerificationStatus.INCONCLUSIVE,
                comparisonSummary = "بيانات Sandbox توضيحية فقط؛ لم يتم تشغيل اختبار أو إثبات إصلاح فعلي.",
                oldStackTrace = "java.lang.NullPointerException: Attempt to invoke virtual method 'int java.lang.String.length()' on a null object reference\n\tat com.superapp.mobile.ui.profile.ProfileFragment.bindData(ProfileFragment.kt:74)",
                newStackTrace = null,
                testScopeDetails = "بيانات Sandbox توضيحية فقط؛ لا توجد جلسة اختبار أو قياسات ذاكرة فعلية.",
                verifiedAt = now - 1 * hour,
                crashExceptionType = "NullPointerException",
                crashMessage = "Attempt to invoke virtual method 'int java.lang.String.length()' on a null object reference",
                environmentValid = false
            )
        )

        repository.addVerificationRecord(
            com.example.domain.model.CrashVerificationRecord(
                id = "verif_002",
                projectId = projectId,
                applicationId = androidAppId,
                crashId = "crash_004",
                issueId = "issue_oom_gallery",
                fixSuggestionId = null,
                fixCommitSha = null,
                githubPrUrl = null,
                previousAppVersion = "1.2.0",
                newAppVersion = "1.2.0-rc2",
                buildId = "demo-build-oom-check",
                testSessionId = null,
                scenarioName = "Demo fixture — no test executed",
                status = com.example.domain.model.VerificationStatus.INCONCLUSIVE,
                comparisonSummary = "بيانات Sandbox توضيحية فقط؛ لم يتم تشغيل اختبار أو إثبات إعادة إنتاج فعلية.",
                oldStackTrace = "java.lang.OutOfMemoryError: Failed to allocate a 33554432 byte allocation with 4194304 free bytes\n\tat dalvik.system.VMRuntime.newNonMovableArray\n\tat android.graphics.BitmapFactory.nativeDecodeStream(BitmapFactory.java)\n\tat com.superapp.mobile.media.GalleryPicker.processImage(GalleryPicker.kt:118)",
                newStackTrace = null,
                testScopeDetails = "بيانات Sandbox توضيحية فقط؛ لا توجد جلسة اختبار أو قياسات ذاكرة فعلية.",
                verifiedAt = now - 5 * hour,
                crashExceptionType = "OutOfMemoryError",
                crashMessage = "Failed to allocate a 32MB bitmap buffer with only 4MB heap remaining",
                environmentValid = false
            )
        )
    }
}
