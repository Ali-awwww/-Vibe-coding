# Screen Inventory & Implementation Tasks

> Scope: Android app navigation declared in `app/src/main/java/com/example/presentation/navigation/Screen.kt` and registered in `AppNavigation.kt`.
>
> Inventory source: static inspection of the current `main` branch. This is a route inventory, not proof that every route is reachable, complete, or working at runtime.
>
> Status key: [ ] Not audited · [x] Audited and verified. Do not mark a task complete from source inspection alone; verify behavior and tests.

## Navigation screen inventory

| # | Route / destination | UI implementation | Parameters | Audit status |
|---:|---|---|---|---|
| 1 | `Login` | `presentation/ui/screens/LoginScreen.kt` | — | [ ] |
| 2 | `Dashboard` | `presentation/ui/screens/DashboardScreen.kt` | — | [ ] |
| 3 | `Projects` | `presentation/ui/screens/ProjectsScreen.kt` | — | [ ] |
| 4 | `Applications` | `presentation/ui/screens/ProjectsScreen.kt` | `projectId` | [ ] Same UI destination as Projects; verify route intent |
| 5 | `Crashes` | `presentation/ui/screens/CrashesScreen.kt` | Optional project, app, exception, version, severity, OS and title filters | [ ] |
| 6 | `CrashDetail` | `presentation/ui/screens/CrashDetailScreen.kt` | `crashId` | [ ] |
| 7 | `FixAssistant` | `presentation/ui/screens/FixAssistantScreen.kt` | `crashId` | [ ] |
| 8 | `FixHistory` | `presentation/ui/screens/FixHistoryScreen.kt` | Optional `projectId` | [ ] |
| 9 | `Integrations` | `presentation/ui/screens/IntegrationsScreen.kt` | `projectId` | [ ] |
| 10 | `Issues` | `presentation/ui/screens/IssuesScreen.kt` | Optional `projectId` | [ ] |
| 11 | `Analytics` | `presentation/ui/screens/AnalyticsScreen.kt` | — | [ ] |
| 12 | `LiveTesting` | `presentation/ui/screens/LiveTestingScreen.kt` | — | [ ] Agent transport is not implemented; UI must state unavailable |
| 13 | `LiveSession` | `presentation/ui/screens/LiveSessionScreen.kt` | `sessionId` | [ ] Must not display fabricated live events |
| 14 | `SetupGuide` | `presentation/ui/screens/SetupGuideScreen.kt` | `projectId`, `applicationId` | [ ] |
| 15 | `GitHubConnect` | `presentation/ui/screens/GitHubConnectScreen.kt` | `projectId` | [ ] |
| 16 | `DebugSymbols` | `presentation/ui/screens/DebugSymbolsScreen.kt` | `projectId` | [ ] Verify real symbol upload / lookup / symbolication |
| 17 | `AIModels` | `presentation/ui/screens/AIModelsScreen.kt` | — | [ ] Verify provider setup, connectivity and truthful model status |
| 18 | `VibeStudio` | `presentation/ui/screens/VibeStudioScreen.kt` | — | [ ] |
| 19 | `DevDebugCenter` | `presentation/ui/screens/DevDebugCenterScreen.kt` | Optional `workspaceId` | [ ] |
| 20 | `Settings` | `presentation/ui/screens/SettingsScreen.kt` | — | [ ] |
| 21 | `ModelTeams` | `presentation/ui/screens/ModelTeamsScreen.kt` | — | [ ] |
| 22 | `CreateTeam` | `presentation/ui/screens/ModelTeamsScreen.kt` | — | [ ] Route starts create flow then reuses ModelTeams UI |
| 23 | `TeamDetail` | `presentation/ui/screens/TeamDetailScreen.kt` | `teamId` | [ ] |
| 24 | `TeamUsage` | `presentation/ui/screens/TeamUsageDashboardScreen.kt` | `teamId` | [ ] |
| 25 | `TeamChat` | `presentation/ui/screens/TeamChatScreen.kt` | Optional `teamId`, `projectId` | [ ] |
| 26 | `TeamValidation` | `presentation/ui/screens/TeamValidationCenterScreen.kt` | — | [ ] |
| 27 | `ProductQualityCenter` | `presentation/ui/screens/quality/ProductQualityCenterScreen.kt` | — | [ ] |

**Count:** 27 navigation route declarations. Some routes intentionally reuse the same Composable implementation; route count is not the count of unique screen files.

## Reusable UI components (not independent navigation destinations)

These are embedded panels, tabs, dialogs or reusable UI pieces. Track them during the owning screen audit rather than counting them as separate app screens.

- Quality: `quality/ScreenQualityInspectorDialog.kt`
- Team chat: `teamchat/ChangeAgentDialog.kt`, `DirectTaskAssignmentDialog.kt`, `LiveTeamCollaborationPanel.kt`, `TeamActivityLogPanel.kt`, `TeamChatInputPanel.kt`, `TeamFileAttachmentDialog.kt`, `TeamInstructionDialog.kt`, `TeamResultReviewPanel.kt`
- Validation tabs: `validation/CrashSandboxTab.kt`, `ValidationMatrixTab.kt`, `ValidationReportTab.kt`, `VibeSandboxTab.kt`
- Vibe Studio components: `vibe/VibeAgentChatPanel.kt`, `VibeAppBuilderDialog.kt`, `VibeApprovalQueueDialog.kt`, `VibeCheckpointsDialog.kt`, `VibeDiffViewerDialog.kt`, `VibeGovernanceDialog.kt`, `VibeLivePreviewPanel.kt`, `VibeLocalAgentDialog.kt`, `VibeResourceLimitsDialog.kt`, `VibeReviewIssuesDialog.kt`, `VibeSearchDialog.kt`, `VibeSensitiveActionDialog.kt`, `VibeTaskReviewDialog.kt`, `VibeTerminalPanel.kt`, `VibeTestingPanel.kt`, `VibeVersionControlDialog.kt`

## Audit tasks — apply to every route

- [ ] Confirm the route is registered and reachable from the intended navigation entry points.
- [ ] Confirm loading, empty, success, offline, permission-denied and error states.
- [ ] Trace every visible action to its ViewModel/repository operation; remove inert buttons and fake success.
- [ ] Verify data is scoped to the selected user, project, application, team and workspace.
- [ ] Verify persistence and process-death / configuration-change behavior where relevant.
- [ ] Verify input validation, destructive-action confirmation and accessibility semantics.
- [ ] Verify Arabic and English strings, RTL layout and small-screen behavior.
- [ ] Add or update unit/UI tests for the screen's critical flows and negative paths.
- [ ] Run the relevant Gradle checks; record exact command and result.
- [ ] Manually exercise the route on an emulator/device before marking runtime verification complete.

## Cross-screen release blockers to investigate

- [ ] Audit navigation start destination and authentication/session restoration.
- [ ] Audit all routes for fallback user IDs, default workspace IDs and cross-account data leakage.
- [ ] Audit crash investigation and symbolication for evidence-backed output only.
- [ ] Audit AI provider state so CONNECTED means a real successful request.
- [ ] Implement a real Android Agent transport before enabling live testing; until then keep it explicitly unavailable.
- [ ] Audit Vibe Studio execution, terminal, file editing, approvals, checkpoints and Git operations for real side effects and safe failure handling.
- [ ] Audit team orchestration, collaboration, validation and usage metrics for real backend behavior rather than seeded/demo data.
- [ ] Audit secrets, API keys, signing material and repository hygiene. Never commit private signing keys or credentials.

## Working rules

1. Update this file as each screen is inspected and fixed.
2. Record concrete defects and evidence under the relevant screen; do not label a screen "complete" because it compiles.
3. Keep simulated/demo functionality clearly labeled and isolated from production actions.
4. A task is complete only after code review plus the relevant automated test; runtime claims require an emulator/device run.
