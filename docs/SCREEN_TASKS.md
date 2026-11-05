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


## Product direction and screen-level target state

The product target is a dependable Android development and diagnostics workspace. The following are implementation targets, not claims that these capabilities already exist. Keep unavailable capabilities disabled or clearly labeled until their real backend/transport is implemented and verified.

### Priority 0 — core diagnostic integrity

| Screen | Required target behavior | Verification focus |
|---|---|---|
| `Crashes` | List only crash records received from a real configured source; preserve project/application filters and loading/empty/error states. | Source provenance, pagination, filters, project isolation, no seeded production records. |
| `CrashDetail` | Show the original crash metadata, full stack trace and evidence-backed investigation status. | Missing/partial report handling, correct crash-to-project association, no fabricated fields. |
| `FixAssistant` | Generate fixes from the selected crash and actual source context; distinguish evidence, inference and proposed changes. | Context selection, provider failure, source references, diff review, no claim that a fix was applied unless it was. |
| `FixHistory` | Show persisted fix attempts and their actual outcomes. | Persistence, project scoping, accurate applied/rejected/failed states. |
| `DebugSymbols` | Support real symbol artifact upload, storage, lookup and symbolication, or explicitly report which operation is not implemented. | Artifact validation, build/version matching, symbol resolution accuracy and failure states. |
| `DevDebugCenter` | Aggregate diagnostics for the selected workspace without inventing device, build or runtime evidence. | Workspace scoping, source timestamps, permission and unavailable states. |

### Priority 1 — AI provider reliability and live execution

| Screen | Required target behavior | Verification focus |
|---|---|---|
| `AIModels` | Configure supported providers, validate credentials safely and show CONNECTED only after a real successful request. | Success, invalid key, timeout, rate limit, empty response, provider outage; prevent test action crashes. |
| `LiveTesting` | Start and control tests only through a real Android Agent transport. Until transport exists, explain unavailability and do not create sessions. | Agent discovery/authentication, command delivery, timeout/disconnect, truthful session lifecycle. |
| `LiveSession` | Display events and results received from the actual Agent for the requested session. | Session ownership, event ordering, reconnect behavior, no synthetic live events. |
| `SetupGuide` | Guide the user through the real prerequisites for connecting an app/project and its diagnostic source. | Steps reflect implemented integrations; completion state is persisted and evidence-based. |
| `GitHubConnect` | Authenticate and connect the selected repository with least-privilege access. | OAuth/token handling, repository permissions, project mapping, disconnect/revocation. |

### Priority 2 — development workspace and collaboration

| Screen | Required target behavior | Verification focus |
|---|---|---|
| `VibeStudio` | Evolve into a real project workspace: inspect/edit files, review diffs, manage checkpoints and Git changes, and run supported tasks only in a controlled execution environment. | Real file side effects, workspace boundary, approval gates, rollback, command isolation and truthful tool status. |
| `Projects` / `Applications` | Provide clear project/application separation and reliable selection context for downstream screens. | Route intent, CRUD persistence, ownership, application-to-project mapping. |
| `ModelTeams` / `CreateTeam` | Create and manage actual agent teams; distinguish configured agents from active agents. | Create flow, persistence, duplicate handling and truthful availability. |
| `TeamDetail` | Show team configuration and membership for the selected team. | Team ownership, membership changes and empty/error states. |
| `TeamChat` | Persist messages and task assignments; show execution status only when backed by real agent events. | Delivery, retries, attachments, authorization and audit trail. |
| `TeamUsage` | Report measured usage/cost rather than estimates presented as actual billing. | Metric provenance, time ranges, missing data and aggregation. |
| `TeamValidation` | Run real validation jobs and preserve their reports; label any sandbox/demo mode explicitly. | Job lifecycle, reproducibility, report provenance and failure handling. |

### Priority 3 — account, overview and product quality

| Screen | Required target behavior | Verification focus |
|---|---|---|
| `Login` | Provide secure authentication and reliable session restoration/sign-out. | Invalid credentials, expired session, account switching and no fallback identity. |
| `Dashboard` | Summarize persisted data from the current user's authorized projects. | Accurate counts, refresh, empty state and data isolation. |
| `Integrations` | Show actual integration connection state and actionable setup/removal flows. | Credential handling, permission scope and connection revalidation. |
| `Issues` | Display real tracked issues associated with the selected project. | Source mapping, filtering, updates and empty/error states. |
| `Analytics` | Derive metrics from persisted, traceable events and define each metric. | Query correctness, date range, timezone and no fabricated sample metrics in production. |
| `Settings` | Persist user preferences and expose security/account controls accurately. | Persistence, validation, reset behavior and secret redaction. |
| `ProductQualityCenter` | Provide actionable quality findings backed by checks and evidence. | Finding provenance, severity consistency, deduplication and false-positive handling. |

### Shared workflow and component targets

- `ScreenQualityInspectorDialog`: findings must identify the inspected screen and evidence; distinguish automated checks from manual review.
- Team chat dialogs/panels: assignment, collaboration, activity, attachments, instructions and result review must reflect persisted state and authorized operations.
- Validation tabs: report real job inputs/results; sandbox fixtures must be visibly identified as fixtures.
- Vibe Studio dialogs/panels: approvals, diffs, checkpoints, governance, preview, terminal, testing and version control must be connected to real workspace operations before being presented as operational controls.

## Implementation sequence

1. Establish a reproducible baseline: inspect Gradle configuration, run available unit tests/build tasks, and record exact failures/results.
2. Stabilize the diagnostic path: crash ingestion → crash detail → symbolication → evidence-backed investigation → reviewed fix proposal.
3. Stabilize provider configuration and real-request validation; handle provider errors without crashing or masking failure.
4. Keep Live Testing unavailable until an authenticated, functioning Android Agent transport and event protocol exist.
5. Implement Vibe Studio workspace boundaries, file operations, diff/approval flow, checkpoints and controlled command execution.
6. Audit all 27 routes against the inventory and shared checklist; fix navigation, persistence, authorization, localization and accessibility defects.
7. Verify critical flows with automated tests and emulator/device runs; record evidence and exact commands.

## Current implementation notes

- Live Testing was deliberately changed to fail explicitly when no real Agent transport is available. This is a safety behavior, not completion of Live Testing.
- Crash investigation was changed to avoid manufacturing crash evidence when no real crash report is available. Continue auditing all displayed conclusions for provenance.
- AI provider validation was tightened to perform a real connectivity check. Continue testing error paths and confirm the UI does not crash.
- These notes describe prior code changes; they do not assert that Gradle, CI, emulator or device verification has passed. Record new verification results here only after observing them.


## Working rules

1. Update this file as each screen is inspected and fixed.
2. Record concrete defects and evidence under the relevant screen; do not label a screen "complete" because it compiles.
3. Keep simulated/demo functionality clearly labeled and isolated from production actions.
4. A task is complete only after code review plus the relevant automated test; runtime claims require an emulator/device run.


## Continuous task register — cybernetic space / engineering UI

This document is the living task register for screen implementation, not a one-time inventory. Update it in the same change as every screen audit, UI change, functional fix, and verification result. Keep incomplete work open; never mark a task done because a mockup exists or a composable compiles.

### Approved visual direction

- **Identity:** hybrid of futuristic space command center, cyberpunk, and precision engineering.
- **Effects:** strong visual treatment, controlled for legibility, performance and accessibility.
- **Palette:** deep-space navy/black surfaces; electric cyan and blue as primary accents; violet as secondary accent; green for verified success; amber for caution; red for errors.
- **Visual language:** angular engineering frames, restrained holographic/glass panels, orbital/grid motifs, technical typography, luminous status indicators and purposeful motion.
- **Product constraints:** Android-first responsive layouts; Arabic RTL and English LTR; readable code and diagnostics; reduced-motion accommodation; adequate contrast; no decorative animation that obscures controls or consumes resources needlessly.
- **Truthfulness constraint:** visual status indicators must be bound to actual application state. Do not show fabricated metrics, live connections, active agents, successful tests or running jobs.

### Global design-system tasks

| ID | Task | Applies to | Status |
|---|---|---|---|
| UI-001 | Inspect current Compose theme, typography, shapes, spacing and color tokens before changing them. | App-wide | [ ] |
| UI-002 | Define reusable cybernetic color, typography, spacing, shape, border and elevation tokens in the existing theme architecture. | App-wide | [ ] |
| UI-003 | Build reusable engineering surfaces, outlined panels, status chips, metric tiles and section headers; avoid duplicating one-off styling. | App-wide | [ ] |
| UI-004 | Establish consistent top bars, navigation, screen padding and adaptive layouts for phone sizes. | App-wide | [ ] |
| UI-005 | Implement accessible neon focus/pressed/disabled/error states and verify contrast. | App-wide | [ ] |
| UI-006 | Add purposeful motion and respect Android reduced-motion/system animation preferences where applicable. | App-wide | [ ] |
| UI-007 | Verify Arabic RTL, English LTR, long labels, font scaling and small screens. | App-wide | [ ] |
| UI-008 | Verify dark theme rendering, scrolling performance, recomposition and battery impact on a device/emulator. | App-wide | [ ] |
| UI-009 | Ensure all visual indicators are backed by real state and remove illustrative/demo values from operational screens. | App-wide | [ ] |
| UI-010 | Add/update screenshot or UI tests for shared components and representative screens. | App-wide | [ ] |

### Screen-by-screen visual implementation backlog

Status is intentionally **[ ] Not started** until the actual Compose screen and its interactions have been changed and verified.

| Route / screen | Visual treatment to implement | Status |
|---|---|---|
| Login | Secure access console; restrained orbital background; high-contrast form and validation. | [ ] |
| Dashboard | Space-command overview; real project metrics, diagnostics and clear primary actions. | [ ] |
| Projects | Engineering project cards/list, ownership and selected-project context. | [ ] |
| Applications | Application-focused view distinct in purpose from Projects, while reusing components where appropriate. | [ ] |
| Crashes | Diagnostic radar/list, severity hierarchy and real source/time metadata. | [ ] |
| CrashDetail | Investigation console with readable stack trace, evidence, timeline and provenance. | [ ] |
| FixAssistant | AI repair workbench with context, evidence/inference distinction and reviewable diff. | [ ] |
| FixHistory | Auditable repair timeline with actual persisted outcomes. | [ ] |
| Integrations | Connection control panels with real connection and permission states. | [ ] |
| Issues | Engineering issue tracker with clear severity, status and project context. | [ ] |
| Analytics | Technical telemetry dashboards with sourced metrics and readable charts. | [ ] |
| LiveTesting | Agent connection console; unavailable state must remain explicit until real transport exists. | [ ] |
| LiveSession | Live telemetry/event console populated only by actual session events. | [ ] |
| SetupGuide | Guided engineering setup with clear prerequisites and progress. | [ ] |
| GitHubConnect | Repository authorization console with least-privilege explanation and status. | [ ] |
| DebugSymbols | Artifact management and symbolication workbench with build matching evidence. | [ ] |
| AIModels | Model/provider control center with real request status, latency/errors where measured. | [ ] |
| VibeStudio | Main development cockpit: files, editor, agent activity, diffs, approvals and terminal states. | [ ] |
| DevDebugCenter | Workspace diagnostics console with explicit device/build/runtime evidence. | [ ] |
| Settings | System configuration console with grouped settings and clear destructive actions. | [ ] |
| ModelTeams | Agent network overview distinguishing configured, available and active agents. | [ ] |
| CreateTeam | Focused team-creation flow consistent with the agent-network design. | [ ] |
| TeamDetail | Team command view with membership, capabilities and real activity. | [ ] |
| TeamUsage | Usage/compute dashboard showing measured data and provenance. | [ ] |
| TeamChat | Operational communications console; message and task states must be persisted. | [ ] |
| TeamValidation | Validation control room with real job lifecycle and evidence-backed reports. | [ ] |
| ProductQualityCenter | Quality observatory showing actionable, traceable findings. | [ ] |

### Continuous execution log

Append newest entries first. Each entry must state what changed, files/routes affected, verification performed, and remaining risks. If no code or test was run, say so explicitly.

| Date | Commit / PR | Work completed | Verification | Remaining work |
|---|---|---|---|---|
| 2026-11-05 | — | Approved cybernetic space / cyberpunk / engineering direction with strong effects. Added global design-system and 27-route visual backlog to this task register. | Documentation update only; no screen implementation, Gradle build, UI test or device verification performed in this entry. | Implement shared Compose design tokens/components, then migrate screens incrementally without changing their real behavior. |

### Mandatory update protocol

1. Before editing a screen, identify its route, composable, ViewModel, state source and repository/use-case dependencies.
2. Add or refine the screen's concrete tasks here before or alongside implementation.
3. Preserve working business logic; visual refactors must not replace real operations with mock data or placeholder callbacks.
4. After editing, record changed files and the exact checks actually run. Separate static review, unit tests, build, emulator and physical-device verification.
5. Mark an individual task [x] only when its stated acceptance criteria are met. If verification is partial, leave it [ ] and record the partial evidence.
6. Add a dated execution-log entry for every meaningful implementation batch. Keep the latest entry at the top.
7. Reconcile this inventory with `Screen.kt` and `AppNavigation.kt` whenever routes are added, removed or renamed.
8. This file is the source of truth for remaining screen work; update it continuously on `main` as implementation proceeds.


## Individual screen objectives — purpose, user outcome and acceptance

These objectives are defined per destination. They do not require auditing or implementing all screens in one pass. The objective describes the intended product behavior, not a claim about current implementation. Work on one screen or a small dependency-aware group at a time.

### 1. Login — secure entry and identity
- **Purpose / outcome:** authenticate the user and establish the identity that scopes all later data; make sign-in, session restoration and sign-out understandable.
- **Required behavior:** validated sign-in form, progress state, actionable authentication errors, session restoration and safe sign-out.
- **Acceptance:** invalid credentials never enter the app; successful sign-in uses the identity returned by the auth provider; no fallback or hard-coded user ID; secrets are never logged or exposed.
- **Visual target:** space-console access panel with restrained orbital graphics and a highly readable form.

### 2. Dashboard — operational overview
- **Purpose / outcome:** show the user what is happening across their authorized projects and what needs attention next.
- **Required behavior:** selected-project context, recent real crashes/issues, verified summary metrics, refresh, empty state and shortcuts to core workflows.
- **Acceptance:** metrics derive from authorized persisted records; metric definitions/time windows are clear; cards navigate to the corresponding filtered screen; no sample metrics appear as live data.
- **Visual target:** flagship command center with clear hierarchy and compact telemetry.

### 3. Projects — project portfolio
- **Purpose / outcome:** create, select and manage software projects; make the active project unambiguous.
- **Required behavior:** project listing, supported create/edit/archive/delete actions, ownership and repository context, search/filter when useful.
- **Acceptance:** changes persist; destructive actions require confirmation; selected project context reaches dependent screens; loading/empty/offline/error states are distinct.
- **Visual target:** engineering project registry with structured cards and technical metadata.

### 4. Applications — project application inventory
- **Purpose / outcome:** display applications/build targets belonging to the supplied projectId and let the user select the correct target.
- **Required behavior:** project identity, package/application identifiers, available platform/build metadata and routes into setup, crash and symbol workflows.
- **Acceptance:** projectId is honored; applications are distinct from projects; cross-project data is impossible; invalid or empty project states are explicit.
- **Visual target:** application/build inventory with compact platform and package metadata.

### 5. Crashes — crash intake and triage
- **Purpose / outcome:** list and prioritize real crash reports for a project/application.
- **Required behavior:** crash records, severity, exception/title, sourced version/device/OS/time details, filters, sorting and navigation to the exact crash.
- **Acceptance:** each record has source provenance; filters change the actual query; crash ID is preserved; missing metadata is shown as unavailable, never inferred; no seeded production records.
- **Visual target:** diagnostic incident queue with severity hierarchy and dense but readable data.

### 6. CrashDetail — evidence-based investigation
- **Purpose / outcome:** explain what happened in one crash, what evidence exists and what remains unknown.
- **Required behavior:** original metadata, available stack trace, build/device context, symbolication state, investigation timeline and source evidence.
- **Acceptance:** crashId resolves to the correct authorized record; raw evidence is distinct from AI interpretation; partial/missing reports are explicit; no invented root cause or source location; FixAssistant receives the same crash context.
- **Visual target:** forensic engineering console; stack trace readability takes priority over decoration.

### 7. FixAssistant — reviewed repair proposal
- **Purpose / outcome:** generate a safe code-change proposal grounded in the selected crash and actual source context.
- **Required behavior:** crash context, retrieved source references, evidence/inference distinction, proposed patch, diff review, provider status and approval/apply flow when supported.
- **Acceptance:** no source context means no source-specific claim; provider failures remain visible; a patch is not called applied until a real write succeeds; identity is authenticated with no fallback constant; risky edits require approval.
- **Visual target:** AI repair workbench separating evidence, explanation, diff and approval.

### 8. FixHistory — repair audit trail
- **Purpose / outcome:** preserve attempted fixes and their real outcomes for future review.
- **Required behavior:** chronological entries, project/crash association, author, summary, status, timestamps and retained diff/report links.
- **Acceptance:** history persists across restarts; pending/applied/rejected/failed are distinct; status follows the actual operation; project filters work; no fabricated success records.
- **Visual target:** traceable engineering change timeline.

### 9. Integrations — external connections
- **Purpose / outcome:** configure and manage supported integrations for one project.
- **Required behavior:** integration catalog, setup, permission scope, real connection test, last-checked state and disconnect/revoke.
- **Acceptance:** connected means credentials/session were actually validated; projectId scopes the integration; expired access and failures are actionable; secrets are redacted; revoke is real.
- **Visual target:** modular connection panels with honest health states.

### 10. Issues — engineering work queue
- **Purpose / outcome:** unify real project issues and let the user navigate to their canonical source.
- **Required behavior:** title/type/severity/status/source/project, filters and detail navigation.
- **Acceptance:** supplied projectId is parsed and applied; source and sync time are visible; updates persist to the authoritative source; no placeholder issue is represented as real.
- **Visual target:** technical issue tracker with clear priority and lifecycle.

### 11. Analytics — explainable engineering metrics
- **Purpose / outcome:** reveal trends in crashes, reliability, resolution and project health for informed decisions.
- **Required behavior:** project scope, date range, metric definitions, trend charts and drill-down to source records.
- **Acceptance:** calculations reconcile with source data; timezone and aggregation window are explicit; empty data is not presented as a success rate; no invented percentages.
- **Visual target:** telemetry observatory with legible charts, units and restrained glow.

### 12. LiveTesting — real device test control
- **Purpose / outcome:** run tests on a real Android device through an authenticated Agent.
- **Required behavior when implemented:** Agent pairing/discovery, device identity, permissions, test selection, start/stop, progress, logs and results.
- **Acceptance:** until real transport and protocol exist, show unavailable and do not create sessions; once implemented, commands require Agent acknowledgement and handle timeout/disconnect safely.
- **Visual target:** spacecraft diagnostic bay; connection status must reflect a real Agent.

### 13. LiveSession — inspect one test session
- **Purpose / outcome:** display real events and final evidence for the supplied sessionId.
- **Required behavior:** session/device/Agent identity, lifecycle, ordered event stream, logs, artifacts and supported cancel/reconnect actions.
- **Acceptance:** session ownership is checked; events come from the real Agent/backend; timestamps/order are preserved; reconnect does not duplicate events; expired sessions have an explicit state.
- **Visual target:** live telemetry console; live indicators appear only while an actual stream is active.

### 14. SetupGuide — project onboarding
- **Purpose / outcome:** guide the user through prerequisites to make project diagnostics operational.
- **Required behavior:** ordered setup steps, copyable configuration, per-step validation, persisted progress and links to relevant screens.
- **Acceptance:** instructions match supported integrations; a step completes only after real validation; platform/build requirements are stated; failed steps can be resumed.
- **Visual target:** guided mission checklist with validation checkpoints.

### 15. GitHubConnect — repository authorization
- **Purpose / outcome:** connect the correct GitHub repository to the selected project using minimum required permissions.
- **Required behavior:** authorization, repository/branch selection, permission explanation, connection status and revoke/disconnect.
- **Acceptance:** tokens are stored securely and redacted; repository access is validated; project mapping is correct; expired or revoked access is reflected accurately.
- **Visual target:** repository control console with branch and permission details.

### 16. DebugSymbols — symbol artifact lifecycle
- **Purpose / outcome:** upload and manage symbol artifacts so stack traces can be resolved against the correct build.
- **Required behavior:** supported artifact types, upload validation/progress, project/build mapping, artifact inventory and symbolication results.
- **Acceptance:** artifact identity and build match are validated; unsupported/mismatched files fail clearly; unresolved frames remain visible; no success is shown without backend confirmation.
- **Visual target:** binary artifact workbench with build matrix and symbol-resolution status.

### 17. AIModels — provider/model control center
- **Purpose / outcome:** configure providers and select models for supported tasks while understanding actual availability.
- **Required behavior:** provider setup, model selection, secure key entry, real connection test and provider-specific error reporting.
- **Acceptance:** CONNECTED requires a successful real request; invalid key/quota/timeout/rate-limit/empty response are distinct; test action cannot crash; unsupported providers are not shown as operational.
- **Visual target:** neural compute control panel with truthful connection telemetry.

### 18. VibeStudio — primary development workspace
- **Purpose / outcome:** inspect, modify, validate and manage a real software project with AI assistance.
- **Required behavior in stages:** explicit workspace selection, file tree/read, editor/diff, task panel, approval queue, checkpoints, Git status and controlled command/test output.
- **Acceptance:** file operations stay inside the selected workspace; sensitive writes require approval; diffs precede changes; checkpoints restore state; commands run within defined isolation; every tool reports real success/failure; unavailable features are disabled or labeled.
- **Visual target:** flagship cybernetic engineering cockpit; code and diffs remain readable while agent telemetry is secondary.

### 19. DevDebugCenter — workspace diagnostics
- **Purpose / outcome:** aggregate build, dependency, device, runtime and configuration diagnostics for one workspace.
- **Required behavior:** workspace identity, diagnostic categories, check results, timestamps, evidence/log references and links to repair destinations.
- **Acceptance:** optional workspaceId is resolved explicitly; checks target the intended workspace; stale results are labeled; no device/build/runtime state is inferred without a source.
- **Visual target:** engineering operations console with categorized health panels.

### 20. Settings — account and app configuration
- **Purpose / outcome:** manage supported account, appearance, language, notification, privacy and security preferences.
- **Required behavior:** grouped preferences, current values, validation, persistence, reset and account/session controls.
- **Acceptance:** settings persist across restart; unsupported options are not shown as active; credentials are never exposed; sign-out clears session and returns to Login.
- **Visual target:** system configuration deck with structured sections and explicit states.

### 21. ModelTeams — agent team registry
- **Purpose / outcome:** browse and manage teams of agents configured for coordinated work.
- **Required behavior:** team list, create entry point, purpose/member/role summary, configuration state and navigation to detail/chat/usage.
- **Acceptance:** teams persist and are owner/workspace scoped; configured agents are distinguished from connected/active agents; errors are visible; duplicate creation is handled.
- **Visual target:** agent-network topology supported by a readable roster and status labels.

### 22. CreateTeam — focused team creation
- **Purpose / outcome:** create a team with a clear goal, members, roles and coordination policy.
- **Required behavior:** name/description, agent selection, role/instruction configuration, validation, create and cancel.
- **Acceptance:** required fields are validated; selected agents exist and are available for the intended operation; creation persists once; failure preserves input; success opens the created team.
- **Visual target:** mission configuration flow distinct in purpose from the team registry.

### 23. TeamDetail — team operations
- **Purpose / outcome:** inspect and manage the team identified by teamId.
- **Required behavior:** team identity, roster/roles, capabilities, instructions, status and supported management actions.
- **Acceptance:** teamId is validated and access-controlled; changes persist; active status comes from real heartbeat/events; inaccessible/deleted teams have explicit states.
- **Visual target:** team command deck with roster and capability map.

### 24. TeamUsage — measured usage and cost
- **Purpose / outcome:** explain resource consumption for the selected team over a defined period.
- **Required behavior:** teamId, date range, metric units, provider/model breakdown and data freshness/provenance.
- **Acceptance:** values come from metered records; estimates are labeled as estimates; currency/units are defined; missing telemetry is not shown as zero.
- **Visual target:** compute/resource telemetry with clear units and no misleading gauges.

### 25. TeamChat — persistent team communication
- **Purpose / outcome:** send instructions, assign tasks and review team responses/results.
- **Required behavior:** persisted conversation, composer, delivery state, task assignment, supported attachments, agent attribution and result review.
- **Acceptance:** messages are persisted and access-controlled; delivery/retry states are real; task status comes from orchestration events; attachments are validated; agent replies are not fabricated.
- **Visual target:** command communications console balancing readable chat and compact activity.

### 26. TeamValidation — validation jobs and reports
- **Purpose / outcome:** run and inspect validation jobs for team outputs or project changes.
- **Required behavior:** validation categories, job configuration, progress, supported cancellation, structured results and evidence.
- **Acceptance:** each result links to a real job and versioned inputs; fixtures are labeled; pass/fail criteria are explicit; partial/failed jobs are not shown as passed.
- **Visual target:** validation control room with matrix, report and sandbox views.

### 27. ProductQualityCenter — quality governance
- **Purpose / outcome:** identify, prioritize and track product defects and quality risks to verified resolution.
- **Required behavior:** finding, severity/rationale, affected route/component, reproduction evidence, owner/status and retest outcome.
- **Acceptance:** findings are traceable and deduplicated; severity is consistent; automated checks differ visibly from manual review; resolved requires successful retest or documented disposition.
- **Visual target:** quality observatory with prioritized findings and evidence drill-down.

### Screen execution policy

- Do not attempt to audit or redesign all 27 screens in one pass.
- Select one screen or a small dependency-aware slice based on risk and prerequisites.
- For the selected screen: inspect its code and dependencies, refine its tasks, implement, run relevant tests, record exact results and update only its status.
- All other objectives remain planned work; their presence here does not mean they were inspected or implemented.
- Suggested dependency path: Login/session identity → Projects/Applications context → Crashes → CrashDetail → DebugSymbols → FixAssistant → FixHistory. This is a proposed sequence, not a completion claim.
