package com.example.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.StateFlow
import com.example.di.DependencyProvider
import com.example.presentation.ui.screens.*
import com.example.presentation.viewmodel.*

@Composable
fun AppNavigation(
    passwordRecoveryLink: StateFlow<String?>,
    onPasswordRecoveryLinkConsumed: () -> Unit
) {
    val context = LocalContext.current
    val mainViewModel: MainViewModel = viewModel {
        MainViewModel(
            authRepository = DependencyProvider.provideAuthRepository(context),
            networkDiagnosticProvider = { DependencyProvider.inspectAuthNetwork(context) }
        )
    }
    
    val navController = rememberNavController()
    val currentUser by mainViewModel.currentUser.collectAsState()
    val recoveryLink by passwordRecoveryLink.collectAsState()

    LaunchedEffect(recoveryLink) {
        val link = recoveryLink ?: return@LaunchedEffect
        mainViewModel.acceptPasswordRecoveryLink(link)
        navController.navigate(Screen.Login) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
        onPasswordRecoveryLinkConsumed()
    }

    val startDestination = if (currentUser == null) Screen.Login else Screen.Dashboard

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<Screen.Login> {
            LoginScreen(
                viewModel = mainViewModel,
                onAuthSuccess = {
                    navController.navigate(Screen.Dashboard) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                }
            )
        }
        
        composable<Screen.Dashboard> {
            val dashboardViewModel: DashboardViewModel = viewModel {
                DashboardViewModel(
                    DependencyProvider.provideCrashRepository(context),
                    DependencyProvider.provideAuthRepository(context)
                )
            }
            DashboardScreen(
                navController = navController,
                onSignOut = {
                    mainViewModel.signOut()
                    navController.navigate(Screen.Login) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                viewModel = dashboardViewModel
            )
        }
        
        composable<Screen.Projects> {
            val projectsViewModel: ProjectsViewModel = viewModel {
                ProjectsViewModel(
                    DependencyProvider.provideCrashRepository(context),
                    DependencyProvider.provideAuthRepository(context)
                )
            }
            ProjectsScreen(navController = navController, viewModel = projectsViewModel)
        }

        composable<Screen.Applications> { backStackEntry ->
            val route: Screen.Applications = backStackEntry.toRoute()
            val projectsViewModel: ProjectsViewModel = viewModel {
                ProjectsViewModel(
                    DependencyProvider.provideCrashRepository(context),
                    DependencyProvider.provideAuthRepository(context)
                )
            }
            androidx.compose.runtime.LaunchedEffect(route.projectId) {
                if (route.projectId.isNotBlank()) {
                    projectsViewModel.selectProject(route.projectId)
                }
            }
            ProjectsScreen(
                navController = navController,
                viewModel = projectsViewModel,
                initialTab = 1
            )
        }
        
        composable<Screen.CrashDetail> { backStackEntry ->
            val route: Screen.CrashDetail = backStackEntry.toRoute()
            val crashDetailViewModel: CrashDetailViewModel = viewModel {
                CrashDetailViewModel(
                    DependencyProvider.provideCrashRepository(context),
                    DependencyProvider.provideSymbolicationRepository(context),
                    DependencyProvider.provideTeamOrchestrator(context),
                    DependencyProvider.provideAuthRepository(context)
                )
            }
            CrashDetailScreen(crashId = route.crashId, navController = navController, viewModel = crashDetailViewModel)
        }

        composable<Screen.FixAssistant> { backStackEntry ->
            val route: Screen.FixAssistant = backStackEntry.toRoute()
            val fixAssistantViewModel: FixAssistantViewModel = viewModel {
                DependencyProvider.provideFixAssistantViewModel(context)
            }
            val currentUserId = mainViewModel.currentUser.collectAsState().value?.id
            FixAssistantScreen(
                crashId = route.crashId,
                navController = navController,
                viewModel = fixAssistantViewModel,
                userId = currentUserId
            )
        }

        composable<Screen.FixHistory> { backStackEntry ->
            val route: Screen.FixHistory = backStackEntry.toRoute()
            val fixHistoryViewModel: FixHistoryViewModel = viewModel {
                DependencyProvider.provideFixHistoryViewModel(context)
            }
            FixHistoryScreen(projectId = route.projectId, navController = navController, viewModel = fixHistoryViewModel)
        }

        composable<Screen.Crashes> { backStackEntry ->
            val route: Screen.Crashes = backStackEntry.toRoute()
            CrashesScreen(
                projectId = route.projectId,
                applicationId = route.applicationId,
                exceptionType = route.exceptionType,
                appVersion = route.appVersion,
                severity = route.severity,
                osName = route.osName,
                filterTitle = route.filterTitle,
                navController = navController
            )
        }

        composable<Screen.Integrations> { backStackEntry ->
            val route: Screen.Integrations = backStackEntry.toRoute()
            IntegrationsScreen(projectId = route.projectId, navController = navController)
        }

        composable<Screen.Issues> { backStackEntry ->
            val issuesViewModel: IssuesViewModel = viewModel {
                IssuesViewModel(DependencyProvider.provideCrashRepository(context))
            }
            IssuesScreen(navController = navController, viewModel = issuesViewModel)
        }

        composable<Screen.Analytics> {
            val analyticsViewModel: AnalyticsViewModel = viewModel {
                AnalyticsViewModel(DependencyProvider.provideCrashRepository(context))
            }
            AnalyticsScreen(navController = navController, viewModel = analyticsViewModel)
        }

        composable<Screen.Settings> {
            SettingsScreen(
                viewModel = mainViewModel,
                onSignOut = {
                    mainViewModel.signOut()
                    navController.navigate(Screen.Login) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                navController = navController
            )
        }

        composable<Screen.LiveTesting> {
            val liveTestingViewModel: LiveTestingViewModel = viewModel {
                LiveTestingViewModel(
                    DependencyProvider.provideLiveTestingRepository(context),
                    DependencyProvider.provideCrashRepository(context)
                )
            }
            LiveTestingScreen(navController = navController, viewModel = liveTestingViewModel)
        }

        composable<Screen.LiveSession> { backStackEntry ->
            val route: Screen.LiveSession = backStackEntry.toRoute()
            val liveTestingViewModel: LiveTestingViewModel = viewModel {
                LiveTestingViewModel(
                    DependencyProvider.provideLiveTestingRepository(context),
                    DependencyProvider.provideCrashRepository(context)
                )
            }
            LiveSessionScreen(sessionId = route.sessionId, navController = navController, viewModel = liveTestingViewModel)
        }

        composable<Screen.SetupGuide> { backStackEntry ->
            val route: Screen.SetupGuide = backStackEntry.toRoute()
            val setupViewModel: SetupGuideViewModel = viewModel {
                SetupGuideViewModel(DependencyProvider.provideSdkIntegrationRepository(context))
            }
            SetupGuideScreen(
                projectId = route.projectId,
                applicationId = route.applicationId,
                navController = navController,
                viewModel = setupViewModel
            )
        }

        composable<Screen.GitHubConnect> { backStackEntry ->
            val route: Screen.GitHubConnect = backStackEntry.toRoute()
            val githubViewModel: GitHubViewModel = viewModel {
                GitHubViewModel(
                    DependencyProvider.provideGitHubRepository(context),
                    mainViewModel.currentUser.value?.id ?: ""
                )
            }
            GitHubConnectScreen(projectId = route.projectId, navController = navController, viewModel = githubViewModel)
        }

        composable<Screen.DebugSymbols> { backStackEntry ->
            val route: Screen.DebugSymbols = backStackEntry.toRoute()
            val debugSymbolsViewModel: DebugSymbolsViewModel = viewModel {
                DebugSymbolsViewModel(
                    DependencyProvider.provideSymbolicationRepository(context),
                    DependencyProvider.provideCrashRepository(context),
                    route.projectId
                )
            }
            DebugSymbolsScreen(projectId = route.projectId, navController = navController, viewModel = debugSymbolsViewModel)
        }

        composable<Screen.AIModels> {
            AIModelsScreen(navController = navController)
        }

        composable<Screen.VibeStudio> {
            val vibeViewModel: VibeStudioViewModel = viewModel {
                DependencyProvider.provideVibeStudioViewModel(context)
            }
            VibeStudioScreen(navController = navController, viewModel = vibeViewModel)
        }

        composable<Screen.DevDebugCenter> { backStackEntry ->
            val route: Screen.DevDebugCenter = backStackEntry.toRoute()
            val devDebugViewModel: DevDebugCenterViewModel = viewModel {
                DependencyProvider.provideDevDebugCenterViewModel(context)
            }
            DevDebugCenterScreen(
                workspaceId = route.workspaceId,
                navController = navController,
                viewModel = devDebugViewModel
            )
        }

        composable<Screen.ModelTeams> {
            ModelTeamsScreen(navController = navController)
        }

        composable<Screen.CreateTeam> {
            ModelTeamsScreen(navController = navController, initialCreateMode = true)
        }

        composable<Screen.TeamDetail> { backStackEntry ->
            val route: Screen.TeamDetail = backStackEntry.toRoute()
            TeamDetailScreen(navController = navController, teamId = route.teamId)
        }

        composable<Screen.TeamUsage> { backStackEntry ->
            val route: Screen.TeamUsage = backStackEntry.toRoute()
            TeamUsageDashboardScreen(navController = navController, teamId = route.teamId)
        }

        composable<Screen.TeamChat> { backStackEntry ->
            val route: Screen.TeamChat = backStackEntry.toRoute()
            TeamChatScreen(
                navController = navController,
                teamId = route.teamId,
                projectId = route.projectId
            )
        }

        composable<Screen.TeamValidation> {
            com.example.presentation.ui.screens.TeamValidationCenterScreen(navController = navController)
        }

        composable<Screen.ProductQualityCenter> {
            val qualityViewModel: com.example.presentation.viewmodel.ProductQualityCenterViewModel = viewModel {
                DependencyProvider.provideProductQualityCenterViewModel()
            }
            com.example.presentation.ui.screens.quality.ProductQualityCenterScreen(
                navController = navController,
                viewModel = qualityViewModel
            )
        }
    }
}

