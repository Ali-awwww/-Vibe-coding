package com.example.di

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.room.Room
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.remote.*
import com.example.data.repository.*
import com.example.domain.repository.*
import com.example.presentation.viewmodel.AuthNetworkSnapshot
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.MessageDigest

object DependencyProvider {
    private var authRepository: AuthRepository? = null
    private var crashRepository: CrashRepository? = null
    private var liveTestingRepository: LiveTestingRepository? = null
    private var sdkIntegrationRepository: SdkIntegrationRepository? = null
    private var githubRepository: GitHubRepository? = null
    private var symbolicationRepository: SymbolicationRepository? = null
    private var aiModelRepository: AIModelRepository? = null
    private var modelTeamRepository: AIModelTeamRepository? = null
    private var teamOrchestrator: com.example.domain.agent.TeamOrchestrator? = null
    private var vibeStudioRepository: VibeStudioRepository? = null
    private var database: AppDatabase? = null
    private var firestore: FirebaseFirestore? = null
    private var firebaseAuth: FirebaseAuth? = null

    private fun getFirestore(context: Context): FirebaseFirestore? {
        return firestore ?: synchronized(this) {
            try {
                if (!com.example.util.isFirebaseConfigured(context)) {
                    android.util.Log.i("DependencyProvider", "Firebase unprovisioned: utilizing local cache")
                    return@synchronized null
                }
                val dbId = try {
                    context.getString(R.string.firestore_database_id)
                } catch (_: Exception) {
                    null
                }
                if (dbId.isNullOrBlank()) {
                    android.util.Log.w("DependencyProvider", "firestore_database_id missing/blank: running in local mode")
                    return@synchronized null
                }
                val instance = FirebaseFirestore.getInstance(dbId)
                firestore = instance
                instance
            } catch (e: Throwable) {
                android.util.Log.w("DependencyProvider", "FirebaseFirestore unavailable: running in local mode", e)
                null
            }
        }
    }

    private fun getFirebaseAuth(context: Context? = null): FirebaseAuth? {
        return firebaseAuth ?: synchronized(this) {
            try {
                if (context != null && !com.example.util.isFirebaseConfigured(context)) {
                    return@synchronized null
                }
                val instance = FirebaseAuth.getInstance()
                firebaseAuth = instance
                instance
            } catch (e: Throwable) {
                android.util.Log.w("DependencyProvider", "FirebaseAuth unavailable: running in local mode", e)
                null
            }
        }
    }

    fun provideFirestore(context: Context): FirebaseFirestore? = getFirestore(context)
    fun provideFirebaseAuth(context: Context? = null): FirebaseAuth? = getFirebaseAuth(context)

    private val githubRetrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val sentryRetrofit = Retrofit.Builder()
        .baseUrl("https://sentry.io/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val bugsnagRetrofit = Retrofit.Builder()
        .baseUrl("https://api.bugsnag.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private const val FIREBASE_AUTH_ENDPOINT_HOST = "firebaseauth.googleapis.com"

    private val openRouterRetrofit = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/api/v1/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val huggingFaceRetrofit = Retrofit.Builder()
        .baseUrl("https://huggingface.co/api/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val sentryApi = sentryRetrofit.create(SentryApi::class.java)
    private val bugsnagApi = bugsnagRetrofit.create(BugsnagApi::class.java)
    private val githubApi = githubRetrofit.create(GitHubApi::class.java)
    private val openRouterApi = openRouterRetrofit.create(OpenRouterApi::class.java)
    private val huggingFaceApi = huggingFaceRetrofit.create(HuggingFaceApi::class.java)

    private fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "crash_intelligence_db"
            ).fallbackToDestructiveMigration().build()
            database = instance
            instance
        }
    }

    fun provideDatabase(context: Context): AppDatabase = getDatabase(context)

    fun provideAuthRepository(context: Context): AuthRepository {
        val appContext = context.applicationContext
        return authRepository ?: synchronized(this) {
            authRepository ?: FirebaseAuthRepository(
                context = appContext,
                firestoreProvider = { getFirestore(appContext) },
                firebaseAuthProvider = { getFirebaseAuth(appContext) }
            ).also { authRepository = it }
        }
    }

    fun inspectAuthNetwork(context: Context): AuthNetworkSnapshot {
        val appContext = context.applicationContext
        val packageName = appContext.packageName
        val signingSha1 = resolveSigningSha1(appContext)
        val playServicesStatus = resolvePlayServicesStatus(appContext)
        return runCatching {
            val cm = appContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = if (network != null) cm.getNetworkCapabilities(network) else null
            if (caps == null) {
                AuthNetworkSnapshot(
                    endpointHost = FIREBASE_AUTH_ENDPOINT_HOST,
                    vpnActive = false,
                    networkValidated = false,
                    transportSummary = "No active network",
                    appPackageName = packageName,
                    signingSha1 = signingSha1,
                    playServicesStatus = playServicesStatus
                )
            } else {
                val hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val transports = buildList {
                    if (hasVpn) add("VPN")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Cellular")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
                }
                AuthNetworkSnapshot(
                    endpointHost = FIREBASE_AUTH_ENDPOINT_HOST,
                    vpnActive = hasVpn,
                    networkValidated = validated,
                    transportSummary = transports.ifEmpty { listOf("Other") }.joinToString(" + "),
                    appPackageName = packageName,
                    signingSha1 = signingSha1,
                    playServicesStatus = playServicesStatus
                )
            }
        }.getOrElse {
            AuthNetworkSnapshot(
                endpointHost = FIREBASE_AUTH_ENDPOINT_HOST,
                appPackageName = packageName,
                signingSha1 = signingSha1,
                playServicesStatus = playServicesStatus
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveSigningSha1(context: Context): String? {
        return runCatching {
            val pm = context.packageManager
            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = info.signingInfo
                val signatures = if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
                signatures?.firstOrNull()?.toByteArray()
            } else {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                info.signatures?.firstOrNull()?.toByteArray()
            }
            certBytes?.let { bytes ->
                MessageDigest.getInstance("SHA-1")
                    .digest(bytes)
                    .joinToString(":") { byte -> "%02X".format(byte) }
            }
        }.getOrNull()
    }

    private fun resolvePlayServicesStatus(context: Context): String? {
        return runCatching {
            when (val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)) {
                ConnectionResult.SUCCESS -> "AVAILABLE"
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "UPDATE_REQUIRED"
                ConnectionResult.SERVICE_UPDATING -> "UPDATING"
                ConnectionResult.SERVICE_MISSING -> "MISSING"
                ConnectionResult.SERVICE_DISABLED -> "DISABLED"
                ConnectionResult.SERVICE_INVALID -> "INVALID"
                else -> "CODE_$status"
            }
        }.getOrNull()
    }

    fun provideCrashRepository(context: Context): CrashRepository {
        val appContext = context.applicationContext
        val db = getDatabase(appContext)
        return crashRepository ?: CrashRepositoryImpl(
            firestore = getFirestore(appContext),
            crashDao = db.crashDao(),
            sentryApi = sentryApi,
            bugsnagApi = bugsnagApi,
            githubRepository = provideGitHubRepository(appContext),
            symbolicationRepository = provideSymbolicationRepository(appContext),
            aiModelRepository = provideAIModelRepository(appContext),
            auth = getFirebaseAuth(appContext),
            authRepository = provideAuthRepository(appContext)
        ).also { crashRepository = it }
    }

    fun provideAIModelRepository(context: Context): AIModelRepository {
        val appContext = context.applicationContext
        return aiModelRepository ?: AIModelRepositoryImpl(
            aiProviderDao = getDatabase(appContext).aiProviderDao(),
            openRouterApi = openRouterApi,
            huggingFaceApi = huggingFaceApi,
            enablePlatformFirebaseAi = com.example.util.isFirebaseConfigured(appContext)
        ).also { aiModelRepository = it }
    }

    fun provideAIModelTeamRepository(context: Context): AIModelTeamRepository {
        return modelTeamRepository ?: AIModelTeamRepositoryImpl(
            context = context,
            authRepository = provideAuthRepository(context),
            firestore = getFirestore(context),
            firebaseAuth = getFirebaseAuth(context),
            aiProviderDaoOverride = getDatabase(context).aiProviderDao()
        ).also { modelTeamRepository = it }
    }

    private var teamGovernanceEngine: com.example.domain.governance.TeamGovernanceEngine? = null

    fun provideTeamGovernanceEngine(context: Context): com.example.domain.governance.TeamGovernanceEngine {
        return teamGovernanceEngine ?: com.example.data.governance.TeamGovernanceEngineImpl(
            provideAIModelTeamRepository(context)
        ).also { teamGovernanceEngine = it }
    }

    fun provideTeamOrchestrator(context: Context): com.example.domain.agent.TeamOrchestrator {
        val aiRepo = provideAIModelRepository(context)
        val teamRepo = provideAIModelTeamRepository(context)
        val govEngine = provideTeamGovernanceEngine(context)
        return teamOrchestrator ?: com.example.data.agent.TeamOrchestratorImpl(
            aiModelRepository = aiRepo,
            teamRepository = teamRepo,
            vibeRepository = provideVibeStudioRepository(context),
            orchestratorProvider = { provideVibeAgentOrchestrator(context) },
            taskPlanner = com.example.data.agent.orchestration.TaskPlannerImpl(aiRepo),
            sharedContextStore = com.example.data.agent.orchestration.SharedContextStoreImpl(),
            resultEvaluator = com.example.data.agent.orchestration.ResultEvaluatorImpl(aiRepo),
            responseComposer = com.example.data.agent.orchestration.ResponseComposerImpl(aiRepo),
            crashRepository = provideCrashRepository(context),
            crashDao = getDatabase(context).crashDao(),
            governanceEngine = govEngine
        ).also { teamOrchestrator = it }
    }

    fun provideLiveTestingRepository(context: Context): LiveTestingRepository {
        return liveTestingRepository ?: LiveTestingRepositoryImpl(
            getDatabase(context).crashDao()
        ).also { liveTestingRepository = it }
    }

    fun provideSdkIntegrationRepository(context: Context? = null): SdkIntegrationRepository {
        val dao = context?.let { getDatabase(it).crashDao() } ?: database?.crashDao()
        return sdkIntegrationRepository ?: SdkIntegrationRepositoryImpl(dao).also { sdkIntegrationRepository = it }
    }

    fun provideSetupGuideProgressStore(context: Context): SetupGuideProgressStore =
        com.example.data.local.SharedPreferencesSetupGuideProgressStore(context)

    fun provideGitHubRepository(context: Context): GitHubRepository {
        return githubRepository ?: GitHubRepositoryImpl(
            getDatabase(context).crashDao(),
            githubApi
        ).also { githubRepository = it }
    }

    fun provideSymbolicationRepository(context: Context): SymbolicationRepository {
        return symbolicationRepository ?: SymbolicationRepositoryImpl(
            getDatabase(context).crashDao()
        ).also { symbolicationRepository = it }
    }

    fun provideFixAssistantViewModel(context: Context): com.example.presentation.viewmodel.FixAssistantViewModel {
        return com.example.presentation.viewmodel.FixAssistantViewModel(provideCrashRepository(context))
    }

    fun provideFixHistoryViewModel(context: Context): com.example.presentation.viewmodel.FixHistoryViewModel {
        return com.example.presentation.viewmodel.FixHistoryViewModel(provideCrashRepository(context))
    }

    fun provideAIModelsViewModel(context: Context): com.example.presentation.viewmodel.AIModelsViewModel {
        return com.example.presentation.viewmodel.AIModelsViewModel(
            provideAIModelRepository(context),
            provideAuthRepository(context)
        )
    }

    fun provideVibeStudioViewModel(context: Context): com.example.presentation.viewmodel.VibeStudioViewModel {
        return com.example.presentation.viewmodel.VibeStudioViewModel(
            provideVibeStudioRepository(context),
            provideAIModelTeamRepository(context),
            provideAuthRepository(context)
        )
    }

    fun provideModelTeamsViewModel(context: Context): com.example.presentation.viewmodel.ModelTeamsViewModel {
        return com.example.presentation.viewmodel.ModelTeamsViewModel(
            provideAIModelTeamRepository(context),
            provideAIModelRepository(context),
            provideAuthRepository(context),
            isCloudConfigured = com.example.util.isFirebaseConfigured(context)
        )
    }

    fun provideTeamDetailViewModel(context: Context, teamId: String): com.example.presentation.viewmodel.TeamDetailViewModel {
        return com.example.presentation.viewmodel.TeamDetailViewModel(
            teamId,
            provideAIModelTeamRepository(context),
            provideTeamOrchestrator(context),
            provideAuthRepository(context),
            provideAIModelRepository(context)
        )
    }

    fun provideTeamChatViewModel(context: Context, teamId: String? = null, projectId: String? = null): com.example.presentation.viewmodel.TeamChatViewModel {
        return com.example.presentation.viewmodel.TeamChatViewModel(
            teamId,
            projectId,
            provideAIModelTeamRepository(context),
            provideTeamOrchestrator(context),
            provideCrashRepository(context),
            provideAIModelRepository(context),
            provideAuthRepository(context),
            isCloudConfigured = com.example.util.isFirebaseConfigured(context)
        )
    }

    fun provideDevDebugCenterViewModel(context: Context): com.example.presentation.viewmodel.DevDebugCenterViewModel {
        return com.example.presentation.viewmodel.DevDebugCenterViewModel(
            provideVibeStudioRepository(context),
            provideAuthRepository(context)
        )
    }

    fun provideVibeStudioRepository(context: Context): VibeStudioRepository {
        return vibeStudioRepository ?: synchronized(this) {
            val repository = VibeStudioRepositoryImpl(
                getDatabase(context).vibeStudioDao(),
                provideAIModelRepository(context),
                provideCrashRepository(context),
                provideGitHubRepository(context),
                getDatabase(context).crashDao(),
                governanceEngine = provideGovernanceEngine(context),
                orchestrator = provideVibeAgentOrchestrator(context),
                teamRepository = provideAIModelTeamRepository(context)
            )
            vibeStudioRepository = repository
            repository
        }
    }

    private var governanceEngine: com.example.domain.governance.GovernanceEngine? = null
    fun provideGovernanceEngine(context: Context): com.example.domain.governance.GovernanceEngine {
        return governanceEngine ?: com.example.data.governance.GovernanceEngineImpl(
            getDatabase(context).vibeStudioDao()
        ).also { governanceEngine = it }
    }

    private var agentToolAdapter: com.example.domain.agent.AgentToolAdapter? = null
    fun provideAgentToolAdapter(context: Context): com.example.domain.agent.AgentToolAdapter {
        return agentToolAdapter ?: com.example.data.agent.AgentToolAdapterImpl(
            vibeRepositoryProvider = { provideVibeStudioRepository(context) }
        ).also { agentToolAdapter = it }
    }

    private var agentOrchestrator: com.example.domain.agent.VibeAgentOrchestrator? = null
    fun provideVibeAgentOrchestrator(context: Context): com.example.domain.agent.VibeAgentOrchestrator {
        return agentOrchestrator ?: com.example.data.agent.VibeAgentOrchestratorImpl(
            provideAIModelRepository(context),
            vibeRepositoryProvider = { provideVibeStudioRepository(context) },
            provideAgentToolAdapter(context)
        ).also { agentOrchestrator = it }
    }

    private var teamValidationEngine: com.example.domain.validation.TeamValidationEngine? = null
    fun provideTeamValidationEngine(context: Context): com.example.domain.validation.TeamValidationEngine {
        return teamValidationEngine ?: com.example.data.validation.TeamValidationEngineImpl(
            aiModelRepository = provideAIModelRepository(context),
            teamRepository = provideAIModelTeamRepository(context)
        ).also { teamValidationEngine = it }
    }

    fun provideTeamValidationViewModel(context: Context): com.example.presentation.viewmodel.TeamValidationViewModel {
        return com.example.presentation.viewmodel.TeamValidationViewModel(
            provideTeamValidationEngine(context),
            provideAuthRepository(context)
        )
    }

    private var qualityGateEngine: com.example.data.quality.ProductQualityGateEngine? = null
    fun provideProductQualityGateEngine(): com.example.data.quality.ProductQualityGateEngine {
        return qualityGateEngine ?: com.example.data.quality.ProductQualityGateEngine().also {
            qualityGateEngine = it
        }
    }

    fun provideProductQualityCenterViewModel(): com.example.presentation.viewmodel.ProductQualityCenterViewModel {
        return com.example.presentation.viewmodel.ProductQualityCenterViewModel(
            provideProductQualityGateEngine()
        )
    }
}
