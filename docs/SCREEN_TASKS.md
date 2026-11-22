# Screen Inventory & Implementation Tasks

> Scope: Android app navigation declared in `app/src/main/java/com/example/presentation/navigation/Screen.kt` and registered in `AppNavigation.kt`.
>
> Inventory source: static inspection of the current `main` branch. This is a route inventory, not proof that every route is reachable, complete, or working at runtime.
>
> Status key: [ ] Not audited · [x] Audited and verified. Do not mark a task complete from source inspection alone; verify behavior and tests.

## Mandatory design-system and screen-design gate (2026-11-06)

> **Non-negotiable:** no screen may be marked complete, closed, or handed off until its visual design and UX have been materially reviewed and improved in the same feature slice as its functionality. Backend wiring, bug fixes, or passing unit tests alone do not count as screen completion.

- Every screen task must record: (1) observed visual/UX problems, (2) concrete visual changes implemented, (3) interaction and responsive/accessibility decisions, and (4) visual verification evidence (emulator/device screenshot or an explicit, documented blocker).
- If visual verification is unavailable, record the limitation and keep the screen open. Never claim visual completion based only on source inspection.
- Apply the shared Vibe Engineering design language consistently: cosmic depth + cyber/technical accents + engineering precision. Avoid arbitrary per-screen styling and generic uncustomized Material defaults.
- Reuse centralized color, typography, spacing, shape, elevation, motion and component tokens. Any new token must be added to the shared theme rather than hard-coded independently in a screen.
- Validate both Arabic RTL and English LTR, small screens, keyboard/IME behavior, accessibility semantics, and loading/empty/success/error/offline states as applicable.
- Required screen workflow: inspect → record design issues → implement visual/UX refinement → wire real interactions/data → test → inspect rendered UI → document evidence. Do not advance to the next screen while the current screen's design gate remains open.
- **Login is explicitly not design-complete.** Its existing dark grid/cyan foundation is not a substitute for a substantive visual refresh and rendered visual review.

## Foundational platform directive — Supabase migration (added 2026-11-05)
- **Priority:** foundational; blocks production authentication and cloud-data work.
- **Decision:** create a dedicated Supabase project for Vibe Coding. Do not reuse the existing `qabasapp` project; it belongs to a separate product and contains unrelated platform data.
- **Scope:** replace Firebase services actually used by this Android application with Supabase equivalents, rather than layering Supabase alongside Firebase. Inventory Firebase Authentication, Firestore/database, Storage, Functions, messaging, analytics and crash reporting; migrate each used capability to a suitable Supabase service or document a deliberate alternative/removal.
- **Security:** enforce Row Level Security (RLS) on exposed tables and Storage policies; preserve authenticated-user and project-level data isolation. Never commit service-role keys or secrets. Android clients use only publishable/anon credentials protected by RLS.
- **Android integration:** configure Supabase Kotlin client and secure auth-session persistence. Remove Firebase SDK/configuration and obsolete code only after its replacement is implemented and verified.
- **Execution order:** create isolated project → inventory Firebase usage/data models → design schema, migrations, RLS and Storage buckets → implement required Edge Functions → integrate Android repositories/auth/session → remove obsolete Firebase dependencies/config → test authentication, authorization boundaries, CRUD, uploads/downloads and failure/offline behavior.
- **Acceptance:** no production feature depends on Firebase; all cloud access is scoped to authenticated users and authorized projects; RLS and negative authorization tests pass; no secrets in source control; Android build and end-to-end flows are verified.
- **Current status (2026-11-05):** dedicated Supabase project successfully created and verified as `ACTIVE_HEALTHY`: `vibe-coding` (`hgfgbjomwhogpswpqusk`), organization `Developer Abu Mu'adh`, region `eu-west-1`, API URL `https://hgfgbjomwhogpswpqusk.supabase.co`. Initial `public` schema is empty and migration history is empty. Existing `qabasapp` remains untouched. Firebase inventory, schema design, RLS, Android integration and Firebase removal are still pending; do not treat migration as complete.

## Priority 0 — Supabase migration by feature slice (2026-11-05; execution model updated 2026-11-06)

> **Execution gate:** Supabase platform foundations (secure Auth/session handling, compatible Android API wiring, schema/RLS conventions) are prerequisites. After those foundations, migrate cloud functionality incrementally by feature slice, in dependency order. A slice includes its screen(s), ViewModel, repository/use cases, domain/DTO mapping, database objects and RLS policies, visual/interaction redesign, accessibility/localization, and tests. Do not split screen work into a later visual-redesign phase: whenever a screen is taken up, complete its visual design and UX together with its real functionality and required cloud integration, then verify the combined result. Do not claim a screen is complete until its entire slice is implemented and verified. This gate does not prohibit independent reliability, architecture, UI-component or local-only work.

### Shared prerequisites — required before cloud feature slices

- [ ] Complete the Firebase reference/configuration inventory and classify each use as migrate, remove or intentionally retain with a documented reason.
- [ ] Confirm the shared Supabase schema, identity model and ownership conventions against Kotlin domain models; add versioned migrations for missing entities and relationships.
- [ ] Complete the Supabase Auth lifecycle: signup, email verification, sign-in, verified password recovery, sign-out and session refresh; persist sessions securely on Android.
- [ ] Wire a Kotlin-compatible Supabase access layer through dependency injection without shipping service-role keys or provider secrets.
- [ ] Establish reusable RLS policies/helpers and automated authorization test identities for anonymous, owner, member, non-member and cross-user cases.
- [ ] Remove shared fallback identities (including every `usr_dev_master` path) from shared authentication/identity infrastructure; unauthenticated cloud operations must fail closed.

### Ordered cloud feature slices

Complete each slice end-to-end before marking it complete. Visual redesign and functional/backend work are part of the same screen task, not separate future passes. A screen is complete only when its intended visual target, interactions, data behavior, authorization and relevant tests meet acceptance criteria. The Supabase foundation remains a shared prerequisite; independent visual/component work may continue where it does not falsely imply unfinished cloud features are operational.

1. **Identity and account — `Login`, profile/session flows**
   - [ ] Finish Supabase Auth repository contract and verified recovery/deep-link flow.
   - [ ] Verify session restoration, refresh-token rotation, sign-out and account switching.
   - [ ] Add tests for invalid credentials, expired/rejected refresh tokens, transient failures and email-confirmation-required signup.
2. **Projects and applications — `Projects`, `Applications`, `Dashboard` project context**
   - [ ] Migrate project/application repositories and mappings.
   - [ ] Enforce owner/member access and project-level RLS; verify CRUD and cross-project isolation.
   - [ ] Remove local/developer identity fallbacks from these data paths; test empty, offline and authorization failures.
3. **Crash diagnostics — `Crashes`, `CrashDetail`, `FixAssistant`, `FixHistory`, `DebugSymbols`, `DevDebugCenter`**
   - [ ] Migrate crash records and related diagnostic metadata, preserving source provenance and project/application relationships.
   - [ ] Implement required artifact storage/policies and any privileged symbolication operation only where required.
   - [ ] Verify upload/download, lookup, project isolation, partial records and failure handling; no fabricated reports or success states.
4. **Issues and integrations — `Issues`, `Integrations`, `SetupGuide`, `GitHubConnect`**
   - [ ] Migrate persisted issue and integration state; distinguish local setup instructions from remotely verified connection state.
   - [ ] Apply per-project authorization and least-privilege credential handling.
   - [ ] Verify create/read/update/disconnect and provider failure behavior.
5. **Teams and collaboration — `ModelTeams`, `CreateTeam`, `TeamDetail`, `TeamUsage`, `TeamChat`, `TeamValidation`**
   - [ ] Migrate team configuration, membership, execution, messages, assignments and usage records that are confirmed as persisted product data.
   - [ ] Add ownership/member RLS and audit-sensitive mutation policies.
   - [ ] Remove Firestore in-memory-success fallbacks; propagate remote write failures accurately.
   - [ ] Verify team isolation, membership changes, execution ownership and persisted history.
6. **Audit, notifications and analytics — `Dashboard`, `Analytics`, notification flows**
   - [ ] Migrate confirmed audit/notification event storage and define metric provenance.
   - [ ] Verify user/project scoping, pagination/date boundaries and no demo metrics presented as production data.
7. **Remaining cloud-backed capabilities**
   - [ ] Reconcile the completed repository inventory against all remaining Firebase references and decide migrate/remove/retain for each.
   - [ ] Implement any confirmed Storage buckets or Edge Functions not already covered by a feature slice, with explicit policies and tests.

### Completion and retirement criteria

- [ ] Every confirmed Firebase-backed production workflow has a completed, tested Supabase feature slice or a documented intentional alternative/removal.
- [ ] RLS/Storage negative authorization tests pass for anonymous, owner, non-member and cross-user cases.
- [ ] Gradle compilation, relevant unit/instrumentation tests and emulator/device end-to-end tests pass; record exact commands and outputs.
- [ ] Remove Firebase dependencies, initialization, configuration files/resources and obsolete code only after all corresponding feature slices pass. Do not remove a still-used Firebase capability prematurely.
- [ ] Re-run a repository-wide Firebase scan and confirm no unintended production dependency remains.
- [ ] Update this register with file-level changes, commits, test evidence and remaining limitations.

**Current state:** Supabase project, protected initial schema and partial Auth source wiring exist. The projects/applications repositories now have an initial authenticated PostgREST slice, but multiple Firebase-backed repositories and DI/runtime references remain. Feature-slice migration has not been completed; all checkboxes above remain open. Android Kotlin compilation and debug APK packaging were verified in the sandbox; unit-test suite completion and Android device/emulator end-to-end verification are not claimed.

### 2026-11-15 — Projects/applications PostgREST slice and Android build verification
- Added authenticated PostgREST DTO/API/data-source wiring for project and application reads/writes, using the stored Supabase user session and publishable key; existing identity checks and RLS remain in force.
- Routed project/application listing, creation/deletion and project security-setting updates through the new source while retaining Room as the local cache. This is a first slice only; realtime updates, membership/audit/crash/issue/team data and legacy Firestore migration remain open.
- Fixed cross-file visibility of `isGitHubPullRequestUrl` used by `FixHistoryScreen`.
- Fixed Android resource parsing by escaping apostrophes in five English authentication error strings.
- Added Gradle 9.3.1 wrapper scripts and JAR; CI continues to provision the matching Gradle version.
- Verification on the current branch: `:app:compileDebugKotlin` and `:app:assembleDebug` succeeded locally; the latest compile of the app and unit-test sources also succeeded. GitHub Actions initially failed before any runner steps; after runner availability was restored, the workflows exposed test issues. The PR Validation workflow's JDK was raised from 17 to 21 because Robolectric with Android SDK 36 requires JDK 21. The free-model sync test now gives its fake OpenRouter API a non-empty inference response because `syncModels` verifies provider connectivity before listing models. The pause/resume engine test now uses an in-memory repository fake instead of Firebase without an authenticated user. Full-suite CI showed AndroidKeyStore unavailable in Robolectric for requirements 3 and 20, so crypto is injectable while production still defaults to `SecurityUtils`/Android Keystore and JVM tests supply fakes; password-recovery, sign-out-race, and resilience auth tests also use fake session-store crypto. The project multi-tenant test now switches an authenticated test identity while seeding and reading each user's records, preserving the production identity guard. CrashRepository accepts an optional test identity provider, while FirebaseAuth remains the production default. The AES-GCM smoke test verifies masking in JVM tests and skips only the Android Keystore round-trip when Robolectric lacks that provider; actual Keystore verification remains device/instrumentation-only. FixAssistant's Proxy test uses an unchecked exception to avoid Java's checked-exception wrapping, and FixHistory now names `allFlow` explicitly; both targeted ViewModel test classes pass locally. IntegrationWorkflowTest now supplies a matching `usr-dev` test identity; its targeted Robolectric run still exceeds the local 2-minute test-task timeout and needs CI confirmation. No live app or RLS negative-authorization test is claimed.

---

## 2026-11-06 — Fix Assistant identity guard (source change; runtime unverified)

- Updated `AppNavigation.kt` to pass the authenticated user's ID as nullable data instead of substituting the shared `usr_dev_master` identity.
- Updated `FixAssistantScreen.kt` to remove its hard-coded fallback identity, disable Pull Request creation when no non-blank authenticated user ID is present, and show an explicit sign-in requirement.
- Updated `FixAssistantViewModel.kt` to fail closed if PR creation is invoked with a blank user ID.
- Commits: `6fab4b90c3cd99d045c3ce9a887885a5f0c5db7d`, `00f8b4e071ec0198999e46e4449253a037fed5b5`, `72670cd8cca161217ab87b5b23be52f59ed1a7ad`.
- Verification: source edits were accepted by GitHub. No Gradle build, unit test, emulator/device run, or live GitHub PR flow was executed. This is a targeted UI/ViewModel guard, not a repository-wide removal of `usr_dev_master`; other fallback references remain open in the shared prerequisite checklist.

## 2026-11-06 — Cross-feature fallback identity hardening (source changes; runtime unverified)

- `AIModelTeamRepositoryImpl.kt`: replaced the implicit `usr_dev_master` identity with an explicit authentication failure for repository operations requiring a user.
- `CrashRepositoryImpl.kt`: removed the shared fallback from its authentication helper and from AI fix/verification model selection. Missing or unverifiable identity now fails closed rather than routing work to a shared developer account.
- `TeamValidationViewModel.kt`: validation runs now require a non-blank authenticated user and surface a sign-in message otherwise.
- `TeamChatViewModel.kt`: initial team loading and user-scoped team actions now stop with an authentication message when no current user exists.
- `DevDebugCenterViewModel.kt`: clears workspace data and reports the sign-in requirement instead of loading a shared developer workspace.
- Commits: `4989f52952991c9b09d7c0edacfd55c826ad7421`, `ee1520ab5b63b95d5ff92d8b0401a3126ffa42e4`, `d42e86fd8bd931dedd15d18fee7473bb280ba3fc`, `3357dad8819df4d5ef8c876d4b129313d13b459d`, `9b9b64721d3e43b67c16be5ead6a4f65ea9956f0`, `f18fcfd70e3bc4e0c10cad993b75402003c3a31e`.
- Verification: all edited source files were re-fetched from `main`; the targeted fallback strings were checked. No Gradle build, automated test, emulator/device run, or backend authorization test was executed.
- Remaining: `SeedData.kt` still contains a developer-owned sample project and member fixtures. Treat this as demo/seed data requiring an explicit production-seeding review; do not silently assign those records to a real user. The repository-wide identity audit remains open, including all remaining user/workspace/project fallback patterns.

## 2026-11-06 — Signing-key exposure containment (working tree only; history remediation outstanding)

- Removed the two committed Android signing-keystore files from the current `main` tree.
- Added `*.jks` and `*.keystore` ignore rules to `.gitignore`.
- Commits: `7d29cd685dcc635131da77ee469b8bed16a1e65d`, `01e1c14957d73012c96616b422c4bd3e9e5d90a8`, `3a56c42054f0b989aa3bcec23a8a5ef750a8fd71`.
- Critical limitation: these commits remove the files from the latest tree only; prior Git history may still contain the private key material. Treat both signing keys as potentially exposed: rotate/revoke affected signing credentials where possible, audit release access, and plan a coordinated Git-history rewrite plus force-push only with repository-owner approval because it rewrites shared history and disrupts existing clones.
- No claim is made that the historical blobs are purged or that key rotation has occurred.

## 2026-11-06 — Shared Vibe design tokens and Login visual refinement (source changes; rendered UI unverified)

- Added `app/src/main/java/com/example/ui/theme/VibeDesignTokens.kt` as a shared token foundation for cosmic surfaces, cyan/violet accents, text/status colors, spacing and shape values.
- Updated `LoginScreen.kt` to consume shared color tokens, add a compact authentication-system status rail with a cyan/violet gradient edge, strengthen the access-card gradient framing, and apply IME padding so the scrollable form can respond to the on-screen keyboard.
- Existing sign-in/sign-up callbacks, validation, confirmation-required messaging and disabled recovery behavior were preserved; no authentication capability was simulated or newly enabled.
- Design review finding: Login previously had a dark grid and cyan/violet accents, but relied on a comparatively generic outlined card/form hierarchy. This change is a targeted visual improvement, not a complete screen redesign or visual sign-off.
- Commits: tokens `e656a0961f0760966de276cf68765ee2aeb85d00`; Login `644ead6751f95956de072ceabf5391d96002aeb7`.
- Verification: source files were written through GitHub and will be re-fetched for content verification. No Gradle build, unit test execution, screenshot, emulator or device visual review is claimed. Login remains unchecked in the screen inventory and must not be considered complete.
- Remaining design-system work: unify the existing `Eng*` theme palette with the new tokens, define reusable component/state patterns and document spacing/type/motion/accessibility rules. Correct the semantic mismatch where `EngCyan` is currently assigned a green hex value only after auditing its usages.

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


### 2026-11-05 — Automatic profile provisioning on Supabase Auth signup
- Applied migration `create_profile_on_auth_user_signup` to project `hgfgbjomwhogpswpqusk`.
- Added an `AFTER INSERT` trigger on `auth.users` that creates/updates the matching `public.profiles` row using the Auth user UUID and optional display-name/organization metadata.
- Trigger calls `private.handle_new_auth_user()`; function uses `SECURITY DEFINER` with an empty fixed search_path and schema-qualified object references. The `private` schema is revoked from public client roles, and function EXECUTE is revoked from `PUBLIC`, `anon`, and `authenticated`. This is a narrowly scoped trigger-only privileged function, not a public RPC.
- Verified trigger metadata (AFTER INSERT on auth.users), function security/search_path metadata, and Supabase security advisor returned zero lints.
- **Not yet tested:** no actual signup was performed, so profile creation behavior still requires an end-to-end Auth test after Android integration.
- **No Android changes in this batch:** Supabase Auth client/session implementation and Firebase replacement remain pending. No Gradle build or device tests run.
- **Gate:** Priority 0 Supabase integration remains open; screen work remains blocked.

### 2026-11-05 — Supabase team persistence schema
- Applied migration `add_model_teams_and_executions` to project `hgfgbjomwhogpswpqusk`.
- Added `public.model_teams` with JSONB storage for members/settings/governance and owner-bound UUID.
- Added `public.team_executions` with execution state, steps, messages, shared memory, usage, failover and attachment metadata; references team, authenticated user and optional project.
- Enabled RLS and added owner-only SELECT/INSERT/UPDATE/DELETE policies. Execution access additionally requires ownership of the parent team.
- Added owner/time indexes and authenticated grants.
- Verified both tables and all four policies per table through Supabase metadata; security advisor returned zero lints after migration.
- Existing public schema currently has 9 core tables including these two team tables. All are empty; no Firebase data has been migrated.
- **Limitations:** RLS was inspected through catalog metadata and Supabase advisor only; it has not yet been exercised using separate real authenticated test accounts. Team data remains JSONB aggregate storage matching current Kotlin model shape; normalization may be required if team collaboration expands to independent membership/message queries.
- **Android integration:** still not implemented. No Kotlin/Gradle changes, no app build, and no device tests in this batch.
- **Next:** model remaining repository data (integrations, GitHub connections, symbols, fixes, validations, notifications, AI provider settings, Vibe workspaces) against domain models, then implement Supabase auth/session and repository adapters.
- **Gate:** Supabase integration remains Priority 0 and blocks ordinary screen tasks.

### 2026-11-05 — Firebase dependency inventory and Android SDK compatibility review
- **Repository scan:** GitHub code search for `firebase` identified Firebase references in `AuthRepositoryImpl.kt`, `CrashRepositoryImpl.kt`, `AIModelTeamRepositoryImpl.kt`, `DependencyProvider.kt`, `FirebaseUtil.kt`, `CrashIntelligenceApp.kt`, `SdkIntegrationRepositoryImpl.kt`, `SeedData.kt`, `Models.kt`, `SetupGuideScreen.kt`, `IntegrationsScreen.kt`, `WorkspaceGitEngine.kt`, `VibeQualityTestingEngine.kt`, `ProductQualityGateEngine.kt`, test sources, and Firebase-specific resources/configuration. This is a search-derived inventory and each candidate still needs contextual review before removal.
- **Confirmed runtime wiring:** `DependencyProvider` constructs Firebase Auth and Firestore and injects them into repositories; `CrashIntelligenceApp` calls Firebase initialization; `FirebaseUtil` contains a fake programmatic FirebaseOptions fallback; `AuthRepositoryImpl` uses Firebase Auth + Firestore profiles and still has a local Room auth branch; `CrashRepositoryImpl` and `AIModelTeamRepositoryImpl` use Firestore. `AIModelTeamRepositoryImpl` stores teams and executions under user-scoped Firestore subcollections.
- **Identity isolation defect:** `CrashRepositoryImpl.requireUserId()` falls back to the shared literal `usr_dev_master` when Firebase identity is unavailable. This must be removed; unauthenticated cloud access must fail closed, not share a fallback account.
- **Build configuration:** `app/build.gradle.kts` still declares Firebase BOM, Firestore, Auth and Firebase AI dependencies; `gradle/libs.versions.toml` contains their catalog entries. Firebase-specific resource `app/src/main/res/values/firebase_applet_config.xml` and `firebase-blueprint.json` also remain. Their removal is deferred until references are replaced and verified.
- **SDK compatibility gate:** current app uses Kotlin `2.2.10` and `minSdk 24`. The current Supabase Kotlin SDK release surfaced during documentation review is `3.9.0`, whose published metadata reports Kotlin `2.4.21`; the SDK documentation also states Android minimum SDK 26 unless core-library desugaring is enabled. Do not pin 3.9.0 blindly. Select a compatible SDK/Kotlin/AGP/desugaring combination and validate it with Gradle before migrating repositories.
- **Official guidance reviewed:** Supabase Kotlin installation and Android quickstart documentation; SDK release notes reviewed for current compatibility/security changes. Client configuration must use the publishable key only; never embed a secret/service-role key.
- **Not changed in this batch:** no Android source/dependency changes and no Firebase services removed. This avoids introducing an unverified SDK version or partially migrating authentication.
- **Next implementation batch:** inspect complete repository/domain model contracts and Firebase-backed methods; determine exact required tables/columns and storage/functions; choose a Kotlin/SDK compatibility set; then add Supabase client/session persistence with a compile check before changing AuthRepository.
- **Verification:** code search and source reads only. No Gradle build, unit/instrumentation tests, real Auth flow, or emulator/device test was run in this batch.
- **Status:** inventory and compatibility analysis in progress; Supabase migration gate remains open.

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


### 2026-11-05 — Login authentication hardening
- **Commits:** `76fec0da7954c4c4d983aac6d5e3d468d794886d` (Firebase/local auth routing and blank-hash rejection); `8f4f5b0a33705a12ffae35a53bfa4ea27dc545f2` (Firebase profile synchronization made non-blocking after account creation); `10da0df7ab808c0a01cdff8a81696e1e93c5be98` (accurate password-reset confirmation); `488ae67cd61f1780e166d35b37056d0131675758` (initialize the auth state from the synchronous current Firebase identity).
- **Files:** `AuthRepositoryImpl.kt`, `MainViewModel.kt`.
- **Implemented:** when Firebase Auth is configured, email/password sign-up and sign-in now use Firebase Auth instead of silently authenticating against the local Room account table; profile synchronization is best-effort and does not turn a successfully created Firebase identity into a reported registration failure. Local sign-in now rejects accounts with missing password hashes/salts instead of accepting any password. Firebase-backed recovery sends the provider's email reset link; local-account recovery is explicitly unsupported until email ownership can be verified. A Firebase signed-out state no longer falls back to a stale in-memory local identity.
- **Static review:** fetched the updated files from `main` and inspected the changed auth branches. No Gradle build, tests or runtime exercise were available in this pass.
- **Remaining blockers:** local sessions are still memory-only and are not restored after process death; local password hashing remains SHA-256 rather than a password-specific KDF; Firebase configuration, email/password provider enablement, Firestore rules and end-to-end flows have not been verified. A repository-wide scan also found remaining hard-coded `usr_dev_master` identity fallbacks in navigation, crash/team repositories and several ViewModels; these are a cross-screen data-isolation risk and must be removed in a separate dependency-aware pass. The sign-up and sign-in paths still need automated tests, and Login remains [ ] Not audited/verified.
- **Status:** partial security/flow correction only; not a completion claim.

### 2026-11-05 — Login screen implementation batch
- **Commit:** `61457a52390321e1c4146651547257d01780c1ce` (Login UI); `40dd91a7c8532dcffbfbe709f3e7f8a5ad98b780` (local auth fallback removal).
- **Files:** `LoginScreen.kt`, `AuthRepositoryImpl.kt`.
- **Implemented:** replaced prefilled demo credentials and the quick-demo-login action with empty credential fields; added email/password validation, sign-up confirmation checks, clearer inline validation/errors, a cybernetic space-console visual treatment (dark orbital grid, cyan/violet accents, engineering status panel), and explicit failure when local auth storage is not configured. Removed automatic creation of the hard-coded developer account and its hard-coded sign-in bypass.
- **Static review:** re-fetched both files from `main`; confirmed the demo credential strings and developer-account branch are absent from the updated auth repository and the new login screen.
- **Not verified:** Gradle compilation, unit/UI tests, emulator rendering, device behavior, persistence/session restoration and end-to-end sign-up/sign-in.
- **Known remaining risks:** local sign-in state is held in memory and may not survive process death; password recovery currently changes a locally stored password without an email ownership challenge; Firebase-backed sign-up/sign-in behavior is not yet integrated into these repository methods. Do not mark Login complete until these are resolved and tested.
- **Status:** Login remains [ ] Not audited/verified. This batch is a partial implementation, not completion.

### 2026-11-05 — Supabase Auth REST foundation (partial; not yet wired)
- Added typed Retrofit endpoints for Supabase Auth signup, password grant, refresh-token grant, password-recovery request, and sign-out:
  - `app/src/main/java/com/example/data/remote/supabase/SupabaseAuthApi.kt`
  - Commit: `e56295efb50a531955c7cd6bce01da2595de35e6`
- Added Moshi DTOs for signup/login/refresh/auth responses, user metadata, stored sessions and Auth errors:
  - `app/src/main/java/com/example/data/remote/supabase/SupabaseAuthModels.kt`
  - Commit: `b8cb4208d4277a32401d81abdda637538f9c56be`
- Added `SupabaseSessionStore`, encrypting serialized access/refresh tokens with the existing Android Keystore AES-GCM helper and failing closed (clearing unreadable ciphertext).
  - `app/src/main/java/com/example/data/remote/supabase/SupabaseSessionStore.kt`
  - Commit: `8b8511b88507049d37ab32d19e676ea64f3b08b3`
- Set the actual Supabase **publishable** key in `.env.example`; it is a client key, not a service-role/secret key. Commit: `e98599198cff2dd45e5e1138763dc486ee0ffaa2`.
- Excluded the device-bound encrypted Supabase session preference file from cloud backup and device transfer so ciphertext is not restored without its Android Keystore key. Commits: `fb0ecd3d15abcc6118941903f78b46be9d6d6c61`, `cd2c81ce815d8db891d2b9c7b6bebfdb626e08d2`.
- **Important status:** this is only the REST/session-storage foundation. It is not injected into `DependencyProvider`; `AuthRepositoryImpl` still uses Firebase and Room. A follow-up attempt to add the coordinating auth client file was blocked by the platform's security checks and was not committed. No alternate route was used.
- **Not verified:** no Gradle build, KSP/Moshi adapter generation, unit tests, network auth requests, token refresh, email confirmation, recovery deep-link, or device test. Do not treat this slice as a working login replacement.
- **Next:** review the added DTO/API contracts, implement the coordinator and inject it into the repository with no local/Firebase fallback; account for email-confirmation signup (which may return no session) and the recovery deep-link/password-update flow before enabling those UI actions. Then run Gradle build/tests before proceeding to other repositories. Priority 0 remains open.

### 2026-11-05 — Supabase security approval and Android auth integration review
- User explicitly approved the database privilege/RLS hardening after its security impact was explained.
- **Approval executed:** migration `harden_public_table_grants_and_project_membership` applied and catalog-verified (see the preceding execution entry).
- **Android integration review:** current `AuthRepositoryImpl.kt` still depends on Firebase Auth/Firestore and has a Room-backed local account fallback; `DependencyProvider.kt` constructs Firebase clients; `CrashIntelligenceApp.onCreate()` still initializes Firebase. No Supabase Auth client is wired into Android yet.
- Existing Android stack includes Retrofit, OkHttp, Moshi, Kotlin serialization, coroutines, Android Keystore-backed `SecurityUtils.encryptSecret/decryptSecret`, and the Secrets Gradle Plugin. App currently targets minSdk 24 and Kotlin 2.2.10. These existing dependencies may support a REST-based Supabase integration without adding an SDK whose compatibility has not been proven.
- **Implementation constraint:** do not route authenticated traffic back to local Room or Firebase if Supabase configuration/auth fails. Fail closed, persist/refresh the Supabase session securely, and make email verification/recovery semantics explicit. Google sign-in needs a Supabase-compatible ID-token flow and must not silently continue through Firebase.
- **Next implementation slice:** implement the Supabase Auth REST contract/session persistence and inject it through `DependencyProvider`; test token parsing, secure persistence, refresh, sign-out and failure behavior before switching the Login flow. Only then migrate profile/project data and remove Firebase incrementally.
- **Not verified:** no Android code changed in this review; no Gradle build, tests, Supabase sign-up/login, or device run. Priority 0 remains open.

### 2026-11-05 — Supabase project membership and table privilege hardening
- **Database migration:** `harden_public_table_grants_and_project_membership` applied successfully to `hgfgbjomwhogpswpqusk`.
- Revoked all direct table privileges from `anon` across the `public` schema. Authenticated clients retain only SELECT/INSERT/UPDATE/DELETE table privileges, subject to RLS; elevated table privileges such as TRUNCATE, REFERENCES and TRIGGER are not granted.
- Restricted `projects` updates to the explicit mutable columns `name`, `description`, `data_retention_days`, and `pii_scrubbing_enabled`. Client updates cannot change `owner_id`.
- Restricted `project_members` updates to `role`, `user_email`, and `display_name`; client updates cannot change `user_id` or `project_id`.
- Replaced membership insert/update policy logic: project owners may appoint ADMIN/DEVELOPER/VIEWER; admins may only add/change DEVELOPER/VIEWER; no membership row may be assigned OWNER. The actual project owner remains represented by `projects.owner_id`.
- **Verification:** confirmed no direct anon SELECT/INSERT grants on projects/members; authenticated SELECT remains granted; column privilege checks confirmed authenticated clients cannot update project `owner_id` or membership `user_id`, while allowed project name and member role updates remain permitted. Confirmed RLS enabled on all 9 public tables. Supabase security advisor returned zero lints.
- **Limitations:** this verifies catalog privileges, policy definitions and advisor output, not a live two-user REST/API attack test. Real owner/admin/non-member test identities and end-to-end authorization tests remain required before production.
- **Android status:** Supabase is still not connected to the Android client; Firebase replacement, Gradle build and device tests remain pending. Priority 0 gate remains open.

### 2026-11-05 — Supabase client configuration and RLS security review
- **Commit:** `10d2840e7a9c0521733b412be929acbaabfe4f89`.
- **File:** `.env.example`.
- Added `SUPABASE_URL` for the dedicated Vibe Coding project and a publishable-key placeholder. The real `.env` remains ignored by Git. Explicitly documented that service-role/secret keys must never ship in the Android app.
- **Security review finding:** catalog inspection shows the `anon` role currently has broad table-level privileges on application tables (RLS still limits rows, but the grants are unnecessarily broad). The `project_members` insert/update policies permit role assignment paths that need stricter owner-vs-admin constraints. Project updates also need column-level protection so ordinary project admins cannot change `owner_id`.
- **Remediation status:** attempted a narrowly scoped privilege/policy hardening change through both SQL execution and migration application; both operations were blocked by platform security checks. No database permissions or policies were changed in this attempt. Do not bypass this control. Obtain the required explicit approval/path, then apply and verify the hardening before enabling client writes.
- **Build configuration note:** the app already applies the Secrets Gradle Plugin with `.env` and `.env.example`; adding placeholders does not prove that a local `.env` exists or that BuildConfig exposes the values correctly.
- **Not verified:** Android Supabase client, credential injection, build, runtime connectivity, sign-up, RLS API tests and two-user isolation. No Firebase components were removed.
- **Gate:** Priority 0 remains open. Screen work remains blocked.

### 2026-11-05 — Supabase foundation and Firebase inventory (in progress)
- **Supabase project:** `vibe-coding` / `hgfgbjomwhogpswpqusk`, region `eu-west-1`, status verified `ACTIVE_HEALTHY`.
- **Database migrations applied:** `initial_core_schema_and_rls` and `index_foreign_key_columns`.
- **Initial cloud schema:** `profiles`, `projects`, `project_members`, `applications`, `crashes`, `issues`, `audit_logs`. IDs for product entities remain text-compatible with existing Kotlin IDs; Auth identities use Supabase Auth UUIDs.
- **Security:** RLS enabled on every created public table; ownership/member/role-aware policies added; membership helper functions isolated in the non-exposed `private` schema, use a fixed search path, and have execute privileges revoked from PUBLIC; explicit authenticated-role table grants added because new projects no longer expose new tables automatically. Security advisor returned no lints after migration.
- **Performance review:** added indexes for foreign-key columns reported by the advisor. Remaining INFO-level unused-index notices are expected before application traffic exists; re-evaluate after real workloads.
- **Firebase inventory confirmed so far:** Firebase Auth + Firestore are wired through `AuthRepositoryImpl.kt`, `CrashRepositoryImpl.kt`, `AIModelTeamRepositoryImpl.kt`, `DependencyProvider.kt`, and `FirebaseUtil.kt`. Firestore currently stores user profiles, projects and related crash/application/integration/issue/audit/notification/team data. Firebase Auth is currently the identity authority where configured. The app also declares Firebase BOM, Firestore, Auth and Firebase AI dependencies in Gradle; exact use of Firebase AI and any generated Firebase resources still requires a full repository-wide reference scan.
- **Android migration status:** no Supabase Kotlin dependency or client has been added yet; no Android repository has been switched to Supabase. Firebase removal is NOT complete. Existing Room remains the local persistence layer.
- **Credential security correction:** removed a release-signing password fallback that was hard-coded in `app/build.gradle.kts`; release signing now requires environment-provided credentials. **Required operator action:** rotate the exposed Android upload/signing credentials (and reset the Play upload key if this key was ever used for a published app). Removing a secret from the current file does not remove it from Git history. Do not reuse the exposed password.
- **Verification:** re-fetched all seven tables with column/FK details; confirmed RLS is enabled on each. Security advisor returned no lints. A negative authorization probe executed as `authenticated` with an unrelated UUID returned `false` from `private.is_project_member` for an unowned project. This is a narrow policy-helper check, not a full two-user API/RLS integration test.
- **Not verified:** no Gradle build, Android unit/instrumentation test, emulator/device test, real Supabase sign-up/sign-in, two-user data-isolation test, or adversarial RLS test has run. Supabase schema and advisor checks are verified, but application behavior is not.
- **Next batch:** complete Firebase reference/config inventory; configure Supabase Auth email verification and password recovery; implement Supabase Kotlin client with persistent secure session storage; replace AuthRepository and then migrate project/crash/team repositories in small batches. Add explicit two-user/unauthenticated RLS tests before enabling production data flows.
- **Status:** backend foundation only; Login and all data screens remain [ ] Not audited/verified.


### 2026-11-05 — Supabase Auth repository wiring (integration in progress)
- **Commits:** `1516bb5a5d2fa7e45620c71fba4d992d9821a71b`, `c87fc3c00d8e9a61b7a2cefae0e31872e53ae169`, `9660a9581a515d0da95df837a2302fd8b0e12152`.
- Replaced the authentication repository's Firebase Auth / Firestore profile writes and Room local-account fallback with Supabase Auth REST calls for email/password signup and sign-in. DependencyProvider now builds the Supabase Retrofit API from `BuildConfig.SUPABASE_URL`, passes `BuildConfig.SUPABASE_PUBLISHABLE_KEY`, and creates the encrypted Supabase session store.
- Added persisted-session restoration and refresh-on-observation when the access token is within 60 seconds of expiry. If refresh fails, the local session is cleared and the current user becomes null (fail closed).
- Sign-out attempts the remote Supabase logout and always clears the local encrypted session.
- **Important incomplete flows:** signup responses without a session (email confirmation required) do not create a local signed-in state; the repository returns an explicit confirmation message. Password recovery intentionally returns unsupported because the existing UI contract asks for a new password before email ownership is verified and the app has no completed recovery callback/deep-link flow. Google sign-in intentionally returns unsupported until Supabase Google OAuth is configured and validated. No Firebase components were removed from the rest of the app; Firebase remains used by other repositories.
- **Verification:** source files re-fetched after each commit. No Gradle build, KSP generation, unit/instrumentation tests, network request, real signup/sign-in/refresh/sign-out, email confirmation, or device test was run. Workflow lookup for the latest commit returned no workflow runs.
- **Configuration assumption requiring build confirmation:** the wiring references Secrets Gradle Plugin generated `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_PUBLISHABLE_KEY`; actual generated BuildConfig fields have not been compiled/inspected.
- **Gate:** Supabase Auth code is wired in source but not verified operationally. Priority 0 remains open. Do not begin ordinary screen audits until auth and remaining Firebase data repositories are migrated and tested.

- **Follow-up hardening commit:** `fca39e7f15a5c1436d1dc6ca8f681ca5f6bdf23b` preserves the encrypted refresh token when refresh fails due to transient I/O or server errors; it clears the session only for rejected refresh credentials (HTTP 400/401/403). The signed-in state remains null until a later retry.
- **Migration impact:** existing Firebase identities are not automatically imported or linked to Supabase Auth. Existing Firebase users may need a new Supabase account until a verified identity/data migration path is implemented. This is a known migration gap, not a completed migration.

- **Test maintenance:** commit `38374002b5e5e821fadc31b46284f413dbfaf14d` updates the existing invalid-login negative-path test to instantiate the Supabase-backed repository with a failing API stub instead of the removed Firebase/Room constructor. The test has been edited but has NOT been executed.

- **Recovery UX safety:** commit `809831c5c3ca963e318c649700bca60640b7ace7` removes the misleading "new password" entry from the recovery mode and disables submission with an explicit notice until the email-verification/deep-link recovery flow is implemented. The repository continues to reject direct password replacement.

- **Identity model correctness:** commit `2b5041232f2c05265b280ec2ff14fc35cfd5b0f9` maps Supabase `created_at` into the app's `UserAccount.createdAt` rather than resetting the account creation timestamp whenever the session is restored.

- **Concurrency hardening:** commit `94d60baca5db989eac18c6bfa72eefccd37c71b9` serializes session refresh under a coroutine mutex so concurrent auth-state collectors cannot race refresh-token rotation.

### 2026-11-06 — Follow-up source inspection: remaining migration blockers

- **Auth contract gap:** `AuthRepository` still exposes `resetPassword(email, newPassword)`, while a safe Supabase recovery flow must be split into a request step (email only) and a completion step after verified recovery credentials are received. `SupabaseAuthApi` already declares recovery, OTP verification and password-update endpoints, but the repository does not expose or orchestrate them. The UI currently disables recovery; keep it disabled until the full verified flow exists.
- **Deep-link gap:** `AndroidManifest.xml` currently registers only the launcher intent filter for `MainActivity`; no verified Supabase email-confirmation/password-recovery callback route is declared. No redirect URL allow-list or email-template configuration has been verified in the Supabase project.
- **Firebase remains in active dependency injection:** `DependencyProvider` still constructs and injects Firebase Auth/Firestore into legacy repositories. Supabase Auth wiring is not equivalent to completing the application migration.
- **Critical identity-isolation defect remains:** `CrashRepositoryImpl.requireUserId()` still falls back to the shared literal `usr_dev_master` when Firebase identity is unavailable. Cloud operations must fail closed instead of assigning an unauthenticated caller a shared identity.
- **Team repository migration risk:** `AIModelTeamRepositoryImpl` still uses Firebase Auth/Firestore, has the same `usr_dev_master` fallback, and catches Firestore write failures while returning apparent success after updating only in-memory maps. This can report persistence success when no remote write occurred. Replace this with authenticated Supabase operations and explicit failure propagation; do not preserve silent local success as a cloud result.
- **Current scope decision:** next implementation work remains on the Supabase foundation and Firebase-backed repositories. No ordinary screen audit has started. Keep all screen audit checkboxes untouched until the Priority 0 gate is verified.
- **Verification status:** this entry is based on source inspection of the current `main` files only. No Gradle build, unit/instrumentation test, Supabase Auth request, deep-link round trip, multi-user RLS test, emulator or device run was performed in this pass. These findings are blockers, not completed acceptance criteria.

## Updated execution policy — parallel progress without bypassing the Supabase gate (2026-11-06)

The project must not stall while the Supabase migration is in progress. Work is split into parallel tracks; the Supabase gate still controls production cloud-data integration and ordinary screen audits, but it does NOT prohibit independent engineering work.

### Active parallel tracks

1. **Track A — Supabase foundation (continuous):** finish the verified Auth lifecycle, migrate Firebase-backed repositories in dependency-aware slices, eliminate shared fallback identities, validate RLS with multiple real identities, and remove Firebase only after replacements pass.
2. **Track B — independent application reliability:** inspect and fix build configuration, dependency wiring, lifecycle/concurrency defects, crash-prone code paths, persistence correctness, error propagation, and security issues that do not require changing the unfinished cloud data contract.
3. **Track C — diagnostic and AI execution integrity:** improve repository/domain-layer correctness and provider request handling; ensure every reported status is evidence-backed, failures remain visible, and unsupported Agent/live operations remain disabled. Do not represent local fixtures or in-memory state as persisted cloud success.
4. **Track D — engineering foundations and documentation:** improve test coverage, test fixtures, static checks, architecture boundaries and this task register. Record exact files/commits and distinguish source inspection from executed verification.

### Work allowed before the gate closes

- Fix confirmed bugs, security defects, crashes, race conditions and incorrect success/error reporting.
- Improve shared architecture, domain models, repository contracts, dependency injection and testability where changes do not assume an unverified Supabase behavior.
- Implement or strengthen local-only functionality when its persistence semantics are explicit and it does not masquerade as cloud synchronization.
- Add tests and documentation for existing behavior and migration boundaries.
- Continue backend/auth migration in parallel with the above.

### Work still gated

- Do not begin the ordinary 27-route screen audit, broad screen redesign queue, or mark any screen audited/verified until the Supabase acceptance criteria above are met.
- Do not enable cloud-dependent UI flows against incomplete repositories, remove Firebase prematurely, or claim multi-user isolation/build/runtime success without executed evidence.

### Operating rules for each implementation batch

- Select the highest-impact independently actionable defect or migration slice; do not spend a whole batch merely re-reading or re-documenting already-known blockers.
- Inspect the current main source and latest file SHA immediately before each edit. Re-fetch after writing and verify the resulting content/commit.
- Keep changes small and dependency-aware; do not edit the same file concurrently.
- Update the task register with implementation evidence, exact commits, remaining risks and explicit verification status.
- Never mark a task complete based only on code being written. Record build, tests, API checks and device validation only when actually executed.
- When a path is blocked by a platform security control, stop that operation and report the limitation; do not attempt to bypass it.

**Current operating decision:** continue Supabase migration while making measurable progress on independent reliability, architecture, diagnostic integrity and test foundations. The screen-audit gate remains unchanged.


## 2026-11-06 — Android dashboard visual refresh (source implementation)

- **Target:** `app/src/main/java/com/example/presentation/ui/screens/DashboardScreen.kt` on the Android Jetpack Compose app.
- **Visual direction:** dark cosmic background, restrained cyan/blue glow, technical grid and star points, translucent deep-blue panels with rounded borders, and a dark navigation surface. The implementation uses native Compose drawing and Material 3 components; it does not embed a static mockup or web UI.
- **Existing behavior preserved in source:** dashboard data remains bound to `DashboardViewModel`; project/issue/crash counts, recent crash list, notification sheet and read/clear actions, sign-out action, and existing navigation destinations remain wired to their existing callbacks/routes.
- **Implementation commits:** `c88526baad5a163df964a1f080abede7cb7224d0` (cosmic scaffold/backdrop/panel introduction), `61b1b6ec21b93c0b2ed5901c8aa01003c6c0dcab` (consistent panel styling), `a746f4b67a9cb937fbed2527abc7d9eccdb00bd7` (unused-import cleanup).
- **Verification status:** source was re-fetched after edits and the resulting content SHA is `74a084137eb146b42293f72f415314ba81c745c1`. No Gradle build, unit tests, screenshot capture, emulator run, or physical Android device validation was executed. Do not mark the dashboard audited or visually verified until those checks are performed.
- **Scope boundary:** this is a focused home-dashboard styling pass, not the gated 27-route screen audit. Supabase migration and its acceptance gate remain unchanged.


## 2026-11-06 — Index missing team execution project foreign key (Supabase migration applied)

- **Finding:** Supabase Performance Advisor reported `public.team_executions.project_id` foreign key without a covering index. Catalog inspection confirmed the table had indexes on `id`, `(team_id, started_at_ms DESC)`, and `(user_id, started_at_ms DESC)`, but not `project_id`.
- **Change:** added `supabase/migrations/20261105224519_index_team_executions_project_id.sql` with `CREATE INDEX IF NOT EXISTS team_executions_project_id_idx ON public.team_executions (project_id);`.
- **Commits:** initial migration source `0a2f6d80560335390aeb0eeb76fb6640b661efbb`; aligned filename to the Supabase-applied version in commit `8a119e94f0755e20f2786644fa89956d395a09f3`, then removed the mismatched duplicate path in commit `282693f561918ea4a90587052cebfbc117cf9fec`.
- **Backend verification:** Supabase `apply_migration` returned success; a subsequent `pg_indexes` query confirmed the index definition. The Performance Advisor no longer reports the `unindexed_foreign_keys` finding for this FK. It now reports 14 INFO-level unused indexes, including the new index; treat those as workload-dependent notices in this pre-traffic environment and do not remove the index solely on that basis.
- **Limitations:** no query-plan/real workload benchmark, Android build, test suite, or device validation was executed. The source filename now matches the version recorded by Supabase (`20261105224519`); no additional database migration was applied during the rename. The index change does not complete the Supabase cloud feature gate.


## 2026-11-06 — Publish Supabase auth state immediately after sign-in/sign-up (source + test added; execution unverified)

- **Defect identified:** `AuthRepositoryImpl.signIn()` and `signUp()` persisted the valid Supabase session and returned a `UserAccount`, but did not update the repository's `_currentUser` `MutableStateFlow`. Existing collectors (including a long-lived app auth observer) could therefore remain on the unauthenticated/null state after a successful login until a new collection triggered session restoration.
- **Fix:** after encrypted session persistence succeeds, both methods now immediately set `_currentUser.value` to the authenticated account. The state is published only after session persistence succeeds, so a local secure-storage failure does not create an apparent signed-in state.
- **Regression test:** added a Robolectric coroutine test with a successful Supabase sign-in stub. It keeps an auth-state collector active through sign-in and asserts both synchronous current-user state and emitted user identity. The test fixture does not call the live Supabase API.
- **Commits:** implementation `83f999b2e3b19a6a38173d60c5a1327b9e198088`; test `c0d6b5678d2a41b8c1a14fb4fd45672eae84ca44`; coroutine imports `ed2004ce4f0b05fe29215a94077ccf733ec52c0c`.
- **Source verification:** re-fetched `AuthRepositoryImpl.kt` and `ResilienceAndNegativePathTest.kt` from `main`; confirmed state assignments in both successful auth flows and the active-collector regression test.
- **Not verified:** no Gradle compile, Robolectric/unit test execution, live Supabase authentication, or device run was performed. The test depends on the Android secure-session storage implementation under Robolectric; execution will reveal whether the test environment supports the keystore-backed encryption path. The auth state fix does not address unfinished recovery/deep-link flows or the remaining Firebase-backed repositories.



## 2026-11-06 — Login screen: email-confirmation signup state (source change; verification pending)

- **Screen started:** route #1, `Login` (`LoginScreen.kt`). This is a focused functional slice within the screen task, not a claim that the full Identity/Auth gate or screen audit is complete.
- **Finding:** Supabase can accept an email-confirmation-required signup without returning a session. The repository previously represented that expected outcome as a generic `IllegalStateException`; `MainViewModel` then exposed it as a red error state, incorrectly conflating “check your inbox” with a rejected signup.
- **Changes:**
  - Added domain-level `EmailConfirmationRequiredException` to explicitly represent the accepted signup / no-session outcome.
  - `AuthRepositoryImpl.signUp()` now throws that typed outcome when a successful Supabase response has no valid session, with wording that says the request was accepted and email verification is required.
  - `MainViewModel.signUp()` recognizes the typed outcome and publishes it as a success/information message while returning `false` to the UI callback; the app therefore stays on Login and does not navigate into an authenticated workspace without a session.
  - Other signup failures remain surfaced as errors; signup with a returned valid session preserves its existing immediate-auth behavior.
- **Commits:** domain outcome `b4711e3ce7c807c2dbbfb524f1da78624d72a256`; repository classification `45f669da2def71dcee966820ed25c3999700c89a`; ViewModel presentation `6778b08dd1156965a93318e6fe9e3ea4cb630168`.
- **Source verification:** re-fetched all three changed source files from `main`; confirmed the typed exception is imported and used by repository and ViewModel, and the email-confirmation case sets `successMessage` without invoking authenticated navigation.
- **Not verified:** no Gradle compilation, unit/Robolectric test execution, real Supabase signup, confirmation-email round trip, emulator or device validation was run. No new automated regression test has yet been added for this ViewModel branch.
- **Remaining Login work:** verified Supabase recovery deep-link lifecycle, signup/email-confirm behavior with real provider configuration, session restore/account-switch/sign-out validation, UI/UX and accessibility review, tests and device validation. Keep inventory status [ ].


### Login follow-up — validation reachability and signup regression coverage (2026-11-06)

- **UI defect:** the primary sign-in/sign-up button was disabled until every field was already valid, while the inline validation messages were only produced by tapping that same button. Invalid forms therefore had no reachable submit action and could not show their intended field-level guidance.
- **UI change:** the primary action is now enabled whenever the form is not loading and the screen is not in the intentionally-disabled recovery mode. Tapping it runs the existing email/password/name/confirmation validation and surfaces the corresponding inline error. Recovery remains disabled with its explicit explanation until the verified callback flow is implemented.
- **Regression test added:** `ResilienceAndNegativePathTest` now stubs a successful Supabase signup response containing a user but no access/refresh tokens, then asserts the repository returns `EmailConfirmationRequiredException`, does not publish an authenticated user, and does not persist a session.
- **Commits:** Login validation reachability `384922f5a531bea98d7dd7d2f9e81b6f65c0abb8`; signup confirmation regression test `a28cc9aae3080996c316ad89a127c98c1b6a7ac8`.
- **Source verification:** both files were re-fetched from `main`; the test method and updated submit-enable condition are present.
- **Verification limits:** the new test has not been executed; no Gradle compile, UI test, emulator, device, live Supabase signup, email delivery or confirmation callback was run.
- **Agent instructions:** repository tree inspection found no root or nested `AGENTS.md` / `agents.md` instruction file. Agent implementation source exists under `app/src/main/java/com/example/data/agent/`; it is application code, not a contributor instruction document.
- **Login status:** still [ ] Not audited/verified. The source changes above are partial slices; full recovery/deep-link flow, real provider configuration, broader auth lifecycle tests, accessibility/visual validation and device verification remain open.



## 2026-11-06 — CI compiler failure: validation sandbox identity nullability (fix pushed; CI rerun pending)

- **CI evidence:** the three workflow runs for commit `25304589c62dcc1e975d51fb0ca11a99c5ea73b1` completed with failure: Android CI/CD run `42286441735`, Code Quality run `42286441768`, and APK run `42286441786`.
- **Root cause from job logs:** all three paths reached Kotlin compilation and failed in `TeamValidationViewModel.kt` because nullable `String?` user IDs were passed to validation-engine methods requiring non-null `String` at the single-test, Vibe sandbox and Crash sandbox call sites (reported around lines 99, 123 and 133). The lint workflow also stopped at the same compile task; it did not establish an independent lint defect. KSP additionally emitted an AWT EventQueue NPE during processing, but the explicit Kotlin type errors were the compilation failure reported by Gradle.
- **Fix:** `runSingleTest()`, `runVibeSandbox()` and `runCrashSandbox()` now all call `requireAuthenticatedUserId()` and return before launching work if no authenticated identity is available. This both resolves the nullable argument mismatch and preserves fail-closed behavior; the sandbox/test operations no longer pass a null identity into the engine.
- **Commit:** `dd9dd07730dc8869fc9113a39cc12be77aff5f64`.
- **Verification boundary:** source update succeeded. The three workflows were not yet rerun against this fix at the time of this note. No claim of compilation, tests, lint or APK success is made. The previous CI failures remain historical evidence until a new run completes.
- **Login remains in progress:** the above compile blocker was in a separate validation ViewModel and does not complete the Login screen. Continue the Login lifecycle, test and UX work only after the shared compile gate is rechecked.


## 2026-11-06 — Design-system specification and OpenRouter test fake alignment (source/docs; CI rerun unresolved)

- **Design documentation:** added `docs/DESIGN_SYSTEM.md` to record the Vibe Engineering visual direction, current shared color/shape/spacing tokens, existing typography, accessibility/layout rules, and the explicit migration boundary between the new Login-local tokens and the older Material 3 Engineering palette.
- **Important non-completion note:** `Theme.kt` still uses the legacy `EngDark*` / `EngLight*` palette. `EngCyan` is green (`#39D353)); no global palette migration or semantic color change was made because its usage audit is not yet conclusive. The new document describes the current state and target rules; it does not claim app-wide adoption.
- **Token source cleanup:** removed the unused Compose Color import and used the existing import for token constructors in `VibeDesignTokens.kt`.
- **CI finding:** the failed unit-test workflow for docs commit `d1b2c7b1becf371569e759b01b43cd8ff470f8cc` reached Kotlin test compilation and reported three outdated `OpenRouterApi` fakes. The interface now requires `getModels(token: String): OpenRouterModelsResponse`, while those test doubles still implemented a no-argument method.
- **Test source fixes:** updated the fake implementations in `AIFreeSystemValidationTest.kt`, `AIModelTeamsEndToEndValidationTest.kt` and `ResilienceAndNegativePathTest.kt` to match the current API contract. Commits: `87965a1713f03c82f41a8b0acee296c4d5763b57`, `063c6eefece40a7e2453b4d81471ea81b41d37bc`, `7c56573fe46828ccaade58243763d8aef5617ae1`.
- **Additional commits:** token cleanup `fe208dcb1f323095725dd5660719031cdd942dcb`; design-system specification `e8035985efa86df9b0f9c3cdcd76dab6058d4451`.
- **Verification status:** the first workflow run clearly identified the test fake signature mismatch. New workflow runs were triggered by the three test commits and also reported failure; the workflow-job log endpoint returned a missing log blob (404), so their failure cause is not yet established. Do not treat the signature edits as verified until a successful test/build run or inspectable logs confirm them.
- **Not verified:** no successful Gradle unit-test run, full APK build, lint completion, emulator/device run or rendered screenshot review has been established by this batch. Login remains open and visually unverified. App-wide Material theme migration remains pending.


## 2026-11-06 — CI diagnosis, shared dark-theme adoption and Login token pass (source changes; runner verification pending)

- **CI diagnosis refined:** subsequent workflow attempts on `062446077b44e77e380886f07ac018a03a5de935` and `49bb7cb319db404b96ed032fce091746879b6ffc` completed in roughly two seconds with empty job step arrays, `runner_id=0`, and no runner name. These runs did not execute checkout, Java setup, Gradle or Kotlin compilation. Their failure is an Actions job-start/runner allocation failure, not evidence of a source compilation failure. The earlier full CI log remains the only compiler evidence for the stale OpenRouter test fake signatures.
- **Test fake signature changes remain unverified:** the three fakes now accept `token: String` and return/throw against the current `OpenRouterModelsResponse` signature. A run with actual runner steps is still required.
- **Token source compile defect caught and corrected:** the token cleanup commit removed the Compose `Color` import but left the first constructor unqualified. Fixed `SpaceBackground` to use the fully qualified Compose Color constructor in commit `49bb7cb319db404b96ed032fce091746879b6ffc`. Do not treat this fix as build-verified yet.
- **Global dark Material theme:** `Theme.kt` now maps dark Material 3 roles to shared Vibe tokens (cyan/violet/success accents, cosmic background/panels, semantic text and error roles), while the light palette and opt-out from dynamic colors remain unchanged. Commit `b4bb792ba88765f13dc14f388960ff5ae0171f55`.
- **Login token adoption:** replaced repeated panel/control radii, primary text, success/error accents, field surface/focus colors and primary action color with shared Vibe tokens. Existing authentication callbacks and recovery-disabled behavior were preserved. Commit `b867142f7a4e3da668ab7ac07115555867b90b9a`.
- **Remaining design debt:** Login still needs a full rendered review, keyboard/focus behavior inspection, field-level accessibility/error-state review and further visual hierarchy refinement. No screen is being marked complete.
- **Verification:** source changes are committed. GitHub workflow attempts are failing before runner startup; no new build, unit test, lint, APK artifact, emulator/device validation or screenshot has been established. Do not claim these passed.


- **Latest CI recheck (commit `e4755180e822e2091b7b6b540418913699858e3f`):** workflows `42289688006` (unit tests/build), `42289688184` (APK), and `42289688432` (lint) again terminated before any job steps were recorded. Earlier attempts were re-run via GitHub Actions and showed the same pre-runner failure. Job logs are unavailable because no runner produced log output. This confirms the Actions execution blocker persists independently of the Kotlin source commits; it does not identify the underlying GitHub runner-service cause.
- **Latest source spot-check:** re-fetched the token file, Material theme, Login screen and all three test files from `main`. Confirmed the background token uses a fully qualified Compose Color constructor, dark Material roles use shared Vibe tokens, Login uses shared panel/control tokens while retaining real sign-in and disabled recovery, and each fake implements the token-bearing OpenRouter signature. These are source checks only, not compilation.

## 2026-11-06 — Shared Engineering Compose components aligned with Vibe tokens (source change; verification pending)

- **Scope:** `app/src/main/java/com/example/ui/components/EngineeringComponents.kt`; shared reusable components only. This is not a screen audit or screen-completion claim.
- **Findings:** `EngCard`, `EngButton`, `TerminalPanel` and `SectionHeader` still used the legacy 8/4 dp geometry and hard-coded legacy GitHub-style colors, creating visual drift from the shared Vibe token palette.
- **Changes:** `EngCard` now uses the shared cosmic panel/grid tokens, panel radius and spacing; `EngButton` uses the shared cyan/control-radius tokens and explicit disabled colors; `TerminalPanel` uses the shared background/panel/text/success tokens and constrains long logs to a 240 dp vertically scrollable LTR region; `SectionHeader` uses shared grid/cyan accents and spacing.
- **Commit:** `efc3c42c751acf170f637b6e508d60856bae110e`.
- **Source verification:** re-fetched the edited component file after commit; exact content matched the intended update (blob SHA `4a2196fa3031a95511a2f530c40ba278c89cce67`).
- **Compatibility intent:** public composable names, parameters, and callback signatures were preserved. The log viewport is now bounded and scrollable; no fake log entries or new backend behavior were introduced.
- **Not verified:** no Gradle compilation, unit/UI tests, lint, screenshot, emulator or physical-device run was executed. Runtime layout impact across consuming screens remains unreviewed; this shared-component change must be checked in a rendered build before those screens can be audited.

- **Post-change CI recheck (2026-11-06, docs commit `9cc92a8c7e6fb11253ae6769a1df59816e9b676d`):** GitHub Actions created three runs (`42290810927` Android CI/CD, `42290810920` Code Quality, `42290810919` APK). All completed as failures. Their corresponding jobs (`128143438270`, `128143438363`, `128143438484`) expose `steps: null` and no runner metadata, consistent with the previously observed pre-step runner-start failure. No build/test/lint/APK result can be inferred from these attempts.

 
## 2026-11-07 — Supabase Auth coroutine-cancellation safety (source + regression test; execution blocked)

- **Task selection:** continued the highest-priority Identity and account slice. The existing Supabase Auth REST integration is present, but lifecycle correctness and runtime verification remain open. No root or nested AGENTS.md / agents.md instruction file exists in the current repository tree; the app/src/main/java/com/example/data/agent/ directory is product implementation code, not agent-contributor instructions.
- **Finding:** runCatching around suspend signup/sign-in requests converted CancellationException into an ordinary Result.failure, which can break structured concurrency and make a cancelled screen/request look like an authentication error. Session refresh also caught generic Exception without rethrowing cancellation, and remote sign-out revocation used runCatching.
- **Changes:** AuthRepositoryImpl.kt now uses a suspend result wrapper that returns failures for ordinary exceptions but rethrows coroutine cancellation. Refresh explicitly propagates cancellation. Sign-out still clears local credentials in finally, but no longer suppresses cancellation while attempting best-effort remote revocation.
- **Regression coverage:** added a test to ResilienceAndNegativePathTest.kt whose fake Supabase password-grant endpoint throws a specific CancellationException; the assertion requires that exact cancellation instance to propagate rather than being returned as an authentication failure.
- **Commits:** repository fix 790e9e3b2550736f76504e067750c13084456db5; regression test 010c4fb7ddae07ba445102523326fe14ea0e66d1.
- **Source verification:** both changed files were re-fetched from main. Repository blob SHA: 14777a1aec15716039332a975a9727ea469479a1; test blob SHA: bd6f24bbc709d9d29ae6751e1d7a895df2f27b0f. Commit diffs show only the described source and test additions.
- **CI result:** all three workflows triggered for the test commit failed before exposing any job steps: Android CI/CD run 42302842732, Code Quality run 42302842726, and APK run 42302842708. The corresponding job records returned steps: null; this provides no evidence that checkout, Gradle, compilation, tests, lint or APK packaging ran. The test is added but not executed.
- **Still open:** execute the targeted regression test and full build when a GitHub Actions runner is available; finish verified email-recovery/deep-link and account lifecycle flows; validate refresh/sign-out/account switching with real Supabase and Android runtime; complete Login's rendered Arabic RTL / English LTR, keyboard, accessibility and device review. Login remains unchecked and incomplete. No runtime, screenshot or successful build/test claim is made.

## 2026-11-07 — Fix Assistant loading and repository-action failure handling (source + regression tests; execution blocked)

- **Independent review of the preceding agent batch:** reviewed the diffs for AuthRepositoryImpl.kt commit 790e9e3b2550736f76504e067750c13084456db5, the cancellation regression test commit 010c4fb7ddae07ba445102523326fe14ea0e66d1, and this register update. The source change rethrows CancellationException from signup/sign-in, refresh and best-effort remote sign-out; the test injects a specific cancellation instance from the password-grant fake and asserts that it propagates. The diffs match the documented scope. No files from that auth batch were edited in this run.
- **Contributor instructions:** checked the repository tree and the common root, docs/, and .github/ AGENTS file paths. No AGENTS.md / agents.md contributor-instruction file is present; the operational constraints in this task register remain applicable.
- **Finding:** FixAssistantViewModel.loadCrashAndDiagnosis() used first() without handling an empty flow or null record, and left isLoading true unless the verification collector emitted. Lookup/diagnosis and child-flow exceptions were not surfaced consistently. Generation, PR creation and verification could also leave their busy flags active if a repository implementation threw before returning its Result.
- **Changes:** FixAssistantViewModel.kt now cancels prior crash/suggestion/verification jobs when a new crash is loaded; handles empty and null crash lookups; clears loading on missing records and exceptions; preserves coroutine cancellation; reports missing prerequisites instead of silently returning; and catches unexpected repository exceptions while resetting action flags. A successfully returned fix suggestion is immediately reflected in UI state and de-duplicated by ID.
- **Regression coverage:** added FixAssistantViewModelTest.kt for an empty crash flow, an explicit null crash record, and an I/O failure during lookup. These tests assert loading ends, no crash is fabricated, and an actionable error is exposed.
- **Commits:** ViewModel hardening 810bb862557cdfcd6fcefa11124662c2e54133fc; regression tests 77fa64d3d19f1c49c29f5846f81c85c3d7d7f337; unused test-import cleanup f39d51965d77a5cbbf5203c689d86ad37f7f2e47.
- **Source verification:** re-fetched the ViewModel (blob SHA 055aad646023aeb753e87c776430285584ebee16) and test (blob SHA 9bb5e3638b4c4bc7ed275672d03d01649b5f67d9) from main. Commit diffs were inspected and contain only the described ViewModel/test changes.
- **CI result:** workflows for the latest test commit f39d51965d77a5cbbf5203c689d86ad37f7f2e47 were Android CI/CD run 42304555450, Code Quality run 42304555265, and APK run 42304555247; all concluded failure. Their jobs expose steps: null; fetching the latest job log returned BlobNotFound. These runs did not provide evidence that a runner executed checkout, Gradle, compilation, tests, lint or APK packaging. The new tests are added but not executed.
- **Limitations:** FixAssistantScreen.kt was not redesigned or visually verified in this reliability patch; its existing error text binding can surface the new ViewModel message, but its no-suggestions prompt remains visible when a crash record is absent. Keep the route unaudited and incomplete. No crash-data Supabase migration, live backend request, emulator/device run or screenshot is claimed. The preceding auth batch also leaves local credential clearing in signOut() after the awaited remote revocation attempt; consider clear-first local logout as a separate auth change rather than overlapping this batch.

## 2026-11-08 — Supabase Auth local-first sign-out and session serialization (source + regression tests; execution blocked)

- **Task selection:** re-read the current priority register and latest commits. The immediately preceding agent batch was Fix Assistant loading/action reliability (4034dbb3fbfb6f7ebd6601902fb85a2485527eb8); this change is a separate Identity/account lifecycle slice explicitly called out in that batch's remaining notes. No root, docs/, or app/ AGENTS.md / agents.md contributor instructions were found; the register's operational constraints remain in force.
- **Finding:** AuthRepositoryImpl.signOut() retained the encrypted local session and in-memory current user until the remote Supabase revocation request returned or threw. This could leave local identity state exposed during a slow/cancelled logout. Session refresh and concurrent sign-in/sign-up/logout writes also did not share one lifecycle lock.
- **Changes:** renamed the repository mutex to sessionMutex and serialized password sign-in, sign-up session persistence, refresh, and sign-out lifecycle work. Sign-out now clears the encrypted local session and publishes currentUser = null before attempting remote revocation. Remote revocation remains best-effort for ordinary errors; CancellationException is still propagated after local logout has completed. No new auth capability or successful remote result is fabricated.
- **Regression coverage:** added two tests to ResilienceAndNegativePathTest.kt: one verifies local identity is already cleared when remote revocation throws cancellation and that the same cancellation propagates; the other verifies an ordinary remote revocation I/O failure does not restore or retain the local session.
- **Commits:** implementation ccabf3c202cc9ca06c7620628fef16f9c181dd82; tests 051ae3ae3566e85ba6798d4c62466c571484ee2c.
- **Source verification:** re-fetched the implementation from main (blob SHA af71db2deca6f3da8b7cae464f79cb786b79d0bb) and confirmed sign-in/sign-up/refresh/sign-out use the shared mutex, local session and user state are cleared before api.signOut, and cancellation is rethrown. Re-fetched the test file (blob SHA 393e2cdedb7b41b32503e2786591790fd60440a4) and confirmed both regression cases and their pre-revocation state assertions are present. Recent commit search confirms both source and test commits are at the head of main.
- **Build/test/CI status:** the regression tests were added but not executed. The available runtime has Java but no Gradle executable, Gradle wrapper checkout, or local repository working tree. The latest documented CI runs before this batch—Android CI/CD 42304555450, Code Quality 42304555265, and APK 42304555247—all failed with job steps null and no runner logs, so they provide no compilation or test evidence. GitHub's commit-workflow-run lookup returned no runs for the new test commit (the connector lookup is PR-run filtered). No successful build, unit test, lint, APK, emulator/device run, or rendered Login visual review is claimed.
- **Still open:** execute the two tests and full Android build when a functioning runner/local Gradle environment is available; validate logout/session refresh/account switching against real Supabase and Android runtime; complete verified password recovery/deep-link and Login RTL/LTR, IME, accessibility and rendered visual review. Identity/account and Login remain unchecked and incomplete.


## 2026-11-08 — Fix Assistant verification integrity, error isolation, and local-first auth race (source changes; runtime/build verification blocked)

### Independent review of the preceding agent's batch

- Re-read the current task priorities and recent main history. No root or nested AGENTS.md / agents.md instruction file was found; the app/src/main/java/com/example/data/agent/ directory is application code.
- Reviewed the Login validation-reachability change (384922f5) and signup email-confirmation regression test (a28cc9aa). The primary action is now reachable for sign-in/sign-up when not loading; its click handler validates email, password, display name and password confirmation before calling the repository. Recovery remains deliberately disabled until its verified callback/deep-link flow exists. This is a partial Login slice, not screen completion.
- Reviewed the prior local-first Supabase sign-out change. It cleared the local session before remote revocation, but only after acquiring sessionMutex; an in-flight token refresh held that mutex across its network request and could delay local logout. This was corrected in this batch with a session generation guard and a separate state lock: sign-out invalidates in-flight auth requests and clears local state before waiting for the network-operation mutex, auth/refresh responses check the generation before persistence, and sign-out clears again under the mutex so a queued sign-in cannot win the logout race. Remote revocation remains best-effort and cancellation is preserved.
- Reviewed the preceding Fix Assistant ViewModel resilience changes. A confirmed state-model defect remained: diagnosis-load and suggestions/history-flow failures were placed in the same error channel as generation/PR failures. A diagnosis error could therefore be presented as “generate a fix again” even though no diagnosis existed. The error channels and screen state mapping were separated below.

### Fix Assistant / Fix History changes

- FixAssistantScreen.kt: applied a native Compose Vibe/cyber visual refresh (shared tokens, grid/glow backdrop, diagnosis/evidence and crash metadata panels, structured loading/missing/load-error/diagnosis-missing/empty/generation-error/fix-list states). Copy-code now reports through a snackbar; GitHub PR links are host/path validated; PR creation is disabled without a nonblank authenticated user ID. The verification action is disabled with an explicit unavailable state rather than opening a simulated sandbox dialog.
- FixHistoryScreen.kt: removed the simulated re-verification dialog and disabled re-verification. All historical verification badges are neutralized as unverified; stored comparison summaries, test metrics and new stack traces are not presented as trusted results. The original crash stack trace remains available. PR links are validated before opening. Vibe token styling is applied to the history, filters, cards and empty state.
- FixAssistantViewModel.kt: separated loadError, suggestionsLoadError, and verificationsLoadError from generation/operation error; preserved cancellation; reports missing crash records and diagnosis failures as load errors; keeps already loaded suggestions visible if a later suggestions-flow error occurs. Initial UI state starts in loading to avoid a one-frame missing-crash flash.
- CrashRepositoryImpl.kt: verifyFix now returns an explicit UnsupportedOperationException until a real runner is connected. It no longer creates synthetic sessions, test events, mock stack traces, verification records, audit logs, or success notifications. Fix generation no longer attaches fabricated PASSED compilation/static-analysis/unit-test results; testResults is null. Fix generation and PR creation preserve coroutine cancellation.
- SeedData.kt: sandbox fix/history fixtures are labeled inconclusive, have no sample PR URL/commit/session IDs, no synthetic test results, and no claimed verified/reproduced outcome.
- ProductQualityGateEngine.kt: Fix Assistant and Fix History are explicitly BLOCKED until real verification operations and evidence exist; test execution and rendered design review remain unsatisfied. The release blocker wording now describes unavailable/simulated core operations rather than claiming every interaction is cosmetic.

### Regression coverage added or updated

- IntegrationWorkflowTest.kt: asserts verifyFix fails with UnsupportedOperationException, no verification record is persisted, and no VERIFICATION_PASSED notification is emitted.
- FixAssistantViewModelTest.kt: covers empty/missing crash, lookup failure, diagnosis failure, suggestions-flow failure and verification-history-flow failure, including separation from generation errors.
- FixAssistantScreenStateTest.kt: covers initial/loading, missing crash, load errors, diagnosis missing, empty generation state, generation error, populated suggestions and non-blocking history errors.
- ProductQualityGateEngineTest.kt: asserts both verification screens remain blocked and the overall verdict is blocked while a real runner is unavailable.
- AuthRepositorySignOutRaceTest.kt: holds a refresh request in flight, starts sign-out, asserts local session/current-user are cleared before releasing refresh, then asserts the refresh cannot persist a new session and remote revocation observes cleared local state.
- English and Arabic resources were updated for unavailable verification, unverified legacy records, load errors, and test-result provenance. Source-level resource checks found no missing referenced IDs or duplicate IDs in either locale.

### Source changes and key commits

- Fix Assistant / Fix History UI and truthful verification behavior: cb3b903d, 41ac3bf1, d84eeebc, 893238b5, 3237be99.
- Quality-gate blocking and regression coverage: ed6be8f7, ad696b53, 473e59a4, f16269e8.
- Fix Assistant error isolation and tests: be896f46, 0f5d682f, 11ce3940, 2a676cdb, 821a632a, 3e2a3175, 0f70c1bf.
- Auth refresh/logout race correction and test: fc8cdfc3, 92551afe, c0517445.

### Verification limits and remaining work

- GitHub Actions for code commit c051744533b992777bc493ca471562ba92027d08: Android CI/CD run 42424532968, APK run 42424532922, and Code Quality run 42424533018 all concluded failure. Their jobs report runner_id = 0 and steps = []; the connector could not retrieve job logs. These runs do not evidence Gradle compilation, unit tests, lint, or APK packaging.
- The new/updated tests have not been executed. No Android runtime, emulator/device, screenshot review, RTL/LTR rendered review, live Supabase Auth request, email confirmation/deep-link callback, or GitHub PR creation was tested. Source inspection/resource consistency checks are not substitutes for those validations.
- Fix Assistant and Fix History remain open/blocked: no real verification runner is integrated and the mandatory rendered UI/accessibility review is outstanding. Do not mark either screen complete.
- Next backlog priority remains Identity and account — Login / profile/session flows, including verified recovery/deep-link configuration, session restoration/refresh/account switching tests, and a full same-slice visual/interaction review. The broader Supabase/Firebase migration and Android CI runner availability remain separate blockers.


## 2026-11-08 — Login field validation, accessibility semantics, and keyboard/focus flow (source + regression tests; Android execution blocked)

### Independent review of the preceding agent batch

- Re-read the current task register and latest main history before selecting this slice. The preceding agent's latest batch changed FixAssistant/FixHistory UI and verification behavior, CrashRepository verification handling, ProductQualityGate evidence, and Supabase auth refresh/logout race handling. I re-fetched and inspected the relevant diffs and current source; no LoginScreen.kt or LoginFormValidationTest.kt file was part of that batch. I did not edit files changed by that batch.
- Review confirmed that FixAssistant/FixHistory now avoid presenting synthetic verification as a real test result: verification is disabled without a real runner, CrashRepository.verifyFix returns UnsupportedOperationException, and legacy verification records are neutralized as unverified. The Supabase auth source uses a generation guard for in-flight refresh/logout state. These source changes remain runtime/build-unverified.
- Review follow-up, intentionally deferred to avoid overlapping files changed by the preceding agent: FixAssistant's content-state classifier checks suggestionsLoadError before the operation/generation error when no suggestions exist. If a generation request fails while a suggestions-stream error remains set, the generation error/retry state can be masked as a load error. Revisit in a separate change to FixAssistantScreen.kt / FixAssistantViewModel.kt.
- CI recheck for the preceding batch: the latest main docs commit 27198e5b1df5aaa900b8f64e315809be50ec2089 had Android CI/CD run 42424714302, Code Quality run 42424714301, and APK run 42424714280 fail. A rerun was submitted for all three; each rerun still failed with no steps exposed. Job log downloads returned BlobNotFound, so no Gradle/compile/test/lint/APK execution is evidenced.

### Login finding and changes

- Finding: the form only displayed a top-level validation message; invalid fields had no field-level error semantics/supporting text and the keyboard did not route focus through the form. More importantly, inputs remained editable while authentication was loading, and their onValueChange handlers called MainViewModel.clearMessages(), which resets AuthActionState including isLoading. That could prematurely re-enable the submit action during an in-flight request.
- LoginScreen.kt now has a field-aware validation result (email, password, developer name, confirmation, or form-level unavailable recovery), inline supporting error text, Material error styling, and explicit accessibility error semantics. Submitting focuses the first invalid field.
- Added IME/focus order for sign-up: developer name → optional organization → email → password → confirmation. Sign-in routes email → password → Done. Valid submit clears focus and uses the existing real MainViewModel sign-in/sign-up callbacks; no auth success is fabricated.
- Disabled editable fields, password visibility, and auth-mode switching while actionState.isLoading is true so edits cannot clear the loading state or enable a duplicate submission. Verified password recovery remains disabled until its verified email/deep-link flow is implemented.
- Commits: LoginScreen.kt bfbfba2c89c464773389c3695022347b2e24ae95; regression test LoginFormValidationTest.kt 558d6d311905953b8c197af80f75723b1a26e60f.
- Source verification: re-fetched LoginScreen.kt from main (blob SHA 257b1e601f60f85d2dade0b444a21ef95836731d) and the test (blob SHA 3560959ce637ed127b29603cf6d274221c79b086). The source diff was inspected; the old inline emailValid/passwordsMatch checks are absent and the new validator, field error semantics, focus routing, and loading disables are present.
- Regression coverage: eight Robolectric test cases cover invalid email, blank password, signup name requirement, minimum signup password length, password confirmation mismatch, valid sign-in with trimmed email, valid signup, and explicitly unavailable recovery. The tests were added but not executed under Gradle/Robolectric.
- Local isolated Kotlin microcheck: 8 validator assertions passed with kotlinc using a minimal Android Patterns stub. This verifies the isolated Kotlin validation logic only; it is not an Android SDK, Robolectric, Compose, or app-build result.

### Build, test, and CI status

- No Gradle executable, Gradle wrapper checkout, or Android SDK is available in the local runtime. No Android build, Robolectric test execution, lint, emulator/device run, screenshot review, RTL/LTR rendered review, live Supabase Auth request, email delivery, or recovery deep-link callback was run.
- GitHub Actions for source commit bfbfba2c89c464773389c3695022347b2e24ae95: Android CI/CD run 42425431013, Code Quality run 42425431045, and APK run 42425430998 all failed before exposing any job steps.
- GitHub Actions for test commit 558d6d311905953b8c197af80f75723b1a26e60f: Android CI/CD run 42425453878, Code Quality run 42425453868, and APK run 42425453859 all failed. I submitted a rerun of the failed jobs for all three; attempt 2 also failed with empty step arrays. The job-log endpoint returned BlobNotFound for all three latest job IDs (128562764016, 128562766886, 128562771251). These runs provide no evidence that checkout, Gradle, compilation, unit tests, lint, or APK packaging executed.
- Login remains [ ] unaudited/incomplete. Remaining: verified recovery/deep-link and password-update flow; session restoration/refresh/account-switch runtime tests; full rendered Arabic RTL / English LTR, small-screen, IME and accessibility review; and successful Android build/test evidence. No screen-completion claim is made.
- Next independent candidates: review FixHistoryViewModel collector lifecycle/loading-error handling (the screen currently launches loadData both in ViewModel init and in LaunchedEffect; error UI is still not surfaced), and later address the deferred FixAssistant generation-error masking once the prior screen/ViewModel batch is no longer overlapping.


### CI recheck after task-register commit b2379aad00773a29328cb5826e427788474bdfb6

- Docs commit workflows: Android CI/CD run 42425558085, Code Quality run 42425558106, and APK run 42425557955 all failed. I reran the failed jobs for all three (attempt 2); the same pre-step failure persisted. Latest jobs expose empty step arrays, and each log download returned BlobNotFound (job IDs 128563024551, 128563017970, 128563029491). No Gradle/build/test/lint/APK execution is evidenced. The runner-start/log availability blocker remains unresolved.

## 2026-11-08 — Fix History collector lifecycle and actionable load failure (source + regression tests; Android execution blocked)

### Selected task and scope
- Selected the independent Fix History ViewModel loading/error lifecycle follow-up listed after the preceding Login and Fix Assistant batches. Latest main, the task register and recent commits were inspected first; no competing commit had touched the FixHistoryViewModel or FixHistoryScreen paths during this slice.
- Read repository instruction candidates at root, docs/, and app/; no AGENTS.md / agents.md file was found in those locations.

### Changes
- FixHistoryViewModel.kt: removed constructor-time init { loadData() } so the screen's LaunchedEffect(projectId) owns initial loading and does not duplicate the first subscription.
- Replaced accumulating verification collectors with one cancellable Job; retry/project selection cancels the previous stream. Added a generation guard so late emissions/errors from an older request cannot overwrite the currently selected project state.
- Clear stale verification rows when changing project; preserve current rows for same-project retry. Clear load errors on retry/emission, expose stream failures, preserve coroutine cancellation, and stop loading when a source completes without emitting.
- FixHistoryScreen.kt: added a Vibe-styled, accessible error panel with a retry action. When refresh fails but prior rows remain, the error is shown alongside them; when no rows exist, the panel replaces the empty-state message so a load failure is not misreported as an empty history.
- Added app/src/test/java/com/example/FixHistoryViewModelTest.kt: covers no eager fetch on ViewModel construction, stream failure clears loading and retry recovers, project switching cancels the prior collector, and an empty-completing stream does not leave a spinner active.
- Post-write re-fetch caught a malformed intermediate ViewModel patch boundary; the complete ViewModel file was reconstructed and re-fetched before proceeding. The final source is the corrected version in commit 4aedeacc.

### Commits
- ViewModel lifecycle/error handling: adca7276374d42f7cc5e4e3aaa216000bb510959
- Screen error/retry UI: 1e481f9f0a85027499f2194dacc27352b0d9ca1c
- Regression tests: 5fac0e73261657c7afd631e7233865f3d4fce063
- Existing-token correction in error UI: 32c030b8b6392571fc506122728307101f1427f8
- Corrected complete ViewModel source: 4aedeacc892b02cd72216e1366a80ac64321ba2c

### Verification and remaining work
- Re-fetched the final ViewModel, screen, test file and commit diffs from main; verified source boundaries, single collector ownership, generation guard, cancellation handling, retry UI and regression tests are present.
- GitHub combined-status calls returned no status entries for these commits; the commit-workflow-run wrapper returned no runs (it only exposes PR-triggered runs). This is not evidence of successful CI or test execution.
- The latest documented Android CI/CD, APK and Code Quality jobs continue to fail before exposing runner steps/logs; no Gradle compilation, unit test, lint, APK packaging, emulator/device run, screenshot review, or RTL/LTR rendered review was executed in this batch.
- Fix History remains open: run FixHistoryViewModelTest and app build under a functioning Gradle/Android runner, review rendered error/retry and empty/loading states on device, and verify Arabic RTL / English LTR accessibility. No screen-completion claim is made.
- Additional isolated check: compiled the final FixHistoryViewModel logic with the locally available kotlinc and minimal lifecycle/domain/repository stubs, then ran a coroutine harness covering failed-stream error clearing + retry recovery, cancellation of a previous project collector, and an empty-completing stream. Output: PASS. This is a Kotlin syntax/logic microcheck only; it is not the Android Gradle/Robolectric test task, app compilation, Compose compilation, or runtime verification.

## 2026-11-09 — Fix Assistant generation-error precedence (source + regression test; Android execution blocked)

### Independent review of the preceding agent's Fix History slice
- Re-read the current task register and compared `a1d97ce6` to `73ce4f9b`: the preceding batch changed only `FixHistoryViewModel.kt`, `FixHistoryScreen.kt`, `FixHistoryViewModelTest.kt`, and this task register (7 commits total). I re-fetched and inspected the final ViewModel, screen and test source; no blocking defect was confirmed in those changed files, and I did not edit them.
- Repository instruction candidates were checked at root, docs/, app/, and the app/src/main + app/src/test paths relevant to this work; no AGENTS.md/agents.md instructions were present.
- GitHub Actions for the preceding agent's final docs commit `73ce4f9b`: APK run `42529638874`, Android CI/CD run `42529638908`, and Code Quality run `42529638927` all concluded failure. Their jobs exposed `steps: null`; log retrieval returned `BlobNotFound`. No Gradle/test/lint execution is evidenced.

### Confirmed defect and correction
- `FixAssistantScreen.kt` state derivation checked `suggestionsLoadError` before the operation error `uiState.error`. If the suggestions stream had already failed and a subsequent fix-generation request also failed, `FixAssistantViewModel.generateFix()` retained the stream error while setting the generation error. With no suggestions, the screen consequently selected the generic load-error state, masking the generation failure and its generation-retry action.
- Reordered the classifier so, after load/crash and existing-suggestion checks, the current operation error is surfaced as `GenerationError` before a suggestions-stream error. When there is no operation error, the suggestions-stream failure still appears as `LoadError`; existing suggestions continue to remain visible and ready.
- Added a regression test for the combined state (suggestions stream offline + model generation failed), expecting the actionable generation error. Existing classifier tests still cover stream-only failure and existing suggestions.
- Commits: classifier fix `1c52f1c1b46623de8f2961f632b3f15a4c23c88a`; regression test `e7686a3ae6ccc308bbe34bbe98d96987993aeb2c`.

### Verification and limitations
- Re-fetched the changed files from main after writes: `FixAssistantScreen.kt` blob SHA `69788f03edcce8da1260ce93e4282adc42cd5ea3`; `FixAssistantScreenStateTest.kt` blob SHA `3b4ba290418a9d002a87e7a235398ebadf33a897`. Compared the two-commit diff from `73ce4f9b`; only those two source/test files changed in this slice.
- Isolated Kotlin classifier microcheck compiled with the available `kotlinc` and passed 3 assertions: generation error wins the combined state; stream-only error remains a load error; existing suggestions remain ready. This is not an Android/Compose compilation or JUnit/Robolectric test execution.
- GitHub Actions for test commit `e7686a3a`: APK run `42532366403`, Code Quality run `42532366475`, and Android CI/CD run `42532366529` all concluded failure. Each job exposed an empty step array; log retrieval returned `BlobNotFound`. No Gradle, app compilation, JUnit, lint, or APK execution is evidenced. The local runtime has no Gradle executable or Android SDK configured.
- No screenshot, emulator/device, RTL/LTR rendered review, or live backend operation was performed. Fix Assistant remains open under the design gate; this is a targeted state/interaction bug fix, not screen-completion sign-off.

### CI recheck after the task-register commit
- Workflows for documentation commit `116961d1`: APK run `42532434257`, Android CI/CD run `42532434316`, and Code Quality run `42532434231` all concluded failure. Each job returned an empty step array and its job-log download returned `BlobNotFound`. The docs-only run does not validate app compilation or tests; the runner/log availability blocker remains unresolved.


## 2026-11-09 — Fail-closed Vibe Studio and Crash Detail identity paths (source + regression tests; Android execution blocked)

### Selected prerequisite and scope
- Continued the Priority 0 shared identity-hardening prerequisite. The latest main, task register, recent commits, and relevant repository instructions were checked before editing. No AGENTS.md / agents.md was present at the repository root or in .github/, docs/, or app/.
- The preceding batch addressed Fix Assistant error precedence. This batch selected independent Vibe Studio and Crash Detail identity paths; it did not edit the preceding agent's Fix History or Fix Assistant files.

### Changes
- VibeStudioViewModel.kt:
  - Added authenticatedUserIdOrNull, which rejects missing, empty, or whitespace-only identities.
  - Removed the "anonymous" and "local_user" fallbacks from workspace selection/model lookup, workspace creation and generation, agent chat execution, plan execution, app requirement analysis/build, and GitHub push/pull/PR operations. Each operation now stops before the repository call and records an explicit sign-in error when no authenticated ID exists.
  - Replaced separate nested account collectors with one flatMapLatest account-scoped workspace/team stream. Account changes and sign-out clear the prior account's workspace/team UI state and cancel the previous account's combined stream.
- VibeStudioScreen.kt: added a dismissible Vibe-styled error/status panel so authentication failures and other ViewModel errors are visible in the workspace selector and editor.
- CrashDetailViewModel.kt: removed the "user_default" investigation identity. Team investigation now derives the ID from AuthRepository, fails closed when authentication or the team orchestrator is unavailable, and rethrows coroutine cancellation instead of treating it as an ordinary failure.
- CrashDetailScreen.kt: replaced the plain error line with an accessible error-status panel.
- Added app/src/test/java/com/example/VibeStudioIdentityGuardTest.kt with three cases: missing user, blank/whitespace ID, and preservation of a valid authenticated ID.

### Commits
- Vibe Studio identity guard: 095e1e76589f8c7dc4dbdef847a8c72a7b700c71
- Vibe Studio error panel: 4a41b3a5d9bdba5c0aff22f9528a9d82d8e152d5
- Identity guard regression tests: e8a1323d2ee7526c565a706b5148b3b14f337484
- Account-scoped stream synchronization: 512c0e0ec682290604e31309caa927f7d0d1dd07
- Crash Detail authenticated investigation guard: 373b91f1b6286064b0fcb4e7d047604dd1cc183d
- Crash investigation prompt newline correction: 8c1b1fadad7580473ba2506b0772f65e9a3f3f44
- Crash Detail error panel: 0d58dabdb9dd69010ec786b4b9369f2966b17cde

### Verification and limitations
- Re-fetched the final ViewModel, screen, and test sources from main. Source checks confirmed: no "anonymous" / "local_user" literals remain in VibeStudioViewModel.kt; no "user_default" remains in CrashDetailViewModel.kt; the nine protected Vibe Studio operations call the authenticated-ID guard; the account-scoped stream uses flatMapLatest + combine; both screens expose the error state; and all three identity test cases are present.
- The changed-file comparison from the pre-batch task-register commit shows five source/test paths changed in this slice (two ViewModels, two screens, one test file).
- Ran an isolated kotlinc microcheck with minimal model/JUnit stubs: 3 identity-guard test methods passed. This checks the pure ID-validation helper and test contract only; it is not the project's Gradle/JUnit test task or an Android compilation.
- The available runtime has kotlinc and Java but no Gradle executable or Android SDK. No Gradle unit tests, app compilation, lint, APK build, emulator/device run, screenshot inspection, or rendered RTL/LTR review was executed.
- GitHub combined-status queries returned empty status arrays for the source commits. The available commit-workflow-run wrapper only exposes PR-triggered runs and returned no push runs for the latest task-register commit. The last documented Android CI/CD, APK and Code Quality runs failed before exposing job steps/logs; no successful CI evidence is available for this batch.

### Remaining
- The Priority 0 identity prerequisite remains open. SeedData.kt still contains developer-owned demo fixtures tied to usr_dev_master; do not silently reassign or delete these records until production/demo seeding behavior is reviewed. The repository-wide fallback-identity audit and all other cloud authorization tests are not complete.
- VibeStudio and CrashDetail remain open under the screen design gate until Gradle tests/build, backend authorization behavior, rendered device review, RTL/LTR, compact layout and accessibility checks are evidenced.
- No Supabase runtime connectivity or production RLS behavior is inferred from these source changes.


## 2026-11-11 — Supabase password recovery flow (source + regression tests; project/device verification pending)

### Selection and scope
- Re-read the latest task register, recent main commits and repository instruction candidates before editing. The latest preceding implementation was Vibe Studio workspace-collector lifecycle hardening; this batch selected the still-open Priority 0 Identity and account recovery gap and did not edit the Vibe Studio files.
- Checked `AGENTS.md` / `agents.md` at the repository root, `.github/`, `docs/` and `app/`; none were present.
- The existing Login recovery mode was deliberately disabled, while SupabaseAuthApi already exposed `/recover`, `/verify` and `/user` endpoints. The repository contract did not orchestrate a verified recovery cycle.

### Changes
- `AuthRepository.kt`: introduced an explicit `PasswordRecoveryCredentials` value and separate request/completion operations. Default unsupported implementations preserve compatibility for existing test fakes and alternate repositories.
- `AuthRepositoryImpl.kt`:
  - `requestPasswordRecovery(email)` calls Supabase Auth `/recover` with the app redirect URI and does not create a local session.
  - `completePasswordRecovery(...)` exchanges a `token_hash` through `/verify` when present, or accepts the already verified callback access/refresh-token pair with an unexpired expiry. It calls `/user` to set the new password and only then persists the resulting encrypted session and publishes the user.
  - The existing Settings password-change operation now uses the current authenticated session, checks the email matches that session and rejects expired credentials before calling Supabase.
  - Session writes remain guarded by the shared mutex/generation checks; cancellation continues to propagate.
- Added `com.example.auth.AuthRedirects.kt` as the shared redirect contract so presentation and data layers do not depend on one another for the URI constant.
- Added `PasswordRecoveryLinkParser.kt`: accepts only the exact app scheme/host/path and `type=recovery`; supports a `token_hash` query or a verified access/refresh pair in the URI fragment; rejects missing or expired credentials without including token values in error messages.
- `MainActivity.kt`, `AndroidManifest.xml` and `AppNavigation.kt`: register the custom recovery callback, handle both cold-start and `onNewIntent` delivery, route the user to Login, and clear the consumed callback from the Activity intent to prevent replay after recreation.
- `MainViewModel.kt` and `LoginScreen.kt`: add the email-only recovery request and verified new-password step with eight-character minimum and confirmation validation. The success response is generic to avoid account enumeration. The Login status rail no longer implies that a live auth connection has been verified.
- Tests: added three parser cases, four repository cases (including email/session binding for Settings password changes), and updated Login form-validation coverage for the now-enabled recovery and new-password modes.

### Commits
- Domain/recovery contract and shared redirect/parser: `cd2e50e8596f4d3e543317147ba6b422058d5d41`, `d9e0927ae14137b68b5646ef126985454f1be28a`, `005b80e9ff798aefcc5a9b6f501bde5062688c2e`, `60b687aca584e7debe3f5b1f6e9929febba5cf67`, `4d2466d71bceea29c4d72ade0ba5fe924d26e645`, `422b6b66b23b86bd15e34be9c62d6322d3e94ffe`, `a3cdcd6c4852d44008c71f7838d99b4b3b4c8884`.
- Repository/session handling: `eb1aa1c5ebf2de3807bdf072c2df12a543db64fb`, `7e1713cda09b18e4ea2ad51e63ba2cd1ee9462cb`, `10bbfe40fdc050ac1f380ffb88a5a029f65bf3ef`.
- ViewModel, Login and callback wiring: `efaade5b23a96a3f850ad5710ece337c9a9d60d0`, `8f31325c774fde169439f323f073ff125b2b34e2`, `22ea2399d1b59e53e20c243cd32c0d3cfed43df8`, `949bdbf78ee0ad2c76be407e698412911816a3a4`, `21c367f8d1221436eb8591b1cba7720876fe3014`, `2126154186fe450badb371d204e4903f37f222fc`, `e63e441de1eb02dc8b076029272a6e8cab792fff`, `17716dd5a913c8812320255370cdb7c966875b15`.
- Regression tests: `350c82d9139602071b86ac8969ba74bbb445a2b3`, `d7e8d14a70ddfde43b8a2670a9a1a241004ffb00`, `6bc00f20de6ec95883533be8aea96e5c06b4556d`, `5167b9860f0c3334385ef0a647ab1611a86258fd`, `0bfa2f38d21259ce064290e52822c365fb03d09a`, `9acfab00576e6f20fd8045d7456317367a184c70`.

### Verification and limitations
- Re-fetched the final source, manifest and test files from `main`. The compare from pre-batch `830da2d4` to code head `9acfab00` shows 24 commits and 12 changed paths in this slice; no Vibe Studio workspace files were changed.
- An isolated `kotlinc` harness compiled the recovery-link parser logic with minimal domain/redirect constants and passed four assertions: token-hash query, encoded session fragment, wrong scheme and expired session. This is not the repository's Gradle/JUnit test task.
- The local runtime has Java and `kotlinc`, but no Gradle executable or Android SDK (`ANDROID_HOME` absent). No Gradle unit tests, Android/Compose compilation, lint, APK build, emulator/device run, screenshot, RTL/LTR rendered review or live Supabase recovery request was executed.
- GitHub combined-status lookup for code head `9acfab00576e6f20fd8045d7456317367a184c70` returned no status entries. The available commit-workflow-run lookup returned no runs and is limited to PR-triggered runs; this does not establish whether push workflows ran. No successful CI evidence is claimed.
- Supabase project Auth Redirect URLs and the hosted recovery email template were not inspected or changed. Before live use, add exactly `com.aistudio.crashintelligence.vyfrip://auth/recovery` to the project's Authentication redirect allow-list and verify that the recovery email preserves Supabase's `ConfirmationURL` / `redirect_to` behavior. The project-level setting is a required external prerequisite, not something this source change proves.
- Login remains [ ] under the design gate. This is a functional recovery-flow implementation and targeted Login interaction refinement, not visual sign-off or completion of the full Identity slice.

### Remaining
- Configure and verify the Supabase redirect allow-list and recovery email template; then send a real recovery email to a test account and verify the complete callback/update/session flow on Android.
- Run the new and existing Robolectric/JUnit tests, Gradle compilation, lint and APK build in a functioning Android runner.
- Render and review Login on a device/emulator in Arabic RTL and English LTR, including compact screens, keyboard/IME and accessibility.
- Complete email confirmation, session restoration/refresh rotation, sign-out and account-switch runtime tests. Keep the shared Identity prerequisite and Login screen open until all acceptance evidence exists.


## 2026-11-10 — Vibe Studio workspace collector lifecycle hardening (source + regression tests; Android execution blocked)

### Independent review of the preceding agent batch
- Compared 7dffb9db8e4fdd94923e9bfbf2ab19dcb9502374 with d4e3719e061f132646b2b95a4d9a8454c6508282: 8 commits, 6 changed paths (VibeStudioViewModel, VibeStudioScreen, CrashDetailViewModel, CrashDetailScreen, VibeStudioIdentityGuardTest, and this task register).
- Re-fetched and reviewed all five source/test paths. Confirmed AppNavigation injects provideAuthRepository(context) into CrashDetailViewModel; the new auth guard is not left on its nullable default in the app route. The Vibe Studio and Crash Detail error panels are present. No AGENTS.md / agents.md file exists in the repository tree; the only paths containing the word agent are application source directories/files.
- The push-triggered workflows for the preceding batch's final docs commit d4e3719e061f132646b2b95a4d9a8454c6508282 were found through the repository Actions runs collection: Android CI/CD 42705938638, Code Quality 42705938569, and APK 42705938229. All concluded failure. Each run exposed one job with an empty steps array; all three job-log downloads returned BlobNotFound. This confirms no evidence that Gradle, compilation, tests, lint, or APK packaging ran. It corrects the earlier limitation note that relied only on the PR-filtered commit-workflow-run wrapper.

### Confirmed defect and correction
- loadAccountData() cleared the Vibe Studio UI state on sign-out/account switch, but selectWorkspace() launched multiple long-lived child collectors for files, logs, checkpoints, runtime state, terminal history, builds, Git state, reviews, activity logs, approvals and model status without retaining a cancellable owner for the workspace subscription tree.
- Those child collectors were not cancelled when the account changed, when the user left a workspace, or when another workspace was selected. A late emission from the previous workspace could therefore repopulate the shared UI state after it had been cleared or after another workspace/account became active.
- Leaving a workspace previously cleared only activeWorkspace, openFiles, and selectedFileId; the rest of the workspace-scoped UI state could remain populated.
- Added WorkspaceCollectorOwner, which owns the parent coroutine for a selected workspace. Starting a different workspace cancels the previous parent and all nested collectors. Sign-out/account changes and selectWorkspace(null) now cancel the owner. Leaving/switching workspace resets workspace UI state while preserving the currently available team list.
- This is a lifecycle/security correction inside the existing Vibe Studio slice, not a screen-completion claim. The preceding batch's dismissible error/status panel remains in place; no additional visual redesign was made in this narrow fix. Vibe Studio remains open pending rendered UI review.

### Commits
- ViewModel lifecycle correction: 495449d57e7fe73cd8d0a9f330a2a4bbb7be47c0
- Regression tests: dba7e4ef21713560fa1b11563096dbefceb952e5

### Verification and limitations
- Re-fetched the updated ViewModel from main (blob SHA 3092c0d5aa3897923288a4ba3b9c0f403e7b0ac2) and the new test file (blob SHA 8fe968a212eede55bfe8f922202ed2208feef9ab); verified the owner is used for workspace replacement, sign-out/account change, and workspace exit.
- Added two coroutine regression tests: replacing a workspace cancels the previous workspace's child collectors; leaving a workspace cancels the active collectors. The JUnit/coroutines-test file was added but has not been run under Gradle.
- Local isolated kotlinc check compiled the collector-owner logic and a coroutine harness; the harness passed replacement and explicit-cancellation assertions. A separate compile check confirmed the return@start early-exit label compiles. These are isolated Kotlin checks, not Android/Compose compilation or the project's JUnit task.
- No Gradle executable or Android SDK is available in the local runtime. No app build, JUnit/Robolectric test, lint, APK build, emulator/device run, screenshot, rendered RTL/LTR review, or Supabase runtime request was performed.
- No Android screen is marked complete. The Vibe Studio design gate remains open until the combined screen is rendered and reviewed on device/emulator, including RTL/LTR, compact layout and accessibility.


### CI recheck after this batch
- Source commit 495449d57e7fe73cd8d0a9f330a2a4bbb7be47c0: Android CI/CD run 42714574898, Code Quality run 42714574943, and APK run 42714575098 all concluded failure. Their jobs exposed empty step arrays; log downloads returned BlobNotFound.
- Test commit dba7e4ef21713560fa1b11563096dbefceb952e5: Android CI/CD run 42714631616, Code Quality run 42714631568, and APK run 42714631443 all concluded failure. Their jobs exposed empty step arrays; log downloads returned BlobNotFound.
- This task-register commit 93e6c5a6a4aa5ed4b2bd23861ae3e3032912ef22: Android CI/CD run 42714785788, Code Quality run 42714785758, and APK run 42714785502 all concluded failure. Their jobs exposed empty step arrays; log downloads returned BlobNotFound.
- These runs provide no evidence that a runner executed checkout, Gradle, compilation, tests, lint or APK packaging. Do not interpret the failure as a source compilation failure; the actual job steps/logs are unavailable.


## 2026-11-11 — Password recovery cancellation and repeated-link state fix

### Independent review of the preceding agent batch
- Re-read the current task register and recent main history. The preceding password-recovery implementation spans 24 commits and 12 changed paths from baseline `830da2d4` to code head `9acfab00`; the final documentation commit was `fccb8aff`.
- Re-fetched and inspected the current `AuthRepository.kt`, `AuthRepositoryImpl.kt`, `AuthRedirects.kt`, `PasswordRecoveryLinkParser.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `AppNavigation.kt`, `MainViewModel.kt`, `LoginScreen.kt`, and the parser/repository/Login tests. Confirmed the app callback is restricted to the declared scheme/host/path, cold-start and `onNewIntent` delivery are handled, the consumed Activity data is cleared, and recovery credentials are passed to Supabase rather than treated as a local success.
- Checked repository-root, `.github/`, `docs/`, `app/`, and relevant source/test instruction paths for `AGENTS.md` / `agents.md`; none were present.
- No repository source changes from that preceding recovery batch were overwritten.

### Confirmed defect
- After accepting a recovery link, `MainViewModel` stored the one-time credentials and emitted `PasswordRecoveryUiState(isReadyForNewPassword = true)`.
- The Login back action changed only its local Compose mode. It did not clear the pending credentials or reset the ViewModel state.
- If the user later opened another recovery link, the ViewModel assigned the same `true` state. `MutableStateFlow` suppresses equal values, so the Login screen's `LaunchedEffect(passwordRecoveryState.isReadyForNewPassword)` did not run again. The second link could therefore leave the user in sign-in mode even though new credentials had arrived. The previous one-time credential also remained in memory after the user cancelled.

### Changes
- `MainViewModel.kt`: added `cancelPasswordRecovery()` to clear the pending one-time credential, reset the recovery state to false, and clear recovery messages.
- `LoginScreen.kt`: the back action from `SET_NEW_PASSWORD` now invokes the cancellation method before returning to sign-in. This produces a false → true state transition for the next recovery link and discards the abandoned credential.
- Added `MainViewModelPasswordRecoveryTest.kt`: verifies cancellation clears the old credential, a completion attempt after cancellation does not call the repository, and a second distinct recovery link is accepted and passed to the repository.

### Commits
- ViewModel cancellation state: `0e52fa07eba387c5870e326d238f833053433c99`
- Login back-action wiring: `64359d63d7999e811d5aa1d95b9d726005d03783`
- Regression test: `3560227c72c72d9f8b0a46e8f37e5e7d05137e95`

### Verification and limitations
- Re-fetched from `main` after the code/test writes: `MainViewModel.kt` blob `40be10c121ab36d6178e913837dff003dae925d4`; `LoginScreen.kt` blob `1e7450971ab6dcf88a5c2e091aad730d50e1ea7d`; regression test blob `d2b90314333ac4b4bb75dca86272c5d222ac8536`.
- Compared `fccb8aff` to the test commit: exactly three changed paths (ViewModel, Login screen, new test). No files from the preceding agent's recovery implementation were changed.
- Added one coroutine/StateFlow regression test covering the cancel → new-link sequence. It has **not** been executed with Gradle/JUnit. The available runtime has Java and `kotlinc`, but no Gradle executable, no Android SDK (`ANDROID_HOME` unset), and no local repository checkout. No app compilation, Robolectric/JUnit task, lint, APK build, emulator/device test or rendered RTL/LTR review is claimed.
- Push workflows for the ViewModel commit `0e52fa07`: Android CI/CD `42913659001`, Code Quality `42913659125`, APK `42913658995`; for Login commit `64359d63`: Code Quality `42913672134`, Android CI/CD `42913673187`, APK `42913672246`; for test commit `3560227c`: Code Quality `42913724516`, Android CI/CD `42913724527`, APK `42913724643`. All concluded failure. The latest jobs expose empty `steps` arrays, runner ID 0 / no runner name, and log downloads return `BlobNotFound`; this is no evidence that Gradle or lint started and must not be described as a source compilation failure.
- Re-ran the three preceding recovery-batch failed workflows (run IDs `42907697534`, `42907697277`, `42907697385`). The reruns were accepted but all ended in failure again with no usable step logs.
- Supabase project `vibe-coding` remains `ACTIVE_HEALTHY`; Security Advisor returned zero lints. Performance Advisor reports informational unused-index findings only; no database or Auth configuration was changed. The available Supabase connector did not expose the Auth redirect allow-list or hosted recovery email-template settings, so the required redirect `com.aistudio.crashintelligence.vyfrip://auth/recovery` and email-template behavior remain externally unverified. No live recovery email or Android callback was tested.
- Login remains open under the mandatory visual-design gate. This fixes recovery-state lifecycle behavior only; it is not a visual sign-off or completion of the Identity slice.

### Remaining
- Configure and verify the Supabase Auth redirect allow-list and recovery email template; then test a real recovery email and callback on Android.
- Run the new ViewModel regression test plus the existing repository/parser/Login tests in a functioning Android runner.
- Complete session restoration/refresh, email confirmation, sign-out/account-switch tests and Login's rendered RTL/LTR, compact-screen, IME and accessibility review.

## 2026-11-12 — Password recovery in-flight cancellation and stale-result isolation

### Selection and scope
- Re-read the ordered shared Identity prerequisite and recent main history. The previous batch already handled cancellation after the user leaves password setup; this follow-up addresses a different race: a second recovery deep link can arrive while the first password-update request is still in flight.
- Checked the complete repository tree for AGENTS.md / agents.md; no such instruction file exists in the current tree. No newer agent commit was present when this batch began.

### Changes
- MainViewModel.kt: each accepted recovery link invalidates and cancels the previous recovery job. Each completion attempt has a generation number; results and callbacks from superseded attempts are ignored, so an older completion cannot clear a newer one-time credential or navigate the user using a stale callback. Explicit cancellation also invalidates the active attempt.
- AuthRepositoryImpl.kt: the verified recovery coroutine now checks cancellation after OTP verification, after the password-update request, and immediately before persisting the resulting session. A cancelled/superseded attempt therefore cannot proceed to local session persistence once cancellation is observed.
- AuthRepositoryPasswordRecoveryTest.kt: added a regression test where the update endpoint cancels the current coroutine before returning a successful response; the repository must propagate cancellation and leave the local session empty.
- MainViewModelPasswordRecoveryTest.kt: added a regression test proving that arrival of a second recovery link cancels the first in-flight completion, suppresses its stale callback, and preserves the second link for the next attempt.

### Commits
- Repository cancellation guard: ea404b5bd18868656ec53f42a85dd269f96b5199
- Repository cancellation regression test: e5d516d206ff4232cfb0b5df33c11e5ef1120191
- ViewModel attempt-generation and cancellation: 71c39c3db1d39edf65ca832850990869f3a3d257
- Superseded-attempt regression test: 2d741fd43514d9f1377bc391f8d27f1c08a708eb

### Verification and limitations
- Re-fetched all four changed source/test files from main after the writes. Compared base 40071f9b5e9320b8ad991f6fe42c2b6630c7111e to code/test head 2d741fd43514d9f1377bc391f8d27f1c08a708eb: exactly four changed paths, four commits, no unrelated files.
- The new regression tests were added but not executed. An isolated kotlinc 1.9.0 compile check passed for the coroutine APIs used by this change (currentCoroutineContext, ensureActive, CoroutineStart.LAZY, Job cancellation and awaitCancellation); this checks API/syntax availability only, not the repository source or tests. The environment has Java 21 but no Gradle executable, no Android SDK (ANDROID_HOME unset), and no local checkout. No repository Gradle/JUnit/Robolectric test, Android compilation, lint, APK build, emulator/device run, or rendered RTL/LTR review is claimed.
- Push workflows for code/test head 2d741fd43514d9f1377bc391f8d27f1c08a708eb: Android CI/CD run 43110200525, APK run 43110200489, and Code Quality run 43110200419 all concluded failure. Each job reports runner_id=0 and an empty steps array; the Android CI job-log request returned BlobNotFound. This is runner/Actions infrastructure evidence, not a Gradle or source compilation failure. No successful CI evidence exists.
- No Supabase Auth settings, redirect allow-list, email template, database schema, RLS policy, Firebase dependency or production data were changed. No live recovery email or Android callback was tested.

### Remaining
- Run the new and existing recovery/parser/repository/ViewModel tests in a functioning Android runner; resolve the GitHub Actions runner allocation/availability problem before treating CI as a verification source.
- Verify the Supabase redirect allow-list and recovery email template, then test a real recovery email, callback and password update on Android.
- Complete session restoration/refresh-token rotation, email confirmation, sign-out/account-switch runtime tests, and Login's rendered RTL/LTR, compact-screen, IME and accessibility review. Login and the shared Identity prerequisite remain open.


## 2026-11-12 — Rejected password-recovery link resets Login form state

### Independent review of the preceding agent batch
- Re-read the current task register and recent main history. The preceding code batch from `40071f9b` to `2d741fd4` contains four commits and exactly four changed paths: `AuthRepositoryImpl.kt`, `MainViewModel.kt`, `AuthRepositoryPasswordRecoveryTest.kt`, and `MainViewModelPasswordRecoveryTest.kt`. The subsequent commits `4c7c8b1d` and `406c9ad6` only updated this register.
- Re-fetched the four changed source/test files. Confirmed cancellation is rethrown by the repository's `authResult`, the recovery request checks cancellation after OTP verification and password update, and the session write is guarded by both coroutine activity and the session generation.
- Re-fetched the ViewModel and repository regression tests. They cover superseded in-flight recovery attempts and prevent a cancelled password-update response from persisting a local session. These tests were added but not executed by Gradle.
- Checked root, `.github/`, `docs/`, `app/`, `app/src/`, and the main/test instruction paths for `AGENTS.md` / `agents.md`; each candidate returned 404. No repository agent-instruction file is available.
- The preceding batch's Actions jobs for runs `43110200525`, `43110200489`, and `43110200419` each exposed an empty steps array. No job execution or Gradle result is evidenced. Combined-status lookup for the preceding code head returned no status entries.

### Confirmed Login UI state defect
- `LoginScreen.kt` observed `passwordRecoveryState.isReadyForNewPassword` but only handled the `true` transition.
- When a rejected or expired replacement link arrived while the screen was in `SET_NEW_PASSWORD`, `MainViewModel` correctly cleared its one-time credentials and emitted `false`, but the Compose form remained in `SET_NEW_PASSWORD`. The user could still see a password-update form even though its recovery credential no longer existed; submitting then produced a second, confusing “recovery link unavailable” error.
- The back action also cleared the confirmation field but retained the typed new password in Compose state.

### Changes
- `LoginScreen.kt`: added `resolvePasswordRecoveryMode` to map an accepted link to `SET_NEW_PASSWORD` and a rejected/expired link from that mode back to `SIGN_IN`, while keeping the recovery-request form open when appropriate.
- On invalidation, clear the new-password and confirmation fields, hide the password, and preserve the ViewModel's rejected-link error message so it remains visible in the sign-in form.
- The explicit back action now also clears the new-password value and visibility state after cancelling the one-time recovery credential.
- `LoginFormValidationTest.kt`: added four state-transition regression tests for accepted links, rejected links from password setup, rejected links while requesting recovery, and the idle sign-in state.

### Commits
- Login state correction: `35be256d5806b47e9ffd93c66b93cdac2f7db4b8`
- Regression tests: `2f61f346b8ace23610190161b789df047ce842d7`

### Verification and limitations
- Re-fetched both changed files from `main` after the writes. Final blob SHAs: LoginScreen `23e3950e08d180170806df56789a7197f943d77e`; LoginFormValidationTest `0004b8e1409f6bca0920b077434693baea09550a`.
- Compared `406c9ad6` to the test commit: exactly two changed paths (LoginScreen and LoginFormValidationTest), with no overlap with the preceding agent's four changed source/test paths.
- An isolated `kotlinc` harness using the same pure mode-transition function passed four assertions. This is a narrow Kotlin logic check only; it does not compile the Android/Compose source or execute the Robolectric/JUnit test class.
- The environment has Java 21 and `kotlinc`, but no Gradle executable and no Android SDK (`ANDROID_HOME` unset). No Gradle test, Android compilation, lint, APK build, emulator/device run, screenshot, or rendered RTL/LTR review was executed. GitHub combined-status lookup for both new commits returned no status entries; no successful CI evidence is available.
- No Supabase settings, Auth configuration, RLS policy, database, Firebase dependency, or production data were changed. Login remains open under the visual-design gate; this is a recovery-state/secret-clearing correction, not rendered visual sign-off or completion of the Identity slice.

### Remaining
- Run `LoginFormValidationTest` and the existing recovery/parser/repository/ViewModel tests in a functioning Android runner.
- Verify the Supabase recovery redirect allow-list and email template, then test a real recovery email and callback on Android.
- Complete session restoration/refresh rotation, email confirmation, sign-out/account-switch runtime tests, and Login's rendered RTL/LTR, compact-screen, IME and accessibility review.

## 2026-11-13 — Login localization, compact layout and keyboard flow

### Selection and review
- Re-read the shared Identity prerequisite and the latest Login recovery-state correction (`8bfa77c8`). The preceding agent fixed rejected/expired recovery-link state and password-field clearing; this batch does not modify that state-transition behavior.
- Checked the repository tree and the root, `.github/`, `docs/`, and `app/` instruction paths for `AGENTS.md` / `agents.md`; no agent-instruction file exists in the current tree.
- Reviewed the current Login source, shared `VibeDesignTokens`, both string-resource sets, and `LoginFormValidationTest`.

### Observed Login UX issues
- Most Login copy and all local validation messages were hard-coded in Arabic, despite the app having a default English resource set. This prevented the Login screen from following the selected English locale.
- The recovery email field advertised the IME action `Done`, but its callback only dismissed focus; users still had to tap the primary action.
- The form used fixed logo/spacing dimensions and an uncapped content width, making the long identity form less adaptable to compact phones and wide screens.
- Error/success banners were not explicitly exposed as polite live regions to accessibility services.
- The footer said “secure connection” without checking a live connection. The replacement identifies the configured auth provider without claiming that a request is currently online.

### Changes
- `LoginScreen.kt`: moved visible Login labels, instructions, action labels, password visibility descriptions and local validation messages to Android string resources; validation now carries a resource ID instead of Arabic display text.
- Added matching English (`values/strings.xml`) and Arabic (`values-ar/strings.xml`) resources for the complete Login copy and local validation messages.
- Added a responsive 520dp content-width cap, narrower horizontal gutters on compact widths, and reduced header/logo spacing on compact heights while preserving the existing scroll and IME handling.
- The recovery email field now submits the recovery request when the user presses the keyboard Done action; sign-in and sign-up keep their Next/focus behavior.
- Error and success banners now expose polite live-region semantics.
- Clear the password and visibility state when switching between sign-in, sign-up and recovery modes, reducing the lifetime of stale credentials in Compose state.
- Replaced the unverified “secure connection” footer wording with a static Supabase provider label; it does not claim live API connectivity.
- `LoginFormValidationTest.kt`: added four regression tests for the email IME action across sign-in, sign-up, recovery and new-password modes.

### Commits
- Login localization, responsive layout and interaction: `d021a296475876d6bf90c529d586177bf9a36f56`
- English Login resources: `7b02dbf3a632fb32a52d3d4d57b4bdad09864add`
- Arabic Login resources: `526ec808f74fe59aa943b85280e76f31f5cc12a0`
- Localized accessibility label: `1f746286acd66390e434f1ba5f2d913754d823ae`
- Email IME regression tests: `95f5b1e192ab35e1ce7a18a730f569cfd822fee7`

### Verification and limitations
- Re-fetched the final Login source, both resource files and test file from `main`. All 35 distinct `R.string` references used by Login have matching English and Arabic entries; no duplicate auth resource names or remaining Arabic string literals were found in the Login source.
- Compared base `8bfa77c8` to code/test head `95f5b1e1`: five commits, four changed paths only (LoginScreen, the two locale resource files, and LoginFormValidationTest).
- Added four Robolectric/JUnit regression tests, but did **not** execute them. No Gradle executable or Android SDK is available in the current execution environment. No Android compilation, lint, APK build, emulator/device run, screenshot, or rendered RTL/LTR/IME/accessibility review is claimed.
- GitHub Actions did trigger for the code/test head. Android CI run `43313070851`, APK run `43313070857`, and Code Quality run `43313070861` all concluded failure. Their corresponding jobs reported `runner_id=0`, an empty runner name, and `steps=[]`; this provides no evidence that checkout, Gradle, compilation, tests or lint started. Combined commit status returned no status entries.
- No Supabase Auth settings, redirect allow-list, email template, database schema, RLS policy, Firebase dependency, production data or secrets were changed. The Supabase redirect allow-list and live recovery flow remain unverified.

### Remaining
- Run `LoginFormValidationTest` and the existing recovery/parser/repository/ViewModel tests in a functioning Android runner; resolve the GitHub Actions runner-allocation failure before using CI as verification.
- Inspect Login on a real emulator/device in Arabic RTL and English LTR, including compact widths/heights, keyboard/IME, accessibility announcements and contrast. Login remains open under the mandatory visual-design gate.
- Localize auth error/success messages originating from repository/ViewModel/backend paths where they are still hard-coded in Arabic.
- Verify the Supabase recovery redirect allow-list and email template, then exercise a real recovery email, callback, password update, session refresh and account switch.


## 2026-11-13 — Localized authentication result and failure messages

### Independent review of the preceding agent batch
- Re-read the current Identity prerequisite and the latest Login localization/responsive-UX batch. Compared `8bfa77c8` to `95f5b1e1`: five commits, four changed paths (`LoginScreen.kt`, English and Arabic string resources, and `LoginFormValidationTest.kt`). The Login copy migration, responsive width/height handling, recovery-email IME submission, live-region semantics and regression coverage are present in the current tree. I did not alter the preceding batch's layout or recovery-mode transition logic.
- Re-fetched the current Login screen, both locale resource files, ViewModel, parser and related tests. The preceding batch's resource references were checked against both locale sets.
- Checked root, `.github/`, `docs/`, `app/`, and relevant source/test instruction paths for `AGENTS.md` / `agents.md`; no contributor instruction file was found. The repository search result for those terms only matched historical documentation, not an instruction file.
- Inspected the preceding agent's code/test head `95f5b1e1` Actions runs: Android CI/CD `43313070851`, APK `43313070857`, and Code Quality `43313070861`. All concluded failure; each job returned `steps: null`, and each job-log fetch returned `BlobNotFound`. No Gradle, compilation, test or lint execution is evidenced.

### Confirmed localization gap
- Login's visible labels and local field validation had moved to Android resources, but `MainViewModel.kt` still put Arabic literals into `AuthActionState` for sign-in, signup, recovery, password update and invalid recovery-link paths.
- For failed repository operations, the ViewModel also forwarded arbitrary `Throwable.message` values from the Supabase/data layer. That could display untranslated provider/transport text in the English locale and expose low-level response details in the UI.
- The current screen reads only the ViewModel's `AuthActionState`; this gap could be addressed without changing Supabase Auth configuration, schema, RLS or production data.

### Changes
- `MainViewModel.kt`: introduced the typed `AuthUiMessage` enum and changed `AuthActionState.errorMessage/successMessage` from raw strings to stable message keys. Sign-in, signup, email-confirmation-required signup, recovery request, recovery completion, reset-password and rejected/missing recovery-link outcomes now emit typed keys.
- Network `IOException` failures map to a shared network message; other failures map to operation-specific generic messages. Raw provider exception text is no longer forwarded to Login. A `CancellationException` inside a failed `Result` is rethrown rather than converted into a user-facing error.
- `LoginScreen.kt`: maps every `AuthUiMessage` to a localized Android string resource before rendering error/success banners.
- Added 13 matching authentication result resources to both `values/strings.xml` and `values-ar/strings.xml`, including a non-enumerating recovery-request success message.
- Added `MainViewModelAuthMessageTest.kt` with nine regression cases for generic and network sign-in failures, signup email confirmation, recovery request success/failure, rejected/missing recovery credentials, password-recovery failure and reset-password failure.

### Commits
- Typed ViewModel outcomes: `72a1d6340c1f0b262ff8c922f565b2243396641f`
- Localized Login rendering: `a59a3e395635f3ac35ed8b7ef0f699c242579fd1`
- English result resources: `09f4e4398cbabb9be3bb78b0c65e848cac294ba1`
- Arabic result resources: `18e42ccd674abf74890dd91488dbb790d5bbb8ee`
- Regression tests: `adf6e393e726b6b758be9666f954af06238972c5`
- Test import cleanup: `4c9582075d8e4ded0a5dde39f2710419434a40fd`

### Verification and limitations
- Compared the feature base `f199a175` to test head `adf6e393`: five commits, five changed paths (ViewModel, Login screen, both locale resources and the new test file). The later import cleanup changes only the test file.
- Static consistency check on the re-fetched final source: all 13 enum values have exactly one UI resource mapping; all 13 referenced resource IDs exist in both English and Arabic; no duplicate auth-result resource names were found; no direct `exception.message` / parser-message forwarding remains in `MainViewModel.kt`.
- The new nine JUnit tests were added but not executed. The environment has Java 21 and Kotlin 1.9.0, but no Gradle executable, no Android SDK (`ANDROID_HOME` unset), no Gradle dependency cache and no local project checkout. Therefore no Android compilation, Gradle test, lint, APK build, emulator/device run or rendered RTL/LTR review was executed.
- Actions for code/test head `adf6e393`: APK `43317656807`, Android CI/CD `43317656816`, and Code Quality `43317656961` all failed before exposing steps; `steps` was null and log retrieval returned `BlobNotFound`. This is not evidence of a source compilation failure, but it is also not a successful verification.
- No Supabase Auth settings, redirect allow-list, email template, database schema, RLS policy, Firebase dependency, production data or secrets were changed.

### Remaining
- Run the new ViewModel tests and existing Login/recovery/parser/repository tests in a functioning Android runner; restore GitHub Actions runner/log availability before treating CI as verification.
- Inspect Login on an emulator/device in Arabic RTL and English LTR, including compact layouts, IME, accessibility announcements and contrast. Login remains open under the mandatory visual-design gate.
- Verify the Supabase recovery redirect allow-list and email template, then exercise a real recovery email/callback/password update, session restoration/refresh rotation, email confirmation, sign-out and account switching.

## 2026-11-14 — Android Build & CI reproducibility investigation

### Repository and agent review
- Inspected `main` at initial HEAD `53031e383a53b5851b7673fd78b69822e221c4f4`, including the latest 20 commits and the existing build-related register entries.
- Read this complete task register. Checked root `AGENTS.md` and `agents.md`; both paths return 404. No repository agent-instruction file was available to read.
- Reviewed the previous agent's latest Login/authentication batch and its recorded CI outcomes. The latest feature/test commits are followed by documentation-only corrections; no Android build or JUnit execution is recorded as successful.

### Confirmed build/CI configuration findings
- `gradle/wrapper/gradle-wrapper.properties` pins Gradle `9.3.1`, but the repository does not contain `gradlew`, `gradlew.bat`, or `gradle/wrapper/gradle-wrapper.jar`. Therefore the documented wrapper commands cannot run from this repository as currently checked in.
- The Android CI, APK, Code Quality, PR Validation and Release workflows invoked the system `gradle` command. Several used `gradle/actions/setup-gradle` without specifying `gradle-version`; the Code Quality, PR Validation and Release workflows did not configure a Gradle version at all. This made Gradle provisioning dependent on the runner's preinstalled toolchain rather than the repository's declared version.
- The last previously documented Actions runs (`43313070851`, `43313070857`, `43313070861`) concluded failure. Fetching their job steps returned empty arrays and their log downloads returned `BlobNotFound`; this does not identify a Gradle/source root cause.
- Combined status lookup for the initial HEAD returned no status entries. The available GitHub integration does not expose a repository-wide workflow-run listing action in this execution, so new run IDs/job logs could not be independently enumerated.
- Local execution environment: Java 21 is installed; `gradle` and `adb` executables are absent; `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset; there is no local repository checkout. No Gradle build or Android test could be run locally.

### Changes made on main
- Pinned Gradle `9.3.1` through `gradle/actions/setup-gradle@v4` in all five workflows: `.github/workflows/android.yml`, `build-apk.yml`, `code_quality.yml`, `pr_validation.yml`, and `release.yml`.
- Removed the duplicate Gradle cache option from `actions/setup-java` in workflows using setup-gradle, and removed misleading optional `chmod gradlew` steps from workflows that do not have a wrapper.
- Corrected YAML indentation in the Android CI and APK workflow setup steps in follow-up commits after re-fetching the current files.
- Commits (all on `main`):
  - `f27147d6993f0523a0acfe3ebc9f5fad84b8b466` — initial Android CI Gradle pin
  - `8ba8d56635669f02d94a68852baa0b4d2b81469b` — APK workflow Gradle pin
  - `f1dceb8e50d3510fef638588552cfbc0deb42cdb` — Code Quality Gradle pin
  - `0a055523f435021a72a703c1440fe5ae7fd1f940` — PR Validation Gradle pin
  - `047d15ecda4410fcdbf19782a82d514aa9b24189` — Release workflow Gradle pin
  - `7b45b1fcee614d6f8109420cdba5d44c979e093c` — correct Android CI YAML indentation
  - `ab2b30915870522e8fefd8d2ddbdf68c2a6750d0` — correct APK workflow YAML indentation

### Verification and limitations
- Re-fetched the five workflow files from `main` after the changes and inspected their Gradle setup steps. The final Android CI and APK workflow indentation corrections are present.
- Latest commit-status lookup returned no status entries. No current workflow run could be retrieved through the available run-list interface; therefore no CI success is claimed.
- `./gradlew --version`, `./gradlew clean`, `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, and `./gradlew assembleRelease` were not run: wrapper scripts/JAR are absent and the local environment has no Gradle or Android SDK.
- No APK artifact, Android compilation, JUnit result, lint report, or release build is verified.
- This change improves deterministic Gradle provisioning in CI; it does **not** prove the cause of the previously failed runs or prove the Android project builds successfully.

### Remaining build work
- Restore a functioning GitHub Actions runner/log path and verify the next Android CI, APK and Code Quality runs to completion.
- Decide whether to add and validate a complete Gradle Wrapper (scripts, JAR and pinned distribution) or continue using the explicitly pinned non-wrapper Gradle installation. Do not invoke `./gradlew` until the wrapper actually exists.
- Run configuration, compilation, unit tests, lint, debug APK generation and artifact inspection on a runner with Android SDK 36 available.
- Investigate any actual Gradle/Kotlin/AGP/compiler error only after obtaining readable job logs.


## 2026-11-14 — Firebase migration inventory (source/configuration audit; runtime unverified)

### Selection and non-repetition
- Re-read the Priority 0 shared prerequisites and inspected the latest 20 commits. The preceding run was confined to Android build/CI reproducibility and pinned Gradle 9.3.1 in workflows; this run deliberately moved to the independent first shared prerequisite: inventory and classification of remaining Firebase usage.
- Current code head before this inventory: `53031e383a53b5851b7673fd78b69822e221c4f4`; latest `main` documentation head was `53f3d55e5e05d3d5dbd1c9995eedbc0e136cfb1c`. The 20-commit review showed the latter was documentation-only.
- Checked root `AGENTS.md`, `.github/AGENTS.md`, and `app/AGENTS.md`; each returned 404. No repository agent-instruction file was available.

### Confirmed inventory findings
- Added `docs/FIREBASE_INVENTORY.md` with file-level evidence, classification and a safe migration/retirement sequence.
- Confirmed active Firestore usage in `CrashRepositoryImpl.kt` for project/application/crash/integration/issue/audit/notification data, with nullable Firestore injection from `DependencyProvider.kt`.
- Confirmed the CrashRepository FirebaseAuth parameter is optional and currently not supplied by `DependencyProvider.provideCrashRepository`; therefore cloud methods that require the Firebase UID cannot be assumed to work. The application Auth repository is separately wired to Supabase. Identity must be unified before migrating the repository.
- Confirmed `AIModelTeamRepositoryImpl.kt` directly obtains `Firebase.auth`, reads/writes Firestore team, execution and usage collections, and contains in-memory fallback maps. Several writes suppress Firestore exceptions after mutating local state, which can misrepresent persistence success.
- Confirmed `CrashIntelligenceApp.kt` invokes Firebase initialization; `FirebaseUtil.kt` contains hard-coded placeholder FirebaseOptions (`remixedappid`, `remixed-api-key`, `remixed-project-id`). These are not evidence of a live Firebase project and should be retired only after consumers are migrated.
- Confirmed Firebase BOM, Firestore, Auth and Firebase Vertex AI dependencies in `gradle/libs.versions.toml` and `app/build.gradle.kts`. Targeted source search did not find production imports of Firebase Vertex AI; treat its dependency as a removal candidate pending complete dependency/build verification.
- Crashlytics integration is exposed as an integration type/UI option, but `CrashRepositoryImpl.syncCrashes` explicitly rejects it pending a backend proxy. Targeted search found no Firebase Crashlytics SDK usage.
- Targeted searches found no production source references to Firebase Storage, Messaging or Functions. This search is not a proof of absence across generated code, reflection, transitive dependencies or provider configuration.

### Files and commits
- Created `docs/FIREBASE_INVENTORY.md` with classification, known evidence, safety constraints and the ordered migration sequence.
- Inventory document commit: `606437a38194254556d0e0a5e590ba4fa3ddc7ae`.
- This register update records the same evidence and the next independent task. No application source, Firebase configuration, production data, Firestore rules, Supabase schema/RLS, Auth settings or secrets were changed.

### Verification and limitations
- Re-fetched the listed production source/configuration files and recorded their blob SHAs during inspection.
- Ran targeted GitHub code searches for Firebase Auth, Firestore, Storage, Messaging, Functions, Crashlytics, FirebaseApp and the `com.google.firebase` package.
- Read the latest commit metadata/diff and checked combined commit status for `53f3d55e5e05d3d5dbd1c9995eedbc0e136cfb1c`; the status response contained no status entries. The available connector does not expose a repository-wide Actions run-list operation in this execution, so no new workflow run IDs or logs could be independently enumerated.
- This was source/configuration inspection only. No Gradle dependency report, build, unit test, emulator/device test, live Firebase request, Supabase request or RLS test was run. No runtime connectivity or migration completion is claimed.

### Remaining and next task
- The Firebase inventory prerequisite is now documented, not completed as a migration. Next, resolve the shared identity boundary for CrashRepository and AIModelTeamRepository and confirm schema/ownership conventions before migrating any cloud feature slice.
- Then migrate Projects/Applications/Crash data and Teams in dependency order with versioned Supabase migrations and negative RLS tests.
- Keep Firebase SDKs and initialization until all confirmed consumers have tested replacements. Do not remove Firebase or migrate production data as part of this inventory-only pass.
- Last domain worked: Firebase usage inventory. Next domain: Supabase identity boundary + project/crash repository migration planning/implementation (not Login UI).


## 2026-11-14 — Android CI runner allocation recheck (build execution blocked)

### Repository and non-overlap
- Re-fetched `main` at HEAD `1c4705c89899279814d27c506d4d3c637b4d581e` and reviewed the latest 20 commits.
- Compared the current HEAD with the preceding Android build investigation commit `53f3d55e5e05d3d5dbd1c9995eedbc0e136cfb1c`: the only two intervening commits are `606437a38194254556d0e0a5e590ba4fa3ddc7ae` and `1c4705c89899279814d27c506d4d3c637b4d581e`. They add/update only `docs/FIREBASE_INVENTORY.md` and `docs/SCREEN_TASKS.md`; no Android source or workflow file changed. This is the other agent's Firebase inventory batch and does not overlap the build workflow files.
- Read the task register and checked the repository tree for `AGENTS.md`; no such file exists in the tree (the root path also returns 404). No agent-specific instruction file was present.

### Confirmed GitHub Actions result
- For HEAD `1c4705c89899279814d27c506d4d3c637b4d581e`, Android CI run [43512746113](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43512746113), APK run [43512746170](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43512746170), and Code Quality run [43512746570](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43512746570) all concluded `failure`.
- Re-ran the failed jobs for all three workflows. Attempt 2 also failed before any step executed: each job completed in approximately two seconds with `runner_id=0`, an empty `runner_name`, and `steps=[]`.
- The job-log download returned `BlobNotFound` for all three attempt-2 jobs. The three runs contain no artifacts; in particular, no debug APK or lint report was produced.
- This confirms a runner-allocation/startup failure, not a Gradle, Kotlin, resource, test, or lint failure. The exact upstream reason (hosted-runner entitlement/billing, repository/organization policy, or GitHub service-side allocation) is not exposed by the available job response and cannot be stated as fact.

### Build configuration audit
- All five workflows now use `gradle/actions/setup-gradle@v4` with explicit `gradle-version: '9.3.1'` and invoke the provisioned `gradle` command. The final Android CI and APK YAML indentation is valid on source inspection. The setup action documents `gradle-version` as the supported way to provision a fixed Gradle version for a project without a wrapper.
- `gradle/wrapper/gradle-wrapper.properties` still pins Gradle `9.3.1`, but `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are absent. The current CI does not call `./gradlew`, so the missing wrapper does not explain the observed CI failure. Retain the explicit non-wrapper Gradle provisioning for now rather than add an ungenerated/unverified wrapper.
- Static version review: AGP `9.1.1`, Gradle `9.3.1`, Kotlin `2.2.10`, `compileSdk 36`, and the workflow JDK 17/21 selections align with the AGP 9.1.1 compatibility table. The app uses Compose compiler plugin `2.2.10` and Compose BOM `2024.09.00`; no concrete incompatibility is established from source inspection. Android SDK/build-tools availability remains unverified because no runner reached the setup steps.
- Manifest-referenced XML/string/theme resources are present in the current resource tree. Unit and instrumented test source sets are present, but none ran.

### Local execution and exact limitations
- `java -version` — exit code `0`; OpenJDK `21.0.12.1`.
- `gradle --version` — exit code `127`; `gradle: command not found`.
- `adb version` — exit code `127`; `adb: command not found`.
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset. No local checkout of this repository and no Gradle wrapper script were found.
- `./gradlew --version`, `./gradlew clean`, `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, and `./gradlew lintDebug` were not run: there is no local repository checkout, wrapper, Gradle installation, or Android SDK. No exit code is claimed for commands that were not invoked.
- `./gradlew assembleRelease` was not run. No functioning Android runner or verified release-signing environment was available; no release APK/AAB is claimed.
- No application source or workflow was changed in this recheck. This register entry was introduced by documentation-only commit `b40dcd113a3d7270cf6e190793c54a28fb7febb9`; no application source or workflow file changed.

### Remaining blocker and next action
- Highest blocker: GitHub Actions does not assign a runner to these jobs. A repository owner/admin must inspect Actions availability, hosted-runner access/usage limits, billing and organization/repository runner policies, then restore runner allocation.
- After a runner is available, rerun configuration/version, clean, debug assembly, unit tests, lint and APK artifact checks. Obtain readable logs before diagnosing any application/compiler failure. Do not treat the current failures as evidence of an Android source defect.


### CI after the register update
- The final documentation-only register revision is on HEAD `3b28abd564b3722a2f22f2ba603ddc28cf7143ad`. Its push-triggered Android CI run [43523807965](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43523807965), APK run [43523808167](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43523808167), and Code Quality run [43523807966](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43523807966) all concluded `failure`.
- Each final-HEAD job again completed with `runner_id=0`, no runner name, and an empty step list; all three runs have no artifacts. The documentation push therefore reproduced the same pre-step runner-allocation blocker and did not validate an Android build.


## 2026-11-15 — Project/Application account-scope hardening (source changes; runtime unverified)

### Selection and non-repetition
- Reviewed the latest 20 commits, the previous Login/authentication result, and the latest documented Firebase inventory and CI runner investigation. The recent Login batch was not repeated; the Firebase inventory and Gradle workflow work were also left untouched.
- Priority 0 shared Supabase schema/Auth/RLS foundations remain the prerequisite for cloud migration. The next ordered feature slices are Identity and account, then Projects and Applications. Identity was deliberately not repeated; this batch selected the Projects/Applications account boundary as the highest safe independent slice.
- AGENTS.md, agents.md, and the repository tree were checked; no repository agent instruction file exists.
- Starting main HEAD: 5347ee57bea727ca275b76acd6c3b15bdbc77b06. The eight implementation/test commits below are the only changes in this batch before this register update.

### Evidence-backed issues addressed
- ProjectsViewModel and DashboardViewModel fell back to getProjects() when there was no signed-in user. The local repository implementation of that method returned every Room project, without an account filter.
- getProjectsForUser(userId) trusted the caller-supplied ID on the legacy Firestore path rather than checking it against the app's Supabase session.
- Project lookup could fall back from a missing Firestore record to an unscoped Room lookup. Local application listing returned every application, and application creation did not verify that its parent project belonged to the active account.
- The app's AuthRepository is Supabase-backed, while the remaining Firestore path uses Firebase Auth. Treating those provider identities as interchangeable would be unsafe.

### Changes made on main
- Added ProjectIdentityGuard.kt: require the current Supabase user, reject requested user IDs that differ from that principal, and permit legacy Firestore access only when the Firebase UID exactly matches the Supabase user ID.
- Injected the Supabase AuthRepository and the optional configured Firebase Auth instance into CrashRepositoryImpl. No Firebase SDK or configuration was removed.
- Scoped project list, per-user list, project lookup, project creation and deletion to the active Supabase account. Local project and application reads now use the account's accessible project IDs; project/application creation rejects foreign owner IDs and applications whose parent project is not accessible.
- Removed signed-out fallback to the unscoped project query from Projects and Dashboard. The shared projectsForAuthenticatedUser flow returns an empty list without invoking the repository when the user ID is missing or blank.
- Added 10 focused unit tests across ProjectIdentityGuardTest.kt and AuthenticatedProjectFlowTest.kt.

### Files and commits
- app/src/main/java/com/example/data/repository/ProjectIdentityGuard.kt — a5693689e8748dab17318f39b6d5120f7cbb8fab
- app/src/main/java/com/example/presentation/viewmodel/AuthenticatedProjectFlow.kt — f475027f4ccbcc67867970b8e75a29317daa3a08
- app/src/test/java/com/example/data/repository/ProjectIdentityGuardTest.kt — 3e34e78d9810840427d43c5d7853f233969646aa
- app/src/test/java/com/example/presentation/viewmodel/AuthenticatedProjectFlowTest.kt — c5729fc99164e7492f99768c57c3888780ba92a7
- app/src/main/java/com/example/data/repository/CrashRepositoryImpl.kt — 21df6675900b231d4e68dad9cd0a3b90a0407f67
- app/src/main/java/com/example/di/DependencyProvider.kt — 2c4db5626c37cc506ec459fefd283833d7fbae80
- app/src/main/java/com/example/presentation/viewmodel/ProjectsViewModel.kt — 2fb1ad1af2754cf1650342ce9a3c05ba8768e855
- app/src/main/java/com/example/presentation/viewmodel/DashboardViewModel.kt — c0e44c7939b3970a0ba730fc85da5928f2e78606
- Code HEAD before this register update: c0e44c7939b3970a0ba730fc85da5928f2e78606.

### Verification and blockers
- GitHub compare from starting HEAD to code HEAD reports 8 commits, 8 changed files, 0 behind, with the expected project/repository/DI/ViewModel/test scope. Re-fetched all changed files and reviewed the resulting source.
- Added unit tests: 7 identity-guard cases and 3 authenticated-flow cases. They were not executed; no test pass is claimed.
- Local environment check: Java 21 is present; gradle --version and adb version both return exit code 127 (command not found); ANDROID_HOME and ANDROID_SDK_ROOT are unset; there is no checkout of this repository or Gradle wrapper in the execution container. Gradle tests, Android compilation, APK build, emulator and device tests were not run.
- Latest inspected GitHub Actions runs on the preceding HEAD 5347ee57 all failed before a runner started: Android CI, APK and Code Quality jobs each reported runner_id=0, empty steps, no artifacts, and job-log downloads returned BlobNotFound. This is a runner-allocation/log-availability blocker, not evidence of a Kotlin compilation failure. The new code still requires CI verification after runner allocation is restored.
- Post-change CI on code HEAD c0e44c7939b3970a0ba730fc85da5928f2e78606: Android CI run [43646952975](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43646952975), APK run [43646953010](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43646953010), and Code Quality run [43646952996](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43646952996) all concluded failure. Each job again had runner_id=0 and an empty step list; log downloads returned BlobNotFound and all three runs had no artifacts. These runs therefore did not execute the added tests or Android build.

- The dedicated Supabase project hgfgbjomwhogpswpqusk currently reports INACTIVE. Read-only table inspection returned “Database is possibly not accessible at the moment”; migration listing timed out. No project restore, schema change, RLS change, production-data operation or credential change was attempted. This blocks verification and the actual Firestore-to-Supabase persistence migration.
- This slice does not complete the Supabase migration. Team repository identity, project-member/security/audit operations, crash-data migration, negative RLS tests and end-to-end behavior remain open. Legacy Firestore access for the scoped project/application methods now fails closed if the Firebase principal is missing or differs from the Supabase account.

### Next selection
- Last domain worked: Projects/Applications account-scoped repository access.
- Next domain: Priority 1 AI provider reliability — inspect the AIModels provider validation/state path and fix one independently evidenced issue. Do not return immediately to Projects/Applications or Login. Revisit the Supabase migration only after the dedicated project is active and its schema/ownership conventions can be verified safely.

## 2026-11-15 — Android CI recheck after Project/Application account-scope batch

### Repository and non-overlap
- Re-fetched `main` at HEAD `00656640f4b3bb03e846de32323d63d7ebc0cfa1` and reviewed the latest 20 commits.
- Compared with the previous Android-build investigation HEAD `5347ee57bea727ca275b76acd6c3b15bdbc77b06`: 10 commits ahead, 0 behind, and 9 changed paths. The other agent's Project/Application account-scope batch consists of eight implementation/test commits: `a5693689`, `f475027f`, `3e34e78d`, `c5729fc9`, `21df6675`, `2c4db562`, `2fb1ad1a`, and `c0e44c79`; the other two commits update this register. The eight code/test paths are `CrashRepositoryImpl.kt`, `ProjectIdentityGuard.kt`, `DependencyProvider.kt`, `AuthenticatedProjectFlow.kt`, `ProjectsViewModel.kt`, `DashboardViewModel.kt`, `ProjectIdentityGuardTest.kt`, and `AuthenticatedProjectFlowTest.kt`.
- Reviewed those diffs and re-fetched the current affected source/test files. No Gradle, version-catalog, manifest, resource, or workflow file changed in that batch. No file from the other agent's implementation was edited or overwritten.
- Read this complete task register (1,438 lines) and scanned the current tracked tree (277 files) for `AGENTS.md`, `agents.md`, and agent-instruction documents. None are present in the repository tree.

### GitHub Actions — current HEAD
- Android CI/CD run [43647080636](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43647080636): attempt 1 failed; rerun attempt 2 also completed with failure.
- APK run [43647080668](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43647080668): attempt 1 failed; rerun attempt 2 also completed with failure.
- Code Quality run [43647080631](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43647080631): attempt 1 failed; rerun attempt 2 also completed with failure.
- The final attempt-2 jobs (`132609643336`, `132609648609`, `132609657770`) each completed in the pre-step phase with `runner_id=0`, an empty `runner_name`, and `steps=[]`. All three job-log downloads returned `BlobNotFound` (HTTP 404); all three runs contain no artifacts.
- These failures prove that no runner was assigned and no checkout, Gradle, Kotlin compilation, unit test, lint, or APK packaging step ran. They are not evidence of an Android source/compiler failure. The upstream reason for runner allocation failure (hosted-runner entitlement/usage, billing, repository/organization policy, or GitHub service allocation) remains unavailable from the returned job data.
- The push-triggered Android CI, APK, and Code Quality workflows are the relevant runs for this HEAD. PR Validation and Release are not established as having run for this push. No workflow is reported as successful or still queued after the final poll.

### Static Android build configuration review
- `gradle/wrapper/gradle-wrapper.properties` pins Gradle `9.3.1`, but `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are absent. The five current workflows use `gradle/actions/setup-gradle@v4` with explicit Gradle `9.3.1` and invoke `gradle`, not `./gradlew`; the missing wrapper therefore prevents the requested wrapper commands locally but does not explain the observed pre-runner CI failures.
- Current versions: AGP `9.1.1`, Gradle `9.3.1`, Kotlin/KGP `2.2.10`, Compose compiler plugin `2.2.10`, Compose BOM `2024.09.00`, KSP `2.3.5`, `compileSdk 36`, `targetSdk 36`, and `minSdk 24`. The workflows select JDK 17 or 21. Android's AGP 9.1.1 compatibility table specifies Gradle 9.3.1, Build Tools 36.0.0, JDK 17, and KGP 2.2.10; the declared core versions align with that table. This is static compatibility evidence, not a successful Gradle configuration/build.
- Manifest-referenced resources are present. The tree contains 26 unit-test source files and one instrumented-test source file. Test execution, dependency resolution, SDK/build-tools availability on a runner, Compose compilation, R8/ProGuard execution, and lint remain unverified.
- The current `app/build.gradle.kts`, version catalog, repositories, manifest, resource tree, ProGuard rules, and all five workflows were inspected. No concrete dependency-resolution, Kotlin/Java, manifest/resource, test, or lint error is established without an executed build log.

### Local environment and exact command results
- `java -version` — exit code `0`; OpenJDK `21.0.12.1`.
- `gradle --version` — exit code `127`; `gradle: command not found`.
- `adb version` — exit code `127`; `adb: command not found`.
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset. There is no local checkout of this repository in the execution container.
- `./gradlew --version`, `./gradlew clean`, `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, and `./gradlew lintDebug` were not run: there is no checkout, wrapper script/JAR, Gradle installation, or Android SDK. No exit code is claimed for commands that were not invoked.
- `./gradlew assembleRelease` was not run. No release-signing environment was verified, and no signed release artifact is claimed.

### Changes and remaining blocker
- No Android source, Gradle configuration, dependency version, manifest/resource, or workflow change was made in this recheck. This entry records the inspected source HEAD `00656640f4b3bb03e846de32323d63d7ebc0cfa1`; the only intended change in this batch is this documentation update.
- No APK artifact, successful Gradle configuration, compilation, unit-test run, lint result, or release build is available.
- Highest blocker: GitHub Actions runner allocation. A repository/organization owner must inspect Actions usage/billing, hosted-runner access, and organization/repository runner policies, then restore runner allocation. Once a runner is assigned, rerun configuration, clean, debug assembly, unit tests, lint, and APK artifact inspection before diagnosing any application-level build failure.


## 2026-11-15 — Android signup email-confirmation deep link (source change; runtime pending)

- Root cause: Android signup called Supabase Auth REST `/auth/v1/signup` without `redirect_to`, so Supabase fell back to the project's Site URL (the reported `localhost:3000` destination).
- Added `SUPABASE_EMAIL_CONFIRMATION_REDIRECT_URI` using the app's existing registered scheme/host: `com.aistudio.crashintelligence.vyfrip://auth/email-confirmation`.
- Signup requests now pass this URI as `redirect_to`; AndroidManifest registers the matching callback path. Existing `MainActivity` already forwards cold-start and `onNewIntent` deep links into `AppNavigation`.
- Added strict callback parsing: accept only the exact app scheme/host/path, no Supabase error, `type=signup`, and a non-empty access token. The callback token is not persisted or logged; the app returns to Login with an explicit Arabic/English “email confirmed; sign in” message. Email confirmation does not silently create a local authenticated session.
- Added unit coverage for a valid signup callback and wrong scheme, wrong type, missing token and error cases.
- Files: `AuthRedirects.kt`, `AndroidManifest.xml`, `AuthRepositoryImpl.kt`, new `EmailConfirmationLinkParser.kt`, `MainViewModel.kt`, `LoginScreen.kt`, English/Arabic `strings.xml`, new `EmailConfirmationLinkParserTest.kt`.
- Commits: `f0f2dc0e`, `28259cd7`, `ac504b1c`, `842f52d5`, `5d3190cd`, `d227bc4d`, `4e21c3e2`, `7aee8fef`, `b72cb05a`.
- Supabase project `vibe-coding` (`hgfgbjomwhogpswpqusk`) was verified as `ACTIVE_HEALTHY`. The Supabase dashboard URL allow-list was not changed by the available project connector. Owner action remains: add the exact callback URI above under Authentication → URL Configuration → Redirect URLs (Additional Redirect URLs). Do not change Confirm email.
- Verification limits: source writes and GitHub re-fetch are being checked; no Gradle build, unit-test execution, installed APK test, real confirmation email click, or live Supabase redirect test has yet been performed. GitHub combined status returned no status entries for the latest source/test commit; this is not a passing CI result.
- Repository agent instructions: root `AGENTS.md`, `.github/AGENTS.md`, and `app/AGENTS.md` returned 404; no agent file was available at those paths.
- Next: user adds the redirect allow-list entry, install a build containing these changes, perform a fresh signup and click the email confirmation link on the same Android device; then verify it opens this app and shows the confirmation message. If Supabase emits a different callback parameter shape, capture only parameter names (never token values) and adjust the parser accordingly.


## 2026-11-15 — Login continuation run: email-confirmation callback handoff recheck

### Continuity and selection
- Re-read the current task register and reviewed the latest 20 commits on `main`. Current HEAD at the start of this run: `f48cde58215b94d513e2768b65f765320df2f439`.
- The latest open Login work is the Supabase signup email-confirmation deep link. Login remains not design-complete under the mandatory visual review gate; this run did not mark it complete or move to another screen.
- Checked root `AGENTS.md`, `.github/AGENTS.md`, and `app/AGENTS.md`; all three paths returned 404. No instructions were available at those paths.
- User previously confirmed in this conversation that the exact app callback URI had been added under Supabase Authentication → URL Configuration → Additional Redirect URLs. This supersedes the earlier owner-action note that the allow-list entry was still pending. Do not change Supabase Auth settings again without a new reason.

### Source recheck
- Re-fetched and inspected `LoginScreen.kt`, `MainViewModel.kt`, `EmailConfirmationLinkParser.kt`, `AuthRepositoryImpl.kt`, `AndroidManifest.xml`, and the English/Arabic authentication strings.
- The source consistently passes `com.aistudio.crashintelligence.vyfrip://auth/email-confirmation` as signup `redirect_to`, registers the matching Android VIEW intent filter, accepts only the exact custom scheme/host/path with `type=signup`, no Supabase error, and a non-empty access token, and displays the confirmation message on Login without persisting/logging the callback token or silently creating a session.
- Re-fetched `EmailConfirmationLinkParserTest.kt`; it covers the valid callback and rejection of a wrong route, wrong type, missing token and error response. These are tests present in source, not tests executed in this run.
- No new source defect was established by this static recheck, so no speculative source change was made.

### Verification status
- PASS — GitHub source re-fetch and static consistency review for the callback URI, manifest filter, parser and UI message.
- PASS — repository commit history query returned the latest 20 commits; current inspected HEAD is `f48cde58215b94d513e2768b65f765320df2f439`.
- BLOCKED — GitHub combined status returned no status entries for this commit, and the available commit-workflow lookup returned no workflow runs. This is not a passing CI result.
- BLOCKED — no Gradle build, unit-test execution, APK installation, device/emulator test, fresh signup, or real email-link click was performed in this run. No Android SDK/Emulator execution environment was attached to this task context.
- BLOCKED — rendered Login review in Arabic RTL and English LTR, compact-screen/IME review, and accessibility inspection remain outstanding.

### Explicit continuity fields
- **الشاشة النشطة:** Login / Identity.
- **آخر خطوة مكتملة:** Static source consistency recheck of the signup email-confirmation deep-link implementation; user-confirmed redirect allow-list setup recorded.
- **معيار القبول التالي:** Verify the callback end-to-end on an installed Android build using a fresh signup.
- **الخطوة التالية الدقيقة للتشغيل القادم:** Obtain/install an APK built from a commit containing `f48cde58215b94d513e2768b65f765320df2f439` or later; create a fresh test account; open the confirmation email on the same Android device; tap the confirmation link; verify Android opens this app and Login shows the localized “email confirmed; sign in” message. Record only callback parameter names and outcome if debugging is needed—never capture or log token values. Then run the parser unit tests and Gradle build when a functioning runner is available, and continue Login's visual redesign/review without switching screens.
- **العائق/المتطلب الخارجي:** An installed build and access to a test mailbox plus a physical Android device or emulator are needed for the real callback test. A functioning Gradle/Android SDK runner is also needed for build and test execution.
- **Screen status:** OPEN — no end-to-end, build, automated test, or visual sign-off evidence has been produced by this run.

## 2026-11-15 — Login accessibility contrast correction (source change; CI/device verification pending)

### Screen and finding
- **الشاشة النشطة:** Login / Identity — remains OPEN; this is the first ordered identity screen and its visual redesign/runtime review is still incomplete.
- Confirmed a measurable accessibility issue in the primary action: white foreground text and spinner were rendered over the bright cyan action background (`#28D7F2`), which does not meet WCAG AA's 4.5:1 contrast target for normal text.
- This is a focused accessibility correction, not a claim that Login's overall visual design is complete.

### Implemented changes
- Added the shared `VibeDesignTokens.Color.OnCyan` foreground token (`#050914`).
- Changed the Login primary action label and loading spinner to use `OnCyan`, preserving the existing cyan action surface and disabled-state styling.
- Added `LoginAccessibilityContrastTest.kt`, which calculates the relative-luminance contrast ratio between `OnCyan` and `Cyan` and asserts WCAG AA 4.5:1 minimum.

### Files and commits
- `app/src/main/java/com/example/ui/theme/VibeDesignTokens.kt` — `e569b049587bb9ea27463a03af375a2fc55e68cc`.
- `app/src/main/java/com/example/presentation/ui/screens/LoginScreen.kt` — `bf518380d2ec782738c172ecdd933fe577d412e6`.
- `app/src/test/java/com/example/LoginAccessibilityContrastTest.kt` — `6e92eecabf99debc3a461db84b565c67ae90f333`.
- These are three sequential commits on `main`; the documentation update is a separate following commit.

### Verification status
- PASS — static color review identified the low-contrast white-on-cyan combination and the code now references the dark shared foreground token for both label and progress indicator.
- PASS — a regression test has been added to assert the token pair meets the 4.5:1 threshold; this test has not yet been executed in this run.
- PASS — the latest pre-change Android CI run on source-equivalent HEAD `5cc51e6634f06bb8f21b003aaed09324ff4dd163` completed successfully: Unit Tests and Debug APK build steps both succeeded. This is baseline CI evidence only; it does not validate the new commits.
- BLOCKED — CI for the new commits is pending at the time of this entry; do not treat the newly added test or current source as CI-verified until a run for the resulting HEAD completes successfully.
- BLOCKED — no Android emulator/device is attached to this run, so rendered Login review, Arabic RTL/English LTR screenshots, compact-screen/IME and TalkBack inspection remain outstanding.

### Explicit continuity fields
- **الشاشة النشطة:** Login / Identity.
- **آخر خطوة مكتملة:** Corrected the primary action foreground contrast and added a WCAG AA contrast regression test.
- **معيار القبول التالي:** Run the new contrast regression test and full Android CI on the latest HEAD, then inspect the rendered Login screen in Arabic RTL and English LTR.
- **الخطوة التالية الدقيقة للتشغيل القادم:** Read latest HEAD and this entry; inspect CI run triggered by commit `6e92eecabf99debc3a461db84b565c67ae90f333` or a later docs commit. Confirm `:app:testDebugUnitTest` and `:app:assembleDebug` pass. Then obtain a debug APK and run Login on an Android emulator/device at compact and standard sizes in both Arabic RTL and English LTR; capture fresh screenshots and inspect IME, focus order, field errors, loading, success and error announcements. Continue Login visual refinement without switching screens.
- **العائق/المتطلب الخارجي:** Android emulator or physical device with installable debug APK is required for visual and accessibility runtime evidence. If CI is not yet complete, wait for the workflow result; do not infer test success from workflow creation.
- **Screen status:** OPEN — accessibility contrast source correction implemented; full Login visual redesign, functional end-to-end review, RTL/LTR visual evidence and device sign-off remain outstanding.

### 2026-11-15 follow-up — loading indicator consistency
- A post-update source re-fetch found the primary button label had switched to `OnCyan`, but its loading spinner still used white. Corrected the spinner to use the same shared `OnCyan` token.
- Additional source commit: `4f277d7d3ddb59c72dc8f38810fae18f504233c9` (`fix(login): align loading indicator contrast`).
- This correction supersedes the earlier partial statement: both the primary action label and loading spinner now reference `OnCyan`.
- The test and build workflows for the current source HEAD still require a completed result; pending/in-progress runs are not PASS.



## 2026-11-15 — Login replacement recovery-link state reset

### Confirmed defect
- LoginScreen keyed its recovery-form effect only on isReadyForNewPassword.
- If a second valid recovery deep link arrived while the first link's password form was already open, the ViewModel cancelled the old one-time credential and installed the new one, but MutableStateFlow suppressed the identical true state. Compose therefore did not rerun the effect, leaving the previous password and confirmation values visible in the form for the new credential.

### Implemented correction
- Added a monotonically increasing linkGeneration to PasswordRecoveryUiState, incremented for each incoming auth link.
- Keyed Login's recovery-state effect on both readiness and link generation. A replacement link now retriggers the existing transition logic, clears password/confirmation values, hides password visibility, and updates the form even when readiness remains true.
- Added a ViewModel regression test proving that a second link received while password setup is already open advances the observable generation without losing the ready state.

### Files
- app/src/main/java/com/example/presentation/viewmodel/MainViewModel.kt
- app/src/main/java/com/example/presentation/ui/screens/LoginScreen.kt
- app/src/test/java/com/example/MainViewModelPasswordRecoveryTest.kt

### Verification
- Regression test `replacingRecoveryLinkWhilePasswordSetupIsOpenAdvancesUiGeneration` executed and passed in both Android CI runs for source commit `cccfbb2b1c8d7837b605dfa0594c302767966bcd`.
- PASS — Android CI/CD run [43754233799](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43754233799): `testDebugUnitTest` and `assembleDebug` succeeded; Debug APK artifact uploaded.
- PASS — APK workflow run [43754233814](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43754233814): `testDebugUnitTest`, `assembleDebug`, and `assembleRelease` succeeded; Debug and Release APK artifacts uploaded.
- PASS — Code Quality run [43754233794](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43754233794): `lintDebug` succeeded and lint report artifact uploaded.
- BLOCKED — no Android emulator/device is attached to this execution environment. Installed-app behavior, screenshots, TalkBack, compact-screen/IME behavior, and rendered Arabic RTL / English LTR review remain outstanding; Login remains OPEN.


## 2026-11-16 — Login provider-error disclosure regression fix

### Selection and independent review
- Re-read the full task register and the latest 20 commits on `main`. Current code HEAD before this fix: `e5c476540555f4d3a6d92b7ba08eae7904cb2e46`.
- Reviewed the other agent's intervening changes rather than overwriting them: Supabase auth error-body parsing / Login diagnostics (`3225facb`, `50678f31`, `781bd114`), JSON-compatible user metadata (`63309009`, `d677c57c`), app branding (`c091fd4c`, `4fba3a1c`, `a3439921`), and Android Keystore-generated AES-GCM IV (`63119439`, `e5c47654`).
- Checked root, `.github/`, `app/`, and the relevant data/repository/remote/viewmodel/screen/test instruction paths for `AGENTS.md` / `agents.md`; none exist at the checked paths.
- The latest explicit screen continuity remains Login / Identity. Its functional/auth work has recent changes, but its required visual redesign and rendered device review remain open; this run addressed a newly introduced security/privacy regression in that same flow.

### Confirmed regression
- The typed, localized authentication result contract had intentionally stopped forwarding raw provider exception text to Login (see the 2026-11-13 localized-auth entry).
- Commit `781bd114` added `AuthActionState.errorDetail`, copied `Throwable.message` from sign-in/sign-up failures into that field, and made `LoginScreen` prefer it over the localized `AuthUiMessage`. Since `AuthRepositoryImpl` now parses Supabase error bodies into exception messages, raw server/provider detail could be displayed directly to the user and bypass the localized error mapping.
- The existing sign-in regression test asserted the typed message key but did not assert that the raw provider detail was absent, so it did not detect this reintroduction.

### Changes implemented on main
- Removed `errorDetail` from `AuthActionState` and stopped copying provider exception messages into the UI state for sign-in and sign-up.
- Restored `LoginScreen` to render only the localized string resource mapped from `AuthUiMessage`; the repository may retain exception context internally, but it is not exposed in the user-facing state.
- Strengthened the sign-in regression test and added a sign-up regression test asserting that raw provider details are absent from the ViewModel UI state.

### Files and commits
- `app/src/main/java/com/example/presentation/viewmodel/MainViewModel.kt` — `83caa5c05c79c6ca8afd00efc86eb68f16184f9f`.
- `app/src/main/java/com/example/presentation/ui/screens/LoginScreen.kt` — `f5519e691f9b1d004985ff846f943e973aafcba4`.
- `app/src/test/java/com/example/MainViewModelAuthMessageTest.kt` — `6f29f62604b1bc4f6dab1f05022965a06c167ab6`.

### Verification and limitations
- PASS — re-fetched each changed file after its write and confirmed the saved content exactly matches the intended replacement.
- PASS — static search/review confirms `errorDetail` and `visibleActionError` are removed from the ViewModel and Login screen; the displayed error path is again the typed localized mapping.
- PASS — the regression test source now checks sign-in and sign-up provider-detail non-disclosure.
- NOT RUN — the new/updated JUnit tests, Gradle compilation, lint and APK build were not executed in this environment. Java 21 is available, but `gradle` and `adb` are absent, Android SDK environment variables are unset, and no repository checkout exists in the execution container.
- BLOCKED — combined commit status for the prior HEAD returned no entries; the connector's commit-workflow lookup returned no runs because it exposes PR-triggered runs only, and the available GitHub connector does not provide a repository-wide push-run listing action. The current workflows are configured to run on pushes to `main`, but no run or result for the new HEAD is claimed. The latest documented successful CI remains the earlier run on `cccfbb2b`, not this fix.
- BLOCKED — no emulator/device is attached, so rendered Login review, RTL/LTR screenshots, IME, TalkBack and real signup/recovery callback checks remain outstanding. Login remains OPEN.

### Explicit continuity fields
- **الشاشة النشطة:** Login / Identity.
- **آخر خطوة مكتملة:** Removed raw Supabase/provider error text from Login UI state and added sign-in/sign-up non-disclosure regression assertions.
- **الخطوة التالية الدقيقة:** Continue the Login screen's substantive visual/UX redesign from the current source; do not repeat the error-detail fix. Inspect the current Compose hierarchy and shared theme tokens, implement one cohesive visual/interaction improvement with focused tests, then use the next available successful CI run and a real Android emulator/device for rendered RTL/LTR and accessibility verification.
- **العوائق:** No local Gradle/Android SDK/ADB or repository checkout; no push-run listing/status result for this HEAD is exposed by the available connector; no emulator/device. Keep the screen open until these gates are actually satisfied.
- **Last domain worked:** Login / Identity — authentication error presentation and privacy.
- **Next domain:** Login / Identity — substantive visual/UX redesign (not another authentication error-message rewrite).
- **Screen status:** OPEN — this is a targeted regression fix, not screen completion.

 
## 2026-11-16 — AIModels multi-tenant model isolation

### Selection and blocker handling
- Login remains OPEN. Its remaining visual redesign and rendered RTL/LTR/accessibility sign-off require an Android emulator/device. This execution environment has no Android SDK or ADB and no attached device; further UI styling without rendered feedback would be speculative. The Login gate is therefore BLOCKED for device verification, not PASS. Work moved to the next independent, security-relevant screen task rather than repeating Login source edits.
- Selected AIModels because its provider/model data is used by AIModels, ModelTeams, TeamChat and agent orchestration, and its current persistence queries can be hardened without changing production services or schemas.

### Confirmed security defect
- `AIProviderDao.getAllAvailableModels()`, `getModelsByProvider(providerId)` and `getModelById(id)` queried `ai_models` without joining `ai_providers.userId`.
- The repository's provider list and provider-by-id queries were user-scoped, but model list/detail reads were not. A signed-in account could therefore receive another account's model names/metadata in AIModels, ModelTeams, TeamChat and orchestration model lists. A foreign model ID could also be stored as the current user's default model.
- `deleteProvider(userId, id)` ignored `userId` in the DAO delete query; a caller with another provider's ID could delete that provider and its models. The ID is random, but authorization must not rely on identifier secrecy.

### Changes implemented
- Added mandatory `userId` parameters to model list/detail/provider queries and joined each model query to `ai_providers` on provider ID with an explicit owner predicate.
- Scoped provider deletion and model deletion to both `userId` and provider ID.
- Made default-model selection reject models not owned by the requesting user; default-model lookup and inference/failover model selection now use the same scoped reads.
- Passed the authenticated user ID through AIModels, ModelTeams, TeamChat, TeamDetail estimation, TeamOrchestrator and DAG orchestration instead of requesting a global model catalogue.
- Updated all repository/orchestrator test doubles to the user-scoped contracts and added a two-account regression test covering model listing, provider model reads, default-model lookup/selection, and foreign-provider deletion.
- No Room entity, schema, migration, Supabase project, production data, Auth configuration or secrets were changed.

### Verification
- Source-level review confirms all production model read/delete paths in this feature now include the authenticated user ID. The new regression test is present in source.
- NOT RUN — Gradle/JUnit and Room/Robolectric tests were not executed locally. The container has Java 21 but no Gradle, ADB, Android SDK or repository checkout.
- CI for the preceding Login privacy fix completed successfully on source commit `6f29f62604b1bc4f6dab1f05022965a06c167ab6`: Android CI, Debug/Release APK build and Code Quality all succeeded. That result does not validate this new AIModels change; the new push-triggered workflows must complete separately.
- BLOCKED — no emulator/device is available for AIModels UI, provider setup, real API connectivity, RTL/LTR or TalkBack review. Live provider credentials are not present and no real provider request was sent.

### Explicit continuity fields
- **الشاشة النشطة:** AIModels / Provider and model configuration.
- **آخر خطوة مكتملة:** Enforced per-user ownership for model reads, default selection, provider deletion and agent model discovery; added cross-account regression coverage.
- **الخطوة التالية الدقيقة:** Inspect the push-triggered Android CI, APK and lint runs for the new AIModels commit and read their job logs/artifacts. Fix any compile/test/lint failures. Then review AIModelsScreen/ViewModel state transitions (loading, empty, provider test, sync failure/success) and ensure the UI never labels a provider CONNECTED without a successful live test.
- **العوائق:** Emulator/device verification remains unavailable. Live AI provider connectivity is not tested because no user provider credentials are available.
- **Last domain worked:** AIModels — user-scoped provider/model data and authorization.
- **Next domain:** AIModels — truthful provider connection status and UI state handling.
- **Screen status:** OPEN — security/data isolation was hardened, but UI behavior, real provider setup/connectivity, visual RTL/LTR and device sign-off remain unverified.


## 2026-11-16 — AIModels provider lifecycle and account-state hardening

### Prior verification rechecked
- The previous AIModels multi-tenant isolation commit `87f81559902d186b81f5ac123ae27c8f71418c36` was rechecked against its push-triggered workflows.
- Android CI run [#43769713268](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43769713268) completed successfully: `testDebugUnitTest` and `assembleDebug` both reported BUILD SUCCESSFUL; the debug APK artifact was uploaded.
- APK run [#43769713249](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43769713249) completed successfully: `testDebugUnitTest`, `assembleDebug` and `assembleRelease` reported BUILD SUCCESSFUL. Debug and release APK artifacts were uploaded. The release workflow is configured to continue on error for the release build, but this run's log explicitly reports BUILD SUCCESSFUL.
- Code Quality run [#43769713233](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43769713233) completed successfully; `lintDebug` reported BUILD SUCCESSFUL and a lint report artifact was uploaded.
- The three workflows ran on commit `87f81559902d186b81f5ac123ae27c8f71418c36`. This validates that prior commit only, not the changes in this entry. No emulator/device UI test was part of those workflows.

### Confirmed AIModels lifecycle defects
- The ViewModel used `currentUser.filterNotNull()`; signing out never emitted a cleared screen state, so the previous account's provider/model data remained visible in memory and UI.
- Initial provider/model load failures had no durable inline retry state; transient operation failures could expose raw exception text through the snackbar.
- Provider actions did not consistently share operation state; the add-provider path launched model sync in a second coroutine, allowing the loading indicator to finish before synchronization did.
- A failed model sync could leave an earlier CONNECTED status unchanged. The provider-test success text was stored in the error field.
- The API-key field displayed the entered key in clear text, provider deletion had no confirmation, and the guide contained an inert external-link button.

### Changes in this batch
- Reworked AIModels account observation so logout clears all user-scoped provider/model state and account switching replaces the previous account's data.
- Added a retryable load-error state, operation-aware loading, stale default-model filtering and safe user-facing error messages that do not echo provider diagnostics.
- Kept provider creation and initial model sync in one operation; failed sync/test actions now set the provider status to ERROR instead of leaving stale CONNECTED state. Success feedback is distinct from errors.
- Added delete confirmation, masked API-key entry with an explicit visibility toggle, disabled duplicate actions while an operation is active, localized provider status labels, and explicit loading/empty/retry states. Removed the non-functional guide button.
- Added ViewModel regression tests for logout clearing, account switching, provider test failure, model sync failure and load retry.
- No production schema, Auth settings, external services, provider credentials or secrets were changed.

### Verification at commit time
- Tests have been added but have not yet been executed against this batch. The push-triggered Android CI, APK build and Code Quality workflows must be checked on the new commit before any test/build success is recorded.
- Emulator/device rendering, RTL/LTR and TalkBack verification remain BLOCKED because this execution environment has no Android SDK, ADB or attached device.
- Live provider credentials are not available in this execution context; no real provider request was sent.

### Explicit continuity fields
- **Active screen:** AIModels / provider and model configuration.
- **Last completed step:** Verified prior AIModels isolation CI and APK artifacts; implemented account-state clearing, provider operation handling, secret-field masking, delete confirmation and retry states.
- **Exact next step:** Inspect all push-triggered Android CI, APK and Code Quality runs for the latest commit, inspect failed job logs if any, fix compile/test/lint failures, then continue with provider catalog correctness and model availability behavior. Do not claim visual completion without emulator/device evidence.
- **Last domain worked:** AIModels — account isolation and provider lifecycle.
- **Next domain:** AIModels — provider catalog freshness, unsupported provider behavior and live-connection truthfulness.
- **Screen status:** OPEN — source and regression coverage updated; new CI/device verification pending.


## 2026-11-16 — AIModels catalog integrity and provider failure taxonomy

### Confirmed defects and corrections
- OpenRouter pricing was compared as literal strings, so equivalent zero values such as `0.000000` were not recognized as free; the category also depended only on prompt price, incorrectly labelling a model with paid completion as FREE.
- Provider sync fetched the OpenRouter catalog twice (once inside `testConnection`, then again for persistence), increasing request volume and making verification and stored catalog potentially refer to different responses.
- Model catalog refresh deleted cached rows and inserted replacements in separate DAO calls. The replacement is now a guarded Room `@Transaction`; empty lists and rows belonging to another provider are rejected before deletion.
- Invalid or non-finite pricing rows are skipped rather than being cached with a zero-cost fallback. Free classification now parses numeric prices and requires both prompt and completion price to be zero.
- Provider failures now use a typed, non-sensitive classification for invalid credentials, access denial, quota, rate limit, timeout, empty response, network, service outage and unsupported providers. Rate-limit failures set the existing RATE_LIMITED status; user-facing messages do not echo provider response bodies or exception details. Coroutine cancellation is rethrown rather than converted into an ordinary provider failure.

### Files and commits
- `app/src/main/java/com/example/data/local/AppDatabase.kt` — added guarded transactional model-catalog replacement.
- `app/src/main/java/com/example/data/repository/AIModelRepositoryImpl.kt` — one-fetch sync, numeric pricing validation and typed provider failure mapping.
- `app/src/main/java/com/example/domain/model/AIProviderFailure.kt` — provider failure contract.
- `app/src/main/java/com/example/presentation/viewmodel/AIModelsViewModel.kt` — localized failure messages and RATE_LIMITED state.
- `app/src/test/java/com/example/AIFreeSystemValidationTest.kt` and `AIModelsViewModelTest.kt` — catalog, account boundary, failure classification and UI-state regression coverage.
- Catalog batch source commit: `b9c2e038139b761009c0c754f828e6aacf7739fc`.
- Follow-up compile correction: `427b75cf9efe4f3f17e4f51ec42d8bef3308dd08` (added the missing remote model import after CI identified the unresolved reference).

### Verification evidence
- PASS — pre-change HEAD `fbaa39fc1b5dc6f3789f862f896aad41974cd049`: Android CI, APK and Code Quality workflows all completed successfully, with debug APK, release APK and lint artifacts.
- FAIL — first catalog commit `b9c2e038139b761009c0c754f828e6aacf7739fc`: Android CI and Code Quality exposed a compile error, `Unresolved reference: OpenRouterModelData`, in the new member-extension receiver. The import was added in `427b75cf9efe4f3f17e4f51ec42d8bef3308dd08`.
- PASS — all push-triggered workflows for corrected HEAD `427b75cf9efe4f3f17e4f51ec42d8bef3308dd08` completed successfully: Android CI [43773820294](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43773820294), APK [43773820498](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43773820498), Code Quality [43773820325](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43773820325). Debug/Release APK and lint artifacts were produced.
- NOT RUN locally — this execution environment has no repository checkout, Gradle installation, Android SDK or ADB. CI is the available compilation/test/lint evidence.
- BLOCKED — no emulator/device is attached and no live OpenRouter/Gemini credentials are available in this run. Rendered RTL/LTR, TalkBack, device behavior and real user-provider connectivity remain unverified. AIModels remains OPEN.

### Continuity
- **Active screen:** AIModels / provider and model configuration.
- **Last completed step:** catalog refresh now reuses one verified OpenRouter response, parses prices numerically, rejects malformed price rows and replaces Room catalog rows transactionally; provider failures are classified without exposing raw provider diagnostics.
- **Exact next step:** inspect the final Android CI, APK and Code Quality runs for the latest HEAD, download/read the lint artifact if warnings are reported, fix any compile/test/lint failures, then continue AIModels model availability and UI verification. Do not mark the screen complete without real provider credentials and emulator/device RTL/LTR evidence.
- **Last domain worked:** AIModels — provider catalog integrity and failure handling.
- **Next domain:** AIModels — remaining model availability and screen interaction/accessibility verification.
- **Screen status:** OPEN — source and regression tests updated; latest CI/device/provider-runtime verification pending.


### 2026-11-16 — provider failure compile follow-up
- CI for the first typed-failure batch exposed one remaining call site in `AIModelsViewModel.addProvider` that still referenced the removed `markProviderError` method. The call now passes the actual sync failure to `markProviderFailure`, so rate limits retain the RATE_LIMITED status and other failures use ERROR.
- The first compile failure on catalog commit `b9c2e038139b761009c0c754f828e6aacf7739fc` was the missing `OpenRouterModelData` import; fixed in `427b75cf9efe4f3f17e4f51ec42d8bef3308dd08`. All workflows for corrected commit `427b75cf9efe4f3f17e4f51ec42d8bef3308dd08` completed successfully: Android CI [43773820294](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43773820294), APK [43773820498](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43773820498), and Code Quality [43773820325](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43773820325).
- Typed-failure commit `c04a345e854981471f05105e03cb69b312e68ff2` then failed `:app:testDebugUnitTest` before tests ran because of the stale `markProviderError` reference. The follow-up source commit `a064a28f3a24dc72365602e2a42e923f3ed61b8b` corrected nullable HTTP status classification but did not yet include the ViewModel call-site fix.
- **Current next step:** inspect CI for the new HEAD after this correction; do not infer test success from the earlier commit. If the tests expose further issues, fix them before continuing AIModels.


## 2026-11-16 — Gemini model availability and cost safety

### Confirmed defect
- The Gemini provider catalog and connection probe still used `gemini-1.5-flash` / `gemini-1.5-pro`, which are not current supported choices. The repository also logged every Gemini request with `estimatedCost = 0.0`, so paid-tier usage was under-reported.
- Google’s current model documentation lists `gemini-3.8-flash` and `gemini-3.5-flash-lite` as stable models. The current pricing page shows free-tier availability but paid-tier rates of $0.75/$3.75 per million input/output tokens for Gemini 3.8 Flash (through 2026-12-31), and $0.30/$2.50 for Gemini 3.5 Flash-Lite.
- Because the app cannot determine whether a user’s Google API project is on the free or paid billing tier, both Gemini models are conservatively excluded from the guaranteed-free filter. This avoids claiming that a potentially billable model is free. Their published paid-tier rates are used for cost estimates.

### Files changed in this batch
- `app/src/main/java/com/example/data/repository/AIModelPricing.kt` — current stable Gemini catalog and shared token-cost calculation.
- `app/src/main/java/com/example/data/repository/AIModelRepositoryImpl.kt` — use Gemini 3.8 Flash for connection checks, replace retired model IDs, and calculate Gemini/OpenRouter usage cost from actual token counts.
- `app/src/main/java/com/example/presentation/ui/screens/AIModelsScreen.kt` — explain conservative Free Only behavior, distinguish free/paid models, show input/output prices and give an actionable empty state.
- `app/src/test/java/com/example/AIFreeSystemValidationTest.kt` — stable model ID, pricing, Free Only and cost-calculation regression tests.

### External source of truth
- Gemini supported model IDs and stability: https://ai.google.dev/gemini-api/docs/models
- Gemini 3.8 Flash model limits: https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash
- Gemini 3.5 Flash-Lite model limits: https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite
- Paid/free tier pricing: https://ai.google.dev/gemini-api/docs/pricing

### Verification and continuity
- PASS — all workflows for previous HEAD `5855e8bcedc654989974fc84031f514533e64cd4` completed successfully: Android CI [43774283867](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43774283867), APK [43774283846](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43774283846), and Code Quality [43774283843](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/43774283843). Debug APK, Release APK and lint artifacts were produced. The lint HTML reports 166 warnings across the app; no AIModels files were listed among the reported locations.
- FAIL — HEAD `2d4a21f44f3fff1b20f94ccc1d427e1bad96ec53` failed unit-test compilation because the new test called the DAO's one-argument `getAllAvailableModels(userId)` with a nonexistent `freeOnly` argument. The test now reads the DAO flow and asserts the free-only predicate directly in commit `8a61c0b602564949ef0e66936042c65dad205371`.
- NOT RUN locally — no local Gradle/Android SDK/ADB or emulator is available. Live Gemini credentials are not available, so no live provider request was made.
- **Active screen:** AIModels — provider/model setup and truthful cost/availability.
- **Exact next step:** inspect the final workflows for the new HEAD, then remove or clearly isolate the remaining synthetic Gemini 1.5 references in the ModelTeams/TeamValidation demo path; do not present those hardcoded demo reports as live usage.
- **Screen status:** OPEN — source, UI and regression tests updated; emulator/RTL/LTR/accessibility and real-provider checks remain unverified.


## 2026-11-16 — unsupported provider status is explicit

- Added `AIProviderStatus.UNSUPPORTED`; typed unsupported-provider failures no longer collapse into generic ERROR.
- Existing database entries for HUGGING_FACE or CUSTOM providers are now displayed as UNSUPPORTED regardless of a stale stored CONNECTED value. Their test/sync actions are disabled and the card explains why; deletion remains available.
- Added a ViewModel regression test for the unsupported failure status and safe Arabic message.
- Test correction: commit `2d4a21f44f3fff1b20f94ccc1d427e1bad96ec53` failed `:app:compileDebugUnitTestKotlin` because the DAO exposes only `getAllAvailableModels(userId)`. The incorrect two-argument test calls were replaced in `8a61c0b602564949ef0e66936042c65dad205371`; that commit's CI is still pending at this point.
- **Active screen:** AIModels remains OPEN. The latest code batch adds explicit unsupported-provider behavior; the next step is to inspect CI for the current HEAD and then continue provider-model selection and device verification.


- Follow-up for unsupported-provider UI: the `isSupported` flag is declared in the provider-card column scope so both the status badge and disabled action controls use the same supported-provider check. This corrects the scope before the current CI run; validate the new HEAD rather than counting the prior commit.


- CI follow-up: commit `a3e904aa87d83fc3407d17ab990b8ef84787db36` exposed an `isSupported` scope error in `AIProviderCard`; the first attempted move placed the declaration in the cost-settings card. It has now been moved into the `AIProviderCard` column itself, where the status badge, warning and disabled actions share its scope. Recheck the current HEAD before treating this UI change as compiled.


## 2026-11-16 — AIModels Vibe Design System slice, legacy `gemini-1.5` cleanup, and Sandbox isolation

### Observed gaps before changes
- `AIModelsScreen.kt` still used generic uncustomized Material 3 cards/surfaces instead of the `VibeDesignTokens` / `JetBrainsMono` Cosmic Cyber engineering style (`docs/DESIGN_SYSTEM.md`), lacked an explicit unauthenticated gate card when `user == null`, lacked Compose `testTag` hooks on interactive controls/lists, and used deprecated `Icons.Filled.FactCheck` / `ArrowBack`.
- `AIModelsViewModel` cleared state on sign-out, but did not expose an explicit `isAuthenticated: Boolean` flag or surface a clear authentication-required error when provider/settings mutations were invoked while signed out.
- `AIModelRepositoryImpl.prioritizeModelsForTask` still boosted retired `gemini-1.5-flash`, while `TeamValidationEngineImpl` and `ValidationReportTab` contained synthetic `gemini-1.5-flash` / `gemini-1.5-pro` references and sample billing/masked-key strings without a prominent `SANDBOX DEMO` disclaimer.
- Missing `.env` values in `.env.example` (`KEY=`) caused `secrets-gradle-plugin` to emit invalid `BuildConfig.java` syntax during local Gradle test runs, and `DependencyProvider.supabaseRetrofit` needed an HTTP fallback when `BuildConfig.SUPABASE_URL` is blank/non-HTTP.

### Concrete changes implemented
- **Missing-config & Supabase guards:** populated valid placeholder values in `.env.example` (`YOUR_GITHUB_CLIENT_ID`, `YOUR_GITHUB_CLIENT_SECRET`, `YOUR_SUPABASE_PUBLISHABLE_KEY`, `https://your-project.supabase.co`) and added URL scheme validation with safe fallback in `DependencyProvider.kt` (`supabaseBaseUrl`).
- **Retired model cleanup & TeamValidation sandbox isolation:**
  - `app/src/main/java/com/example/data/repository/AIModelRepositoryImpl.kt` — `prioritizeModelsForTask` now prefers `GeminiModelCatalog.CONNECTION_MODEL_ID` (`gemini-3.8-flash`) and `gemini-3.5-flash-lite` for planning/root-cause diagnosis.
  - `app/src/main/java/com/example/data/validation/TeamValidationEngineImpl.kt` — replaced synthetic `gemini-1.5-*` identifiers with `sandbox/gemini-3.8-flash` / `Gemini 3.8 Flash (Paid Tier Fixture)` / `Google Gemini 3.8 Flash [Sandbox Demo]`, replaced fake key strings with `[SANDBOX_KEY_FIXTURE]`, and added explicit `SANDBOX DEMO` headers/labels in `generateValidationReport` and `buildMarkdownReport`.
  - `app/src/main/java/com/example/presentation/ui/screens/validation/ValidationReportTab.kt` — added prominent `validation_sandbox_disclaimer` banner clarifying that report tokens/costs are isolated demo fixtures rather than live billing.
- **AIModels ViewModel & UI slice (`VibeDesignTokens`):**
  - `app/src/main/java/com/example/presentation/viewmodel/AIModelsViewModel.kt` — added `isAuthenticated: Boolean` to `AIModelsUiState` and `requireAuthenticatedUserId()` so unauthenticated mutations surface `AUTH_REQUIRED_ERROR` rather than failing silently.
  - `app/src/main/java/com/example/presentation/ui/screens/aimodels/AIModelsComponents.kt` — extracted modular components styled with `VibeDesignTokens` (`SpaceBackground`, `SpacePanel`, `SpacePanelRaised`, `Cyan`, `Violet`, `Success`, `Error`) and `JetBrainsMono`: `VibeSpaceBackdrop`, `VibeSectionHeader`, `AIAuthRequiredCard`, `AISettingsDashboard` (`TELEMETRY • LOCAL LOGS`), `AICostSettingsCard`, `AIModelSelectionSection`, `AIModelItem`, `AIProviderCard` (keystore-safe `runCatching { SecurityUtils.maskSecret(...) }` fallback for JVM tests, paired icon+text `ProviderStatusBadge` with `Modifier.semantics(mergeDescendants = true) {}` for all 5 `AIProviderStatus` values, 48dp minimum touch targets), and `AddAIProviderDialog`.
  - `app/src/main/java/com/example/presentation/ui/screens/AIModelsScreen.kt` — refactored into `AIModelsScreen` + stateless `AIModelsScreenContent(...)` with `WindowInsets.safeDrawing`, cosmic gradient + backdrop, `widthIn(max = 720.dp)` tablet centering, unauthenticated gate, retry/delete confirmation dialogs, and `testTag` hooks (`ai_models_lazy_column`, `ai_models_add_provider_button`, `delete_provider_confirm_button`, etc.).
- **Regression & UI tests:**
  - `app/src/test/java/com/example/AIModelsViewModelTest.kt` — added `isAuthenticated` assertions and unauthenticated `addProvider` error feedback assertion.
  - `app/src/test/java/com/example/AIFreeSystemValidationTest.kt` — added `prioritization prefers gemini-3_8-flash over generic models for diagnosis`.
  - `app/src/test/java/com/example/AIModelTeamsEndToEndValidationTest.kt` — asserted `SANDBOX DEMO` report headers, updated demo model names, and `assertFalse(md.contains("gemini-1.5"))`.
  - `app/src/test/java/com/example/presentation/ui/screens/AIModelsScreenTest.kt` — added Robolectric Compose UI tests covering the unauthenticated gate, authenticated provider badges (`CONNECTED`, stale DB `UNSUPPORTED` override), disabled Test/Sync on unsupported providers, delete confirmation flow, and Add Provider dialog credential trimming/validation.

### Verification evidence
- **Legacy model grep:** `grep -rnI --exclude-dir=.gradle --exclude-dir=build "gemini-1\.5" app/src || true` returned zero matches in `app/src/main` and only the negative regression assertion `assertFalse(md.contains("gemini-1.5"))` in `AIModelTeamsEndToEndValidationTest.kt:267`.
- **Unit & Robolectric tests (PASS — 35/35 passed, 0 failed, 0 skipped):**
  - Command: `gradle :app:testDebugUnitTest --tests "com.example.ExampleRobolectricTest" --tests "com.example.AIModelsViewModelTest" --tests "com.example.AIFreeSystemValidationTest" --tests "com.example.AIModelTeamsEndToEndValidationTest" --tests "com.example.presentation.ui.screens.AIModelsScreenTest"`
  - Result: `BUILD SUCCESSFUL in 2m 25s` (`AIModelsScreenTest` 3/3 PASSED, `AIFreeSystemValidationTest` 11/11 PASSED, `AIModelTeamsEndToEndValidationTest` 11/11 PASSED, `AIModelsViewModelTest` 7/7 PASSED, `ExampleRobolectricTest` 3/3 PASSED).
- **Android APK compilation (PASS):** `compile_applet` succeeded (`Build succeeded - the applet is compiled`).
- **Not run / blocked:**
  - Full-project unit test suite across all unrelated test files was not executed in this turn (targeted verification focused on the 5 suites above).
  - No Android emulator, ADB, or physical device is available in the execution environment; visual screenshot inspection, Arabic RTL / English LTR layout check, font-scale/IME behavior, TalkBack announcement on-device, and live OpenRouter/Gemini API calls remain unverified.

### Continuity
- **Active screens:** `AIModels` and `TeamValidation` remain **`[ ]` OPEN** until rendered device/emulator visual verification and live API credential checks are completed.
- **Next step:** Visually inspect `AIModelsScreen` and `TeamValidation` (`ValidationReportTab`) on the streaming emulator / physical device (RTL & LTR, small width, font scaling, live OpenRouter/Gemini keys), then proceed to the next screen slice in Priority 1 (`Login` / `Projects` / `Dashboard`).


## 2026-11-16 — Projects, Applications & Dashboard context slice (`VibeDesignTokens`, config guards & UI tests)

### Observed gaps before changes
- `DashboardScreen.kt` and `ProjectsScreen.kt` relied on uncustomized Material 3 cards/surfaces and screen-local colors instead of the shared `VibeDesignTokens` Cosmic Cyber palette and modular panel structure.
- `DashboardViewModel` and `ProjectsViewModel` lacked explicit `isAuthenticated`, `isCloudConfigured`, and active project filter / mutation state flags, and `DashboardViewModel` could invoke `CrashRepository` reads before an authenticated user was present.
- `DependencyProvider` passed `SupabaseProjectDataSource` unconditionally even when `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_PUBLISHABLE_KEY` contained template placeholders, and `Screen.Applications` did not land directly on the Applications tab inside `ProjectsScreen`.
- `ProjectsScreen.kt` had grown beyond 1,100 lines, and `ApplicationCard` squeezed status chips next to the delete button on compact widths.

### Concrete changes implemented
- **Supabase configuration guard & DI:**
  - `app/src/main/java/com/example/data/remote/supabase/SupabaseConfig.kt` — added `isConfigured(...)` and `normalizeBaseUrl(...)` to detect `.env.example` placeholders (`PLACEHOLDER_URL`, `PLACEHOLDER_PUBLISHABLE_KEY`, `YOUR_*`) and validate HTTP(S) hosts.
  - `app/src/main/java/com/example/di/DependencyProvider.kt` — normalized `supabaseBaseUrl` and gated `SupabaseProjectDataSource` injection on `SupabaseConfig.isConfigured()`.
- **ViewModels & reactive project context:**
  - `app/src/main/java/com/example/presentation/viewmodel/DashboardViewModel.kt` — added `isAuthenticated`, `projects`, `selectedProjectId`, and `isCloudConfigured` to `DashboardUiState`; skipped `CrashRepository` queries when `user == null`; added `selectProject(projectId?)` and `refresh()` via `_refreshTrigger.flatMapLatest` with `CancellationException` preservation.
  - `app/src/main/java/com/example/presentation/viewmodel/ProjectsViewModel.kt` — added `isAuthenticated`, `isCloudConfigured`, and `isMutating`; enforced signed-out mutation guards (`requireAuthenticatedUser`), `_refreshTrigger` reload after mutations, `trim()` validation, and safe error propagation.
- **Cosmic Cyber UI modularization & navigation:**
  - `app/src/main/java/com/example/ui/theme/VibeDesignTokens.kt` — added shared `Warning` color token (`#FFB74D`).
  - `app/src/main/java/com/example/presentation/ui/screens/dashboard/DashboardComponents.kt` & `DashboardScreen.kt` — extracted modular cosmic components (`CosmicDashboardBackdrop`, `DashboardPanel`, `DeveloperSessionHeader`, `ProjectContextFilterRow`, `DashboardStatCard`, `DashboardToolCard`, `DashboardNotificationsSheetContent`), added `WindowInsets.safeDrawing`, `widthIn(max = 720.dp)`, clickable 2x2 KPI cards, auth-required gate, retry banner, and Compose `testTag`s.
  - `app/src/main/java/com/example/presentation/ui/screens/projects/ProjectsComponents.kt`, `ProjectsApplicationsTab.kt`, `ProjectsAdminTabs.kt`, `ProjectsDialogs.kt`, & `ProjectsScreen.kt` — split 1,165-line monolith into focused modules with `StatusChip` merged semantics, `FlowRow` status layout, 48dp delete targets, `initialTab` support, and auth/error states.
  - `app/src/main/java/com/example/presentation/navigation/AppNavigation.kt` — routed `Screen.Applications` to `ProjectsScreen(..., initialTab = 1)`.
- **Unit & Robolectric Compose UI tests:**
  - `app/src/test/java/com/example/data/remote/supabase/SupabaseConfigTest.kt` (4 tests)
  - `app/src/test/java/com/example/presentation/viewmodel/FakeRepositories.kt`, `DashboardViewModelTest.kt` (3 tests), `ProjectsViewModelTest.kt` (4 tests)
  - `app/src/test/java/com/example/presentation/ui/screens/DashboardScreenTest.kt` (2 Compose UI tests), `ProjectsScreenTest.kt` (3 Compose UI tests)

### Verification evidence
- **Targeted Unit & Robolectric tests (PASS — 26/26 passed, 0 failed):**
  - Command: `gradle :app:testDebugUnitTest --tests "com.example.presentation.viewmodel.AuthenticatedProjectFlowTest" --tests "com.example.data.repository.ProjectIdentityGuardTest" --tests "com.example.data.remote.supabase.SupabaseConfigTest" --tests "com.example.presentation.viewmodel.DashboardViewModelTest" --tests "com.example.presentation.viewmodel.ProjectsViewModelTest" --tests "com.example.presentation.ui.screens.DashboardScreenTest" --tests "com.example.presentation.ui.screens.ProjectsScreenTest"`
  - Output: `BUILD SUCCESSFUL in 2m 24s`.
- **Android APK compilation (PASS):** `compile_applet` succeeded (`Build succeeded - the applet is compiled`).
- **Hardcoded legacy color scan (PASS):** `grep` across modified dashboard/projects files returned zero legacy hex colors (`CLEAN_NO_HARDCODED_LEGACY_COLORS`).
- **Not run / blocked:**
  - Full-project unit test suite and `lintDebug` were not executed in this turn.
  - No Android emulator/ADB device is attached in the container; rendered Arabic RTL / English LTR layout, font scaling, TalkBack traversal, and live Supabase PostgREST project CRUD remain unverified.

### Continuity
- **Active screens:** `Dashboard`, `Projects`, and `Applications` remain **`[ ]` OPEN** until rendered device/emulator UI verification and live Supabase integration are completed.
- **Next step:** Visually verify `Dashboard`, `Projects`, and `Applications` on an emulator/device with live Supabase credentials; proceed to the `ModelTeams`, `CreateTeam`, and `TeamChat` collaboration slice.


## 2026-11-16 — ModelTeams, CreateTeam & TeamChat engineering & collaboration slice (`VibeDesignTokens`, reactive state & tests)

### Observed gaps before changes
- `ModelTeamsScreen.kt` hid `TeamTemplates` whenever custom teams list was empty (`uiState.teams.isEmpty()`), leaving a blank canvas without templates or guidance.
- `AppNavigation.kt` for `Screen.CreateTeam` launched a disconnected coroutine on one ViewModel reference and navigated to `ModelTeamsScreen` separately instead of passing `initialCreateMode = true`.
- `AIModelTeamRepositoryImpl.kt` used a one-shot `flow { emit(localTeams.values...) }` that never emitted dynamic updates after `createTeam` / `updateTeam` / `deleteTeam` / `logExecution`, relied on `FirebaseAuth.getInstance()` directly without Supabase `ProjectIdentityGuard` validation, swallowed Firestore write exceptions in silent `catch (_: Exception) {}` blocks, and lacked scoped `ownerId == uid` filtering.
- `TeamChatViewModel.kt` contained a dead duplicate `?: run { ... }` block in `loadInitialData()`, used unscoped `crashRepository.getProjects()`, did not cancel or track previous `loadTeamExecutions` collectors when switching teams, and leaked execution state across sign-out.
- `TeamActivityLogPanel.kt` fabricated two hardcoded architectural consensus/disagreement strings when execution points were empty.
- Several UI panels (`LiveTeamCollaborationPanel`, `TeamChatScreen`, `TeamActivityLogPanel`, `TeamResultReviewPanel`, dialogs) used hardcoded green colors (`0xFF2E7D32`, `0xFF1B5E20`) and overflowing horizontal `Row` chip rows on narrow widths.

### Concrete changes implemented
- **Repository, Auth & Security Boundary:**
  - `app/src/main/java/com/example/data/repository/AIModelTeamRepositoryImpl.kt` — bound identity via `AuthRepository` + `ProjectIdentityGuard` (`requireSupabaseUserId` + `requireLegacyFirebaseIdentityMatch`), added `currentUserIdProvider` for JVM tests, converted local caching to reactive `MutableStateFlow<Map<String, ModelTeam>>` (`localTeamsState`) and `MutableStateFlow<Map<String, TeamExecution>>` (`localExecutionsState`), eliminated exception swallowing on Firestore mutations, enforced user ownership checks across reads/updates/deletions, and rethrew `CancellationException`.
  - `app/src/main/java/com/example/di/DependencyProvider.kt` — wired `provideAuthRepository(context)`, `getFirestore(context)`, `getFirebaseAuth(context)`, and Room `aiProviderDao()` into `AIModelTeamRepositoryImpl`.
- **ViewModels & Reactive Workspace Coordination:**
  - `app/src/main/java/com/example/presentation/viewmodel/ModelTeamsViewModel.kt` — added `isAuthenticated`, `isCloudConfigured`, `connectedProviderIds`, `isMutating`, `statusMessage`, `nameError`, `membersError`, and `pendingDeleteTeam` to `ModelTeamsUiState`; observed `authRepository.currentUser` reactively with `flatMapLatest` (clearing all data on sign-out); combined `aiModelRepository.getProviders` to track connected vs configured models; added trimmed validation, duplicate name guards, duplicate `(modelId, role)` rejection, draft preservation on write failure, collision-safe naming in `useTemplate()`, and delete confirmation flow.
  - `app/src/main/java/com/example/presentation/viewmodel/TeamChatViewModel.kt` — added `isAuthenticated`, `isCloudConfigured`, and `isLoading` to `TeamChatUiState`; observed `authRepository.currentUser` with `flatMapLatest` (clearing execution and cancelling timer/history jobs on sign-out); scoped projects strictly via `crashRepository.getProjectsForUser(user.id)`; cancelled previous execution history listeners on team switches; added `.catch` (with `CancellationException` rethrow); and enforced non-blank input validation and sign-in checks on all mutations.
- **Cosmic Cyber UI Modularization (`VibeDesignTokens` & <500 lines per file):**
  - `app/src/main/java/com/example/presentation/ui/screens/teams/ModelTeamsComponents.kt` & `ModelTeamsRosterSection.kt` & `CreateTeamSettingsSections.kt` — modularized cosmic cyber cards, badges (`teams_sync_badge`, `StatusPill` with merged semantics), `TeamCard` (distinguishing connected vs configured models, 48dp touch targets), templates roster (always visible), empty roster fallback, and `DeleteTeamConfirmDialog`.
  - `app/src/main/java/com/example/presentation/ui/screens/teams/CreateTeamWizard.kt` — controlled wizard with inline error messages, per-model `متصل`/`مهيّأ` badge, role picker dialog, `imePadding()`, and `testTag` hooks.
  - `app/src/main/java/com/example/presentation/ui/screens/ModelTeamsScreen.kt` — added `initialCreateMode: Boolean = false`, stateless `ModelTeamsScreenContent(...)`, `WindowInsets.safeDrawing`, `widthIn(max = 720.dp)`, `BackHandler` in create mode, and auth card.
  - `app/src/main/java/com/example/presentation/navigation/AppNavigation.kt` — routed `Screen.CreateTeam` directly to `ModelTeamsScreen(..., initialCreateMode = true)`.
  - `app/src/main/java/com/example/presentation/ui/screens/teamchat/TeamChatHeaderAndInstructions.kt` & `TeamChatScreen.kt` — extracted `TeamLiveStatusBar` (showing member counts, Free AI Only, LOCAL indicator, and timer), `TeamInstructionsTabContent`, `TeamChatScreenContent(...)`, `WindowInsets.safeDrawing`, `imePadding()`, `widthIn(max = 760.dp)`, and auth gate.
  - `app/src/main/java/com/example/presentation/ui/screens/teamchat/TeamActivityLogPanel.kt` — removed fabricated consensus/disagreement strings, added honest empty-state messages (`empty_consensus_message`, `empty_disagreements_message`), converted filter `Row` to `LazyRow`, and applied `VibeDesignTokens`.
  - `app/src/main/java/com/example/presentation/ui/screens/teamchat/TeamChatInputPanel.kt` — added `verticalScroll`, replaced overflowing button `Row`s with `FlowRow`, and preserved all interactive `testTag`s.
  - `app/src/main/java/com/example/presentation/ui/screens/teamchat/LiveTeamCollaborationPanel.kt`, `TeamResultReviewPanel.kt`, and dialogs — cleaned up legacy colors (`0xFF2E7D32`), replaced static rows with `LazyRow`, and bound all accents to `VibeDesignTokens.Color.*`.
- **Unit & Robolectric Test Suites Added:**
  - `app/src/test/java/com/example/data/repository/AIModelTeamRepositoryImplTest.kt` (5 tests) — reactive dynamic streams, cross-user SecurityException isolation, unauthenticated fail-closed check, execution streaming, Room usage filtering.
  - `app/src/test/java/com/example/presentation/viewmodel/ModelTeamsViewModelTest.kt` (3 test suites) — auth isolation, sign-out wipe, blank/duplicate input validation, draft preservation on error, template collision naming, delete confirmation.
  - `app/src/test/java/com/example/presentation/viewmodel/TeamChatViewModelAuthAndScopeTest.kt` (3 test suites) — unauthenticated state handling, scoped user projects, prompt validation, execution history cancellation on team switch.
  - `app/src/test/java/com/example/presentation/ui/screens/ModelTeamsScreenTest.kt` (4 Compose UI tests) — auth card, visible templates on empty roster, readiness badge, delete confirmation dialog, CreateTeamWizard validation/save.
  - `app/src/test/java/com/example/presentation/ui/screens/TeamChatScreenTest.kt` (3 Compose UI tests) — auth card, live status bar with LOCAL badge, prompt submission, honest empty consensus/disagreement display.

### Verification evidence & build constraints
- **Static consistency & legacy color audit (PASS):**
  - Command: `grep -rnE "0xFF2E7D32|0xFF1B5E20|Color\.White" ...`
  - Output: `CLEAN_LEGACY_COLORS` (0 occurrences across all modified/created screens and panels).
  - All source files conform to modularity guidelines (<500 lines per UI file).
- **Gradle compilation & test execution status (PAUSED per user instruction):**
  - Per explicit user mandate (`عدم تشغيل البناء إلا عند طلبي الصريح`), no Gradle build, `compile_applet`, `lint_applet`, or test runner commands were executed during this turn.
  - All test suites and production changes are written in source and ready for execution upon explicit user instruction.
- **Supabase Cloud Schema Prerequisite Note:**
  - `/supabase/migrations/` in the repository contains index migrations (`20261105224519_index_team_executions_project_id.sql`), but the initial DDL migration creating `model_teams` and `team_executions` tables is not present in local SQL files. Direct Supabase PostgREST team CRUD cannot be guessed or fabricated; `AIModelTeamRepositoryImpl` currently uses reactive caching with `ProjectIdentityGuard` and legacy Firestore compatibility until the team table DDL is provided.

### Continuity
- **Active screens:** `ModelTeams`, `CreateTeam`, and `TeamChat` remain **`[ ]` OPEN** until explicit build verification, unit tests, and rendered device/emulator checks are executed upon user request.
- **Next step:** Continue automatically with TeamDetail, TeamUsage and TeamValidation after recording external blockers; do not wait for a user command to run build or tests.



## 2026-11-17 — TeamDetail lifecycle, mutation integrity and recovery states

### Confirmed defects and implementation
- TeamDetail previously treated failed repository Result values as successful writes, so governance/member UI could claim a change that was not persisted.
- Team and execution collectors had no error boundary and did not clear state on sign-out or account switch.
- Estimation and execution failures could escape the ViewModel without user feedback; concurrent calls were not guarded.
- Reworked TeamDetailViewModel to observe the current owner's team collection reactively, scope it to the authenticated account, clear/cancel user-scoped work on sign-out/account change, preserve state on failed writes, validate task prompts, expose loading/mutation flags and map low-level failures to safe user messages.
- Updated TeamDetailScreen with explicit loading, signed-out, unavailable and retry states, Vibe design tokens, stable list keys, empty-member guidance, member removal confirmation and estimation progress.

### Files and commits
- app/src/main/java/com/example/presentation/viewmodel/TeamDetailViewModel.kt
- app/src/main/java/com/example/presentation/ui/screens/TeamDetailScreen.kt
- app/src/main/java/com/example/presentation/ui/components/TeamGovernanceDialog.kt
- app/src/test/java/com/example/presentation/viewmodel/TeamDetailViewModelTest.kt
- app/src/test/java/com/example/presentation/ui/screens/TeamDetailScreenTest.kt
- app/src/test/java/com/example/presentation/ui/components/TeamGovernanceDialogTest.kt
- Implementation commits: 692eb7769f2a5029de4e31e1fcd13f8046466ade, 983a780f915128b1b28041b53d510a6572d84643, 8747620160bd76f3d2c423204e39a21f1f738e22, 66baa8f5486304cec34bca671d4217e6fc576ba3, 68125a08ad9c86c66d73c060381abf3915e5540f, e288b3329f582c708619a43bbf2893be2437d04d and 09d76b5e25b56e3426eb2faa1b6990f8f7a9405b.

### Verification
- Added ten ViewModel regression tests covering signed-out isolation, sign-out clearing, live roster/configuration updates, cross-account team isolation, governance/member write failures and successes, estimation/execution errors, cancellation during sign-out, invalid team IDs and user-scoped model capability lookup.
- Added three TeamDetail Robolectric Compose tests for unavailable/retry, signed-out, and member details/capabilities/removal; added three governance-dialog Compose tests for invalid limits, save gating and valid values.
- On e288b332, Code Quality passed; Android CI and APK jobs failed at compileDebugUnitTestKotlin because of test-source issues (Compose text-input chaining and a missing AICapability import). Those test-source issues were corrected in 54e2b04. Runs on 54e2 and later heads were still in progress/queued at the last check; no final successful Gradle/test/APK result is claimed.
- No emulator/device screenshots, rendered RTL/LTR review or live team-backend/RLS validation are claimed. The live team stream remains the authenticated Firebase/local repository path until the missing team-table DDL/RLS contract is supplied.
- TeamDetail still uses the existing Firebase/local team repository path; Supabase team persistence is not claimed. Model capability metadata is read through the authenticated user's existing AIModelRepository.

### Continuity
- TeamDetail remains OPEN until final CI passes and rendered device verification is completed.
- Exact next step: inspect the three push-triggered workflows for the latest HEAD through completion and fix any reported failures. Then continue TeamUsage/TeamValidation; missing usage telemetry must remain unavailable rather than being rendered as zero.
### TeamDetail follow-up — member operations and governance validation
- Added a confirmed member-removal flow; member cards now expose configured model/provider identity, user-scoped model capabilities and member instructions.
- Hardened the shared TeamGovernanceDialog: invalid, negative or zero limits are rejected rather than silently replaced with old values; saving disables duplicate submission/dismissal and shows progress.
- TeamDetail binds dialog saving state to the repository mutation state.
- Added Compose regression coverage for member detail/removal and governance validation/saving.
- Additional files: app/src/main/java/com/example/presentation/ui/components/TeamGovernanceDialog.kt and app/src/test/java/com/example/presentation/ui/components/TeamGovernanceDialogTest.kt.
- Verification for this follow-up is pending the latest push-triggered Actions runs; no device or live backend verification is claimed.
## 2026-11-17 — TeamUsage measured telemetry and governance integrity

### Confirmed defects
- TeamUsage fabricated member rows for members with no usage records, defaulted every member to free, and used the member's name as a model name and provider ID fragments as provider names.
- The dashboard displayed zero totals and zero cost when telemetry was absent, and forced cost to 0.00 whenever Free Only was configured, even if persisted records showed paid usage.
- Unconfirmed cost was included in the confirmed-cost total; governance writes ignored Result.failure and the dialog closed immediately.
- Loading and repository failures were not caught, and the screen did not react to account sign-out or live team updates.

### Implementation
- Reworked TeamUsageViewModel to observe only the current user's team collection, clear data on sign-out/account change, fail closed for foreign/deleted teams, and surface retryable errors.
- Added 7-day, 30-day and all-time periods; metrics, member summaries, failover events and operation logs now use the selected period.
- Aggregation now uses persisted TeamUsageRecord metadata only. No member summary is invented without billable usage; confirmed and unconfirmed cost are separated; model/provider names are never fabricated. Successful records or records with measured tokens/cost count as billable; zero-token/zero-cost blocked or rate-limited attempts remain visible in the operation log but do not inflate consumption totals.
- The dashboard now distinguishes no telemetry from zero usage, labels estimated cost, warns if paid records appear under Free Only, and displays data freshness/provenance.
- Governance writes now inspect repository Result, preserve the previous settings on failure, and dismiss only after success.

### Files and commits
- app/src/main/java/com/example/presentation/viewmodel/TeamUsageViewModel.kt
- app/src/main/java/com/example/presentation/ui/screens/TeamUsageDashboardScreen.kt
- app/src/test/java/com/example/presentation/viewmodel/TeamUsageViewModelTest.kt
- app/src/test/java/com/example/presentation/ui/screens/TeamUsageDashboardScreenTest.kt
- Implementation commits: acbae1d72ec42c308eec05957e20aec1816102f8, 3cfd57a599cb5b335c6b7cebf131fb97d014ff02, 6166df724a4c17987481db34cec8ae84c8c8f8c8, 54e2b04f842251e03ea1fc52d5060f793015fbfb, 8dca8db81fa2ce5b7232d0c75385ce5318f3bae3, b10d33c6a643d27101daeaeb64c5fbc39057dac2 and 0fbf630f52b76c1e431f53f4b9a34d0182731679.

### Verification
- Added six ViewModel tests for absent telemetry, measured aggregation/cost provenance, period filtering, account/ownership isolation, blocked paid-attempt classification and failed/successful governance writes.
- Added three Compose tests for the no-telemetry state, unconfirmed cost label and retry state.
- CI/build/lint verification for the latest HEAD is queued; earlier runs on e288/6166 exposed test-source compile errors that were corrected in later commits. No final successful build/test/lint/APK result, device screenshot, live Firestore/Supabase billing validation or RLS test is claimed.

### Continuity
- TeamUsage remains OPEN until the final CI passes and the rendered screen is checked on an Android device/emulator.
- TeamUsage still reads the current authenticated Firebase/local team repository path. Supabase team-table DDL/RLS is absent from the repository, so production Supabase migration remains BLOCKED without inventing a schema.
- Exact next step: inspect the latest three Actions runs through final status, fix any failing step, then proceed to TeamValidation. Do not show estimated, blocked or missing usage as measured billing.
### Criterion status (no screen is closed by code presence alone)

| Screen | Criterion | State | Evidence / blocker |
|---|---|---|---|
| TeamDetail | Auth-scoped live team, roster, roles, model/provider, capabilities and instructions | IN_PROGRESS | Implementation and regression tests committed; CI not yet final |
| TeamDetail | Loading, signed-out, unavailable, retry and mutation failure handling | IN_PROGRESS | Compose/ViewModel tests added; CI not yet final |
| TeamDetail | Supabase team persistence and RLS verification | BLOCKED | Current implementation remains on Firebase/local repository; production schema/policy changes are prohibited without the required contract and approval |
| TeamDetail | Rendered RTL/LTR, device sizes and accessibility review | BLOCKED | No Android emulator/device available in this automation environment |
| TeamUsage | Auth-scoped telemetry, date periods, billable usage and cost provenance | IN_PROGRESS | Implementation and regression tests committed; CI not yet final |
| TeamUsage | Governance write confirmation, loading/error and no-data states | IN_PROGRESS | Result failures no longer publish success; Compose/ViewModel tests added |
| TeamUsage | Supabase usage/team persistence and RLS verification | BLOCKED | Current source still reads Firebase/local usage records; no production DDL/RLS changes made |
| TeamUsage | Rendered RTL/LTR, device sizes and accessibility review | BLOCKED | No Android emulator/device available in this automation environment |

### Persistent continuity
- Last area worked: TeamUsage telemetry and governance integrity.
- Next area: TeamValidation after the latest CI runs reach a final state; revisit TeamDetail/TeamUsage Supabase blockers when the project schema contract is available.
- Latest implementation HEAD before this continuity commit: 0fbf630f52b76c1e431f53f4b9a34d0182731679.

## 2026-11-17 — TeamValidation: remove fabricated evidence and make checks reversible

### Confirmed defects
- The validation engine marked synthetic in-memory scenarios as PASSED, including sequential/parallel/debate execution, failover, rate limits, Free Only, usage tracking, restart recovery and cross-user isolation.
- The generated report injected fictional provider/model names, token counts, a resolved conflict, a simulated 429 event, zero cost and three security/sandbox guarantees. Those values were not linked to real jobs or telemetry.
- The Vibe sandbox generated a fictional workspace/file/test sequence. The Crash sandbox displayed a fixed NullPointerException and an unexecuted patch as a verified fix.
- Several checks wrote temporary provider/team/usage records without a consistent cleanup contract. The usage test had no reversible deletion path.
- Single-test execution could overlap, user-scoped work was not cancelled on account changes, and the report was written only into app-private storage while the UI described it as exported.

### Implementation
- Replaced the simulated engine with repository-backed, reversible checks:
  - #1 creates, reads and deletes a temporary team.
  - #2 creates, reads and deletes temporary provider configuration records; it does not claim provider connectivity.
  - #3 performs an in-memory secret-encryption round trip with ephemeral values; it does not claim a real provider key was validated.
  - #4 performs a real connection check only for supported providers configured by the current account; if none are configured it is SKIPPED without sending a request.
  - #5 and #6 persist/read/delete temporary team roles and leader assignment.
  - #20 checks local AIModelRepository query scoping with a temporary provider and deletes it. Its evidence explicitly excludes Supabase RLS and Firestore rules.
- Checks #7–#19 now return SKIPPED with a reason until they can execute through a real TeamOrchestrator job, fault-injection adapter, measured usage source or process-restart harness. No synthetic fixture is promoted to PASS.
- Removed fabricated Vibe files, crash reports and patch diffs. Both sandbox views now show an explicit unavailable state and disable execution until a real isolated workspace/source is connected.
- Report defaults now fail closed: no contacted models, conflicts, rate-limit events, measured cost, Free Only guarantee, backend isolation or sandbox guarantee are invented. Local repository scoping is reported separately from backend RLS.
- Validation jobs are serialized, cancellation is propagated, account changes cancel user-scoped jobs and reset results, and the Markdown report is exported through Android's document picker rather than hidden app-private storage.

### Files and implementation commits
- `app/src/main/java/com/example/data/validation/TeamValidationEngineImpl.kt` — commits `d54afc9c850b0ae34da801ed71f566ee86ae131c`, `f90f91ccdd4e5eca2ff9361a1d0ffda4a59f37bf`, `0dc38c231976a7dbe0af43856c6f34e7fd28ff34`, `bca44a9b95ece5783478fab2bb2aa1e481e65cfb`.
- `app/src/main/java/com/example/domain/validation/TeamValidationModels.kt` — commits `37d572f51341b9a2833c97d268efa7e4bde2f7c8`, `8f135aa8482362feb67cb13269e262174b635a6d`.
- `app/src/main/java/com/example/di/DependencyProvider.kt` — commit `0625c68c5f595e70b02f4016df81135b2c15a88d`.
- `app/src/main/java/com/example/presentation/viewmodel/TeamValidationViewModel.kt` — commits `7386824ec3e81a8a11fa039a69fbe27121a76a59`, `a65ed571bf11e24faa8091d57e5430b4cb00c33d`.
- `app/src/main/java/com/example/presentation/ui/screens/TeamValidationCenterScreen.kt` — commit `8ee3b41d75f929bde1d5354a2e637fc16a9b6abf`.
- `app/src/main/java/com/example/presentation/ui/screens/validation/ValidationReportTab.kt` — commit `90ea50edc74d74875b8995063afaa00580e433fc`.
- `app/src/main/java/com/example/presentation/ui/screens/validation/ValidationMatrixTab.kt` — commit `10afd06ad11002667c9035a659d09ac23d9566dc`.
- `app/src/main/java/com/example/presentation/ui/screens/validation/VibeSandboxTab.kt` — commit `f2b7828b02703902720cbc3acd3458c605c7eb0c`.
- `app/src/main/java/com/example/presentation/ui/screens/validation/CrashSandboxTab.kt` — commit `8acf902eccf0114af347b7f2f322d5300c770f70`.
- `app/src/test/java/com/example/data/validation/TeamValidationEngineImplTest.kt` — commits `81c924744755af3e70f8187363894365147aa78b`, `076531511d41604864eb1521b1e5109fd951b447`.

### Tests and verification
- Added seven engine regression tests for full-suite truthfulness/cleanup, temporary team cleanup, temporary provider cleanup, missing-provider skip, orchestration skip, unavailable sandboxes and fail-closed report defaults.
- The tests have been added to source; they are not yet counted as passed.
- CI on commit `8eece53b85e01e19130ed0ac461f6f049237fb6e` exposed a Kotlin string-template syntax error in report cost formatting. The source was corrected in `bca44a9b95ece5783478fab2bb2aa1e481e65cfb`; final CI for that and subsequent HEADs is still pending.
- Earlier CI logs on the preceding TeamUsage head showed a Robolectric Maven artifact fetch failure; that is a separate network/dependency retrieval issue and is not treated as a source-test pass.
- No local Gradle/Android SDK, emulator or device is available in this automation environment. No APK build, rendered RTL/LTR review, screenshot, accessibility run or device test is claimed.
- No `AGENTS.md` / `agents.md` file was found in the repository tree at the inspected HEAD.
- Supabase project schema/RLS was not changed. Test #20 verifies local repository query scoping only; backend RLS/Firestore isolation remains unverified.

### TeamValidation criterion status

| Criterion | State | Evidence / blocker |
|---|---|---|
| Reversible repository checks for team creation, provider records, roles and leader assignment | IN_PROGRESS | Implemented with temporary-record cleanup; awaiting CI execution |
| Local secret-encryption round trip | IN_PROGRESS | Implemented with ephemeral values; awaiting CI execution |
| Real configured-provider connectivity | IN_PROGRESS | Calls testConnection for supported configured providers; SKIPPED when none are configured |
| Sequential/parallel/debate/hierarchical execution, handover, conflict detection and review (#7–#13) | BLOCKED | Requires real TeamOrchestrator jobs and versioned input/output evidence; synthetic results removed |
| Pause/resume, failover, 429 handling, Free Only enforcement, metered usage and process recovery (#14–#19) | BLOCKED | Requires real persisted execution, controlled fault injection, usage telemetry and restart harness; no synthetic writes retained |
| Vibe executable sandbox | BLOCKED | No isolated workspace capable of writing files and running tests is connected |
| Crash investigation sandbox | BLOCKED | No real crash source plus isolated patch/build/regression pipeline is connected |
| Report accuracy, progress states and user-selected Markdown export | IN_PROGRESS | Fail-closed report and Storage Access Framework export implemented; awaiting CI and UI verification |
| Rendered RTL/LTR, device sizes, accessibility and navigation verification | BLOCKED | No Android emulator/device available |

### Persistent continuity
- Last area worked: TeamValidation evidence integrity, reversible repository checks and report/export behavior.
- Next exact step: inspect the Actions runs for the latest main HEAD until terminal status; fix any compile/test/lint failures before adding more TeamValidation behavior. Then build a test-only real TeamOrchestrator execution fixture with deterministic provider adapters and persisted job evidence. Do not mark TeamValidation closed while #7–#19, either executable sandbox, backend isolation or device verification remains BLOCKED.


## 2026-11-17 — Debate orchestration evidence and execution persistence

### Confirmed defects
- Debate mode launched child jobs that appended to shared mutable lists and polled those lists every 500 ms. A failed child could abort the coroutine scope without a clear persisted result, and the polling loop was unnecessary.
- Proposal and critique text was not stored in `TeamExecution.messages`; only a generic “proposals collected” marker was persisted, so the job record could not prove what participants proposed or reviewed.
- The primary `TeamOrchestratorImpl.executeTaskWithContext` path ignored `Result` from `logExecution` and emitted state before confirming persistence. A repository write failure could therefore be presented as progress or completion.

### Implementation
- Replaced debate polling and shared mutable collections with structured `async/awaitAll` execution. Provider and member-status failures now propagate to the orchestration boundary instead of leaving a polling loop waiting.
- Serialized member-status writes around parallel inference because the current team repository replaces the full team document; concurrent status writes could otherwise overwrite each other's member state.
- Persisted each participant proposal, each critique, and the leader's consensus as typed `AgentMessage` records. Empty proposals, critiques, consensus, missing leaders, and leader-only teams fail explicitly.
- The primary execution path now checks each execution-log `Result` and persists before emitting that state. Cancellation is rethrown rather than converted into an ordinary failed execution.
- Added `TeamOrchestratorExecutionFixtureTest`, a test-only deterministic provider adapter plus recording repository around the actual `TeamOrchestratorImpl` and `DebateOrchestrator`. It covers persisted proposal/critique/consensus evidence, provider failure, missing participants, initial persistence failure, and a final persistence failure that must never emit COMPLETED.

### Verification boundaries
- The deterministic provider and recording repository exist only in unit tests. They do not call a real provider, use user API keys, write Supabase/Firebase production data, or prove production database durability.
- User-facing TeamValidation checks #7–#13 remain SKIPPED until real configured provider calls and production execution persistence can be verified. The fixture is orchestration regression coverage, not a PASS for those runtime checks.
- Before this change, main HEAD `44ef5ae50f06d4f619f2d3e86fda7723d6c44199` had terminal-success Actions runs: Android CI `43975154714`, APK build `43975154728`, and code-quality/lint `43975154852`. The APK workflow uploaded `CrashIntelligence-Debug-APK` and unit-test reports. These are baseline results, not verification of this new commit.
- Supabase project `hgfgbjomwhogpswpqusk` is currently ACTIVE_HEALTHY. Read-only inspection found RLS enabled on `public.model_teams` and `public.team_executions`; team policies are owner-scoped, and execution policies require both `user_id = auth.uid()` and ownership of the parent team. Both tables reported zero rows. No production DDL/DML, Auth, RLS, or settings changes were made.
- Live migration history includes `20261105201532 add_model_teams_and_executions`, but the repository currently contains only the later team-execution index migration under `supabase/migrations`. The Android `AIModelTeamRepositoryImpl` still uses Firebase/Room/local state and is not wired to the existing Supabase team tables. This is a source/schema drift to reconcile before claiming Supabase team persistence.
- No `AGENTS.md` or `agents.md` files exist in the current repository tree.

### Verification for this batch
- Commit `e6ebb88dd23930cd47ecbfc10d9f8dc569650138`: Android CI run `43977660630` completed SUCCESS; `testDebugUnitTest` and `assembleDebug` both completed successfully. Debug APK artifact `app-debug-apk` (artifact `14282691179`) was uploaded.
- APK workflow `43977660816`: unit tests, debug build, and `CrashIntelligence-Debug-APK` upload succeeded; artifacts `14282106142` (Debug APK) and `14280979341` (unit-test reports) exist. Release build was still IN_PROGRESS when this follow-up was prepared.
- Code Quality workflow `43977660615` had `lintDebug` IN_PROGRESS; do not mark lint PASS until the run reaches a terminal conclusion.
- This follow-up serializes member-status writes and adds a regression assertion for the maximum concurrent status writes. Its own CI runs must be checked to terminal state.
- No local Gradle execution, emulator/device run, rendered RTL/LTR review, or screenshot is claimed from this automation environment.

### Persistent continuity
- Last area worked: TeamValidation execution evidence, debate orchestration, and execution-log persistence.
- Next exact step: reconcile the live `model_teams` / `team_executions` schema with version-controlled migrations, then implement a Supabase-backed `AIModelTeamRepository` adapter using the existing authenticated session and owner RLS. Keep the production database read-only during development; add adapter contract tests with fake PostgREST responses, and do not fall back to Firestore or local memory while reporting a remote write as successful.


### CI packaging defect and safe handling
- APK run `43977660816` reached terminal SUCCESS, but its `assembleRelease` step internally failed with `UnrecoverableKeyException / BadPaddingException` while reading alias `vibe_release_key`. The following upload reported no release APK files. The workflow's `continue-on-error: true` masked this failure; only the Debug APK and unit-test reports were actually uploaded.
- The signing payload is not modified. The workflow now validates the keystore, alias, and private-key password with `keytool` before exporting signing variables. Invalid or absent signing credentials select an explicitly named unsigned release artifact; a real release build failure is no longer suppressed, and artifact upload fails if no APK exists.
- This is a workflow-only fix and does not change GitHub secrets, Android signing credentials, production services, or release distribution.

## 2026-11-18 — Supabase team/execution adapter and fail-closed persistence

### Implementation
- Added an authenticated PostgREST contract for public.model_teams and public.team_executions, using the dedicated Vibe Coding Supabase session and publishable key only.
- Added owner-scoped team reads/updates/deletes, owner-bound creates, user-scoped execution reads, and execution upserts. Requests include explicit owner_id / user_id filters as defense in depth; the user's JWT remains the authorization credential and RLS remains authoritative.
- Added JSONB mapping for team members/settings/governance and execution steps, memory, messages, usage summaries, failovers, estimation, attachments and consensus evidence.
- Added a separate Supabase auth/session provider for Supabase data sources without changing the app-wide Firebase login provider during this migration slice. The team repository selects Supabase only when the current app identity matches a valid stored Supabase session; a mismatched stored Supabase session is rejected rather than silently falling back to another account.
- Removed production in-memory success fallbacks for team and execution CRUD. Local in-memory state remains available only when enableRemoteBackend=false (test/local mode). Team usage records remain Room-local because the inspected public schema has no team_usage_records table.
- No production DDL/DML, Auth configuration, RLS, storage policy, Firebase configuration, or secret was changed.

### Tests and verification
- Added SupabaseTeamDataSourceTest coverage for owner scoping, nested JSONB mapping, owner spoof rejection, authenticated execution upsert, expired-session rejection and delete-without-representation failure.
- Added a repository regression test proving production reads/writes do not report an in-memory team as persisted when no remote backend is available.
- Implementation commit: d521f6d461f51adfabe1d29d6cd454eade76dfc7.
- Android CI/CD run 44184564227 completed successfully: 277 tests, 0 failures, 0 errors and 4 skipped; Debug APK build passed.
- APK workflow run 44184564243 completed successfully: Debug APK and signed Release APK artifacts uploaded (14374006447 and 14375185146).
- Code Quality run 44184564464 completed successfully. Android lint completed, but its report still contains warnings. One actionable CredentialManagerMisuse warning was confirmed at both Google Credential Manager calls in LoginScreen and is addressed by the follow-up change below.
- No local Gradle/Android SDK, emulator/device, live Supabase write, or live RLS negative-authorization test is claimed.
- The live schema is read-only and has model_teams / team_executions with RLS enabled. The initial table-creation migration remains absent from supabase/migrations; only the later index migration is version-controlled. This source/schema drift remains open and must be reconciled with the exact deployed migration and policies before treating fresh-environment provisioning as reproducible.

### Criterion status
| Screen / area | Criterion | State | Evidence / blocker |
|---|---|---|---|
| TeamDetail | Supabase team CRUD and owner-scoped execution persistence adapter | IN_PROGRESS | Parent ownership checks now guard execution list/detail/upsert; production activation still depends on the Supabase session being available to the app and on realtime/device verification |
| TeamDetail | Cross-device realtime team/execution updates | BLOCKED | Current adapter fetches remote state and updates in-process flows after local mutations; no Supabase Realtime subscription is wired |
| TeamUsage | Persisted usage ledger and RLS verification | BLOCKED | No public.team_usage_records table in the inspected schema; Room remains the local ledger |
| TeamDetail / TeamUsage | Live negative authorization tests | BLOCKED | Production RLS is read-only; no disposable Supabase test project/branch was created |
| TeamDetail | Rendered RTL/LTR, device sizes and accessibility review | BLOCKED | No Android emulator/device is attached to this automation environment |

### Persistent continuity
- Last area worked: Supabase team/execution PostgREST adapter and production persistence fail-closed behavior.
- Next exact step: inspect the implementation commit's Android CI, APK build and lint runs to terminal state; fix any compile/test/lint failures. Then map all current Firebase-backed team/auth call sites and migrate the app-wide Auth provider only with a compatibility plan for the remaining Firebase data slices. Do not claim team screens complete while auth activation, realtime, usage persistence, RLS negative tests or device review remain open.

## 2026-11-18 — Google Credential Manager missing-credential handling

- Fresh lint evidence from run 44184564464 identified CredentialManagerMisuse at the silent and interactive Google credential calls in LoginScreen.
- The silent path now treats NoCredentialException as an expected absence of a previously authorized account and continues without turning it into an authentication failure.
- The interactive path now forwards NoCredentialException to the existing localized GOOGLE_NO_CREDENTIAL state and clears the loading state through the existing MainViewModel failure resolver.
- Added a regression test for the dedicated localized message and diagnostic code.
- This is a targeted return to Login/Identity based on a new lint finding, not a recurring default priority. The screen remains IN_PROGRESS until the new CI run passes and device-level sign-in behavior is verified.
- Last area worked: Google Credential Manager missing-credential handling, following the Supabase team/execution adapter batch.
- Next exact step: inspect the current commit's Android CI, APK and lint runs to terminal state; then resume TeamDetail by enforcing parent-team ownership before execution reads/writes and continue the Firebase-to-Supabase auth compatibility map. Do not close Login or TeamDetail based only on source changes.



## 2026-11-18 — TeamDetail execution access now verifies parent-team ownership

### Confirmed defect
- The authenticated team adapter scoped execution queries by `user_id`, but did not verify that the referenced parent team was owned by the active account before listing, reading or writing executions.
- Execution response mapping validated the account ID but did not validate that returned rows belonged to the requested team. The database RLS remains authoritative, but the Android data source lacked the planned fail-closed parent check and defense against an incorrectly scoped response.

### Implementation
- Added a shared parent-team ownership check using the same Supabase JWT and an owner-filtered `model_teams` lookup before execution list, detail and upsert requests.
- Rejects missing/unowned parent teams before execution reads or writes.
- Verifies `team_id` on every list response and on the persisted upsert representation, in addition to the existing `user_id` checks.
- No production Supabase schema, RLS, Auth settings, data, Firebase configuration or secrets were changed.

### Regression coverage
- Added tests for an unowned parent on list and upsert, an execution response for a different team, a detail record whose parent is not owned, and an upsert response whose team ID differs from the requested parent.
- Updated the successful upsert fixture to include an authenticated owner-scoped parent team.

### Verification at commit time
- Source and regression tests are committed together. Local JDK 21 is available, but Gradle, ADB and Android SDK are not installed in this execution environment; no local Gradle test/build or device test is claimed.
- GitHub Actions for this implementation commit are pending and must be checked to terminal status before marking the tests as passed.

### Persistent continuity
- Last area worked: TeamDetail Supabase execution authorization boundary.
- Next exact step: verify this commit's Android CI, APK and lint workflows to terminal state; fix any failures. Then continue the Firebase-backed team/auth call-site map and define the smallest safe app-wide Supabase Auth cutover slice without changing production Auth settings or removing Firebase capabilities still used by other features.


## 2026-11-18 — TeamDetail CI verification and Supabase Google ID-token exchange

### Previous implementation verification
- Parent-team execution ownership implementation: `d518913608a94a32477a86a743f265ceccde0917`.
- Android CI/CD run 44188693536 completed SUCCESS. The uploaded JUnit reports contain 283 tests, 0 failures, 0 errors and 4 skipped; the new parent-team ownership regression cases all executed and passed.
- APK run 44188693601 completed SUCCESS. Debug APK artifact 14375413018 and signed Release APK artifact 14375697737 were uploaded; unit-test reports are artifact 14375068632.
- Code Quality run 44188693804 completed SUCCESS. Android Lint reported 193 warnings and 0 errors; the earlier CredentialManagerMisuse warning is absent. No warning in the report points to the parent-team guard or its tests.
- Local Gradle, ADB and Android SDK remain unavailable; no local build or emulator/device validation is claimed.

### Auth compatibility map — confirmed current wiring
- `DependencyProvider.provideAuthRepository()` still returns `FirebaseAuthRepository`; `AppNavigation` injects this provider into `MainViewModel`. The visible LoginScreen Google action therefore authenticates against Firebase today.
- `provideSupabaseAuthRepository()` separately constructs `AuthRepositoryImpl` and is injected into `SupabaseProjectDataSource` and `SupabaseTeamDataSource`; it is not the app-wide Login provider.
- `provideCrashRepository()` currently passes a Firebase Auth/Firestore path and `supabaseProjectDataSource = null`; project/application reads and writes have not yet switched to the Supabase project adapter.
- `provideAIModelTeamRepository()` supplies the Supabase team adapter, but the repository only selects it when a valid Supabase session matches the active app identity. The normal Login route does not currently create that Supabase session, so the adapter must not be described as the active production team backend yet.
- A direct switch of the app-wide provider would strand remaining Firestore consumers. Keep Firebase initialization and SDKs until those slices have an authenticated, tested replacement.

### Implementation — Supabase native Google sign-in
- Added the Supabase Auth `POST /auth/v1/token?grant_type=id_token` contract for provider `google`.
- `AuthRepositoryImpl.signInWithGoogle` now validates the ID token, exchanges it through Supabase, validates the returned session, and persists it through the existing mutex/session-generation protections. Blank tokens are rejected before any network request.
- Added regression tests for the Google token request shape, session persistence/current-user publication, and blank-token rejection.
- This enables the Supabase repository path but does not switch the Login UI or app-wide auth provider. The Supabase Google provider must be enabled/configured with the matching Google client ID; its production setting was not inspected or changed.

### Verification for the Google ID-token implementation commit
- Commit `7b7d2aafd44deaf44a748ba4901c36abf72d1a02` passed all three workflows: Android CI/CD run 44190056502, APK run 44190056372, and Code Quality run 44190056397.
- JUnit artifact 14376935688 contains 285 tests, 0 failures, 0 errors and 4 skipped. Both new Google sign-in tests executed and passed.
- Debug APK artifact 14375369967 and signed Release APK artifact 14376043228 were uploaded successfully.
- Lint artifact 14376526477 reports 193 warnings and 0 errors; no lint finding references the changed Supabase Auth files.
- Test follow-up commit `600424c3465ad37e8836be22df1124e987f9eb87` passed Android CI/CD run 44191073093, APK run 44191072816, and Code Quality run 44191072792.
- Its JUnit artifact 14377410899 contains 286 tests, 0 failures, 0 errors and 4 skipped; the incomplete-session regression test passed.
- Debug APK artifact 14376033496 and signed Release APK artifact 14376208069 were uploaded successfully.
- Lint artifact 14375769976 reports 193 warnings and 0 errors.
- This follow-up adds a serialization regression test asserting the Google grant uses Supabase's exact `provider` and `id_token` JSON fields; its own CI run is pending.
- No live Supabase Auth request, provider-configuration check, production Auth setting change, device sign-in or Firebase removal was performed.

### Persistent continuity
- Last area worked: TeamDetail parent-team authorization and the Supabase Auth Google ID-token capability.
- Next exact step: build a dual-session compatibility layer for Google sign-in that keeps Firebase identity available to remaining Firestore repositories while preserving the distinct Supabase user ID for RLS-backed data. Then migrate the CrashRepository project/application slice and wire `SupabaseProjectDataSource`; do not switch the app-wide provider until the remaining Firestore consumers have tested replacements.


## 2026-11-18 — Dual Firebase/Supabase Google identity bridge

### Implementation
- Added `DualSessionAuthRepository`: Firebase remains the app-wide identity and keeps its existing UID for legacy Firestore features; the same Google ID token is also exchanged with Supabase.
- The Firebase identity is hidden from navigation while the secondary exchange is in progress, so team/project screens cannot start with a half-established identity.
- The bridge stores only the Firebase UID ↔ Supabase UID mapping and normalized Google email in a separate Android-Keystore-encrypted preference. OAuth access/refresh tokens remain in their existing provider-specific stores.
- The mapping is written only after both providers return active users with the same non-blank email. A failed or mismatched secondary exchange clears the link and Supabase session but does not misreport the Firebase login as failed.
- Team repository operations now resolve the linked Supabase subject and independently validate the Supabase session before using PostgREST. Supabase owner/user IDs are translated back to the Firebase UID in domain objects, preserving existing ViewModel and Firestore identity contracts.
- Supabase remains optional during this migration slice; when no valid link/session exists, the repository uses its existing authenticated Firebase path or fails closed if no remote backend is available.
- No production Supabase schema, RLS, Auth provider settings, data, Firebase configuration, or secrets were changed.

### Regression coverage
- Added tests for same-token dual sign-in, distinct provider subject IDs, normalized email matching, stale-link removal, secondary-provider failure, primary-provider failure, sign-out across both providers, and hiding the app identity until the second exchange completes.
- Added encrypted-link storage tests for account matching, ciphertext-only persistence, and corruption cleanup.
- Added a repository integration test proving a Firebase UID can safely operate on a Supabase team owned by its separately issued Supabase UID while the presentation model continues to expose the Firebase UID.

### Verification and evidence
- GitHub Actions completed successfully on the dual-session hardening commit `b6615509e7750e67e462a99367b0445b2fdc80e6`:
  - [Android CI](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/44196291948): unit tests and Debug APK build/upload succeeded.
  - [Build & Package APK](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/44196292030): unit tests, Debug APK and Release APK builds/uploads succeeded.
  - [Code Quality](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/44196292321): `lintDebug` succeeded.
- The uploaded JUnit report contains 297 tests, 0 failures, 0 errors and 4 skipped. The new suites passed: DualSessionAuthRepositoryTest (7), SupabaseIdentityLinkStoreTest (2), and AIModelTeamRepositorySupabaseIdentityTest (1).
- The earlier hardening commit `ce5db9c` exposed one stale test expectation (the new logout path performs one Supabase revocation rather than two); the assertion was corrected in `b6615509`, and all 297 tests then passed.
- Debug and Release APK artifacts were uploaded by GitHub Actions; no APK was published or distributed outside the workflow artifacts.
- The lint HTML report records 193 warnings and no lint errors. The two new synchronous-commit `UseKtx` warnings were removed with a narrow suppression because durable commit success is checked before exposing/removing an identity link.
- This environment still has no local Gradle installation, Android SDK, ADB or emulator. CI compilation/build is verified, but runtime installation, visual review, RTL/LTR device checks and live Google sign-in remain unverified.
- No live Supabase Auth exchange, production RLS write, schema/policy change, provider-setting change, Firebase change, or secret change was performed. The Supabase skill was not available in the installed skill catalog, so no live Supabase operation was attempted.

### Criterion status
| Area | Criterion | State | Evidence / blocker |
|---|---|---|---|
| Identity | Firebase remains the app-facing UID while Supabase receives the same Google ID token | IN_PROGRESS | Dual-session code and 6 passing CI tests; live provider/device sign-in still pending |
| Identity | Persisted provider mapping is encrypted, account-scoped and excluded from backup | IN_PROGRESS | Keystore-backed store, 2 passing Robolectric tests, and backup-rule exclusions; device/restore validation pending |
| ModelTeams / TeamDetail | Translate app UID to Supabase UID only after a matching active session | IN_PROGRESS | Fake PostgREST repository test passed; live RLS negative tests and device checks pending |
| Login | Rendered sign-in, provider configuration and lifecycle verification | BLOCKED | No emulator/device attached; Supabase Google provider setting was not changed or verified |
| Projects / Applications | Activate Supabase project data source through the same validated identity bridge | TODO | Next migration slice; CrashRepository still has its Supabase project source disabled |

### Persistent continuity
- Last area worked: dual-provider Google identity, encrypted subject mapping, Supabase team repository UID translation, logout consistency, and backup exclusion.
- The dual-session source/tests are verified by CI; the remaining identity gates are real provider configuration, live RLS checks and device sign-in.
- Current screen task: TeamValidation report correctness. The latest implementation had a concrete false-success label for partially failed or unexecuted models; this batch corrects it and exposes failed/skipped/pending counts.
- Next exact step after this batch: verify the TeamValidation Compose tests in CI, then continue only with validation checks that can produce real orchestrator evidence. Keep checks without an actual execution/persistence harness SKIPPED. Resume CrashRepository project/application migration only when the required Supabase guidance is available; do not change production schema, RLS or Auth settings.
- Repository AGENTS.md / agents.md files: none found in the inspected main tree.

## 2026-11-18 — TeamValidation report truthfulness

### Implementation
- Model outcomes now distinguish full success, partial success, failure, and no execution. A model with zero calls is no longer shown in green as `ناجح (0/0)`.
- Failed, skipped and pending matrix counts are displayed explicitly rather than leaving them implicit in the passed/total ratio.
- The model list is described as tested/attempted models, not as successfully connected models.
- Per-model cost remains labelled unmeasured unless the report carries actual measured cost evidence.

### Regression coverage
- Added Compose tests for all four model outcomes, failed/skipped/pending counts, and the unmeasured-versus-measured per-model cost label.

### Verification
- Source changes and tests are committed; CI for this batch is pending.
- No emulator/device visual verification is available in this environment.

### Criterion status
| Area | Criterion | State | Evidence / blocker |
|---|---|---|---|
| TeamValidation | Model result badges reflect actual success/failure counts | IN_PROGRESS | Compose regression tests added; CI pending |
| TeamValidation | Failed, skipped and pending checks are visible and never implied to pass | IN_PROGRESS | Explicit count summary added; Compose tests pending CI |
| TeamValidation | Model cost is shown only when measured | IN_PROGRESS | Existing report-level measurement gate retained; per-model label test added |
| TeamValidation | Full orchestration, persisted execution and lifecycle checks | BLOCKED | Checks without a real orchestrator/persistence/restart harness remain SKIPPED; no synthetic PASS is introduced |

### Persistent continuity
- Last area worked: TeamValidation report UI and status semantics.
- Next exact step: run the new Compose tests and full CI; fix failures before proceeding. Then implement the next independently testable validation/orchestration behavior without converting unavailable checks to PASS.


## 2026-11-19 — TeamValidation quota fallback and Free Only guard probes

### Implementation
- Replaced the permanently skipped rate-limit failover probe (#15) with a controlled HTTP 429 fixture passed through the production `TeamGovernanceEngineImpl`. The check requires an available free replacement and verifies the failover event links the failed paid model to that free model.
- Replaced the skipped Free Only probe (#17) with the production pre-inference guard: a paid model must fail with `PaidModelBlocked`, while a zero-priced free model remains eligible.
- Both probes are explicitly labeled as in-memory fixtures. They make no provider/network request and send no paid inference.
- The validation suite now has eight locally executable checks; the other twelve remain skipped until their real orchestration, retry/cooldown, usage-ledger and process-recreation harnesses exist.

### Regression coverage
- Updated full-suite expectations to 8 passed / 12 skipped.
- Added tests that assert both probes pass without any inference call, and that rate-limit failover fails closed when no free candidate exists.

### Verification
- Source and regression tests are committed together. GitHub Actions verification is pending; no test pass is claimed yet.
- No live provider request, Supabase write, or production data mutation was performed.

### Criterion status
| TeamValidation criterion | State | Evidence / blocker |
|---|---|---|
| #15 Model failure and free failover | IN_PROGRESS | Controlled HTTP 429 fixture exercises the production free-fallback selector; CI pending |
| #17 Free Only pre-inference protection | IN_PROGRESS | Production guard rejects paid and accepts zero-priced free models without inference; CI pending |
| #16 Rate-limit retry/cooldown | BLOCKED | No deterministic Retry-After/cooldown harness is wired yet; this batch does not claim retry behavior |
| #7–14, #18–19 | BLOCKED | Require real orchestration, cancellation/restart, persisted usage or process-recreation harnesses; no synthetic PASS is introduced |
| TeamValidation visual/device gate | BLOCKED | No emulator/device visual evidence is available in this execution environment |

### Persistent continuity
- Last area worked: TeamValidation controlled quota failover and Free Only pre-inference safety.
- Next exact step: inspect the three CI workflows for this commit to terminal state and confirm the new validation tests execute. Then add a deterministic rate-limit Retry-After/cooldown policy and tests for #16 without sending real 429 or paid requests. Keep the screen open until its remaining acceptance criteria and rendered RTL/LTR/device checks are verified.


## 2026-11-19 — TeamValidation bounded Retry-After cooldown

### Implementation
- Added a shared bounded `RateLimitRetryPolicy` for OpenRouter HTTP 429 responses. It parses integer-second and RFC-1123 `Retry-After` values, uses a 1-second fallback when the header is missing/invalid, caps waits at 30 seconds, and permits one retry by default.
- OpenRouter inference now retries the same request once after the calculated cooldown before moving to the existing model failover chain. Coroutine cancellation still propagates; non-429 HTTP errors are not delayed.
- TeamValidation check #16 exercises the same policy with a controlled header fixture. It verifies the requested wait, retry budget and maximum cooldown without sending a provider request.
- Checks #15 and #17 remain controlled in-memory probes: #15 verifies the real free-model failover selector; #17 verifies the real Free Only pre-inference guard.

### Regression coverage
- Added repository integration tests for a 429 followed by a successful retry and for a bounded retry that still fails after the configured attempt limit. The delay function is injected, so tests do not sleep or contact OpenRouter.
- Updated the full validation suite expectation to 9 passed / 11 skipped.

### Verification
- The first CI attempt for the previous commit failed Kotlin test compilation because the newly added governance test imports were missing. Commit `17e17e65e5fb13bdb56974349851c0b0e4922a66` added the missing imports; its Android CI, APK and lint reruns were still in progress when this batch was prepared.
- New Retry-After source and tests are committed with this entry; their CI verification is pending.
- No live provider request, Supabase write, production data mutation, or real paid inference was performed.

### Criterion status
| TeamValidation criterion | State | Evidence / blocker |
|---|---|---|
| #15 Model failure and free failover | IN_PROGRESS | Controlled HTTP 429 fixture exercises the production free-fallback selector; latest CI rerun pending |
| #16 Rate-limit retry/cooldown | IN_PROGRESS | Shared policy is wired into OpenRouter inference and tested with injected delay; CI pending |
| #17 Free Only pre-inference protection | IN_PROGRESS | Production guard rejects paid and accepts zero-priced free models without inference; latest CI rerun pending |
| #7–14, #18–19 | BLOCKED | Require real orchestration, cancellation/restart, persisted usage or process-recreation harnesses; no synthetic PASS is introduced |
| TeamValidation visual/device gate | BLOCKED | No emulator/device visual evidence is available in this execution environment |

### Persistent continuity
- Last area worked: TeamValidation controlled quota failover, Retry-After cooldown and Free Only pre-inference safety.
- Next exact step: verify CI for the bounded-retry implementation to terminal state and inspect the JUnit report for all three new validation probes plus both repository retry tests. Then continue with the next independently testable TeamValidation criterion; keep the screen open until its remaining real orchestration/persistence and rendered RTL/LTR/device criteria are verified.


## 2026-11-19 — TeamValidation CI regression alignment

- GitHub Actions for commit `17e17e65e5fb13bdb56974349851c0b0e4922a66` reached test execution but failed one assertion in `AIModelTeamsEndToEndValidationTest`: requirement #17 still expected `SKIPPED`, while the newly implemented production Free Only guard correctly returned `PASSED` with a `PaidModelBlocked` result. The same run completed 45 tests with 1 failure and 1 skipped.
- Updated the stale end-to-end assertion to require the new guard evidence and no provider request. This is a regression-test alignment, not a weakening of the acceptance condition.
- The earlier `f8a2f1be` run also exposed missing imports in the new test file; commit `17e17e65` added them. Subsequent Retry-After changes add the shared policy import and explicit Retrofit response type.
- The latest CI runs for the bounded Retry-After implementation and this regression-test correction are pending. No pass is claimed until the final run completes.
- Next exact step: inspect the newest Android CI/CD, APK and Code Quality runs to terminal state; use the JUnit report to confirm the Free Only, quota failover and Retry-After tests executed. Fix any remaining compile/test/lint failure before proceeding.


## 2026-11-19 — Retry-After compile correction

- CI for the bounded-retry change exposed a Kotlin compile error: `AIModelRepositoryImpl` used the new `OpenRouterChatResponse` return type without importing it. This caused the downstream `choices` and `usage` type-resolution errors. Added the missing import.
- The #17 end-to-end test still expected `SKIPPED` after the production guard was implemented; commit `3fd96e43162c5c800eaa99653efa6fa530de1296` updates it to assert `PASSED` and the actual `PaidModelBlocked` evidence.
- The bounded-retry integration test now specifies the Retrofit error response type explicitly. Latest full CI for the combined fixes is pending; no build or test pass is claimed.
- Next exact step: monitor the newest main-head workflows, inspect complete job logs and JUnit results, and correct any remaining compile, test or lint issues.


## 2026-11-19 — TeamValidation aggregate regression correction

- The latest Android CI and APK test jobs on `5f7f72a9b6ac981f6eb7226dec4eb7da94f02766` compiled the new retry implementation and executed 48 tests. One existing aggregate assertion in `AIModelTeamsEndToEndValidationTest` still expected the old 6-passed / 14-skipped matrix and expected Free Only to be unverified. It failed at line 151; the individual #15, #16 and #17 probes did not appear among the failing tests.
- Updated the aggregate regression test to expect 9 passed / 11 skipped and a verified Free Only guard, matching the now-executed checks. The test count was not reduced and no acceptance criterion was removed.
- The Android CI test job failed only on that stale aggregate expectation; the APK job uploaded its unit-test report artifact before failing at the same assertion. The Code Quality workflow had not yet reached a terminal state when this correction was made.
- The combined fix is committed with this test change. CI verification for the new main head is pending.
- Next exact step: inspect the new main-head Android CI, APK and Code Quality jobs to terminal state; download the JUnit artifact and confirm all new retry/governance tests pass, then inspect build/artifact results.


## 2026-11-19 — TeamValidation retry verification and typed provider failures

### Verified on `4f476cd40dbf347c49b7aa993bf9d3a0c8d93f84`
- Android CI/CD completed successfully: 305 tests, 0 failures, 4 skipped. The skipped cases are the existing Firestore rules tests requiring an unavailable external emulator.
- The JUnit artifact includes the full `TeamValidationEngineImplTest` (11 tests) and `AIModelRepositoryFirebaseAiTest` (6 tests); both suites passed, including the controlled 429 retry, bounded retry, Free Only guard and failover probes.
- Android Code Quality completed successfully. The lint report contains 193 existing warnings and no errors; no findings were attributed to the changed TeamValidation/AIModelRepository files.
- The APK workflow completed its unit tests, Debug APK build/upload and signed Release APK build/upload successfully. Artifacts: `CrashIntelligence-Debug-APK` (32,621,510 bytes) and `CrashIntelligence-Release-APK-signed` (22,893,989 bytes). The workflow was still executing its post-build cleanup steps at the time of this entry.
- No emulator/device screenshot or RTL/LTR interaction run was available; the visual/device gate remains BLOCKED.

### Additional hardening in this batch
- Preserve typed `AIProviderOperationException(RATE_LIMITED)` when OpenRouter exhausts its bounded retry, instead of returning an unclassified raw `HttpException`.
- Add direct coverage for RFC-1123 HTTP-date parsing and invalid-header fallback.
- Remove stale “not implemented” explanations for validation checks #15–17, which now execute.

### Criterion status
| TeamValidation criterion | State | Evidence / scope |
|---|---|---|
| #15 Model failure and free failover | PASS | Controlled HTTP 429 fixture exercises the production free-fallback selector and verifies the recorded replacement event; no live provider request |
| #16 Rate-limit retry/cooldown | PASS | OpenRouter repository integration test verifies Retry-After delay, one bounded retry, and typed RATE_LIMITED failure; fake HTTP API, no external call |
| #17 Free Only pre-inference protection | PASS | Production TeamGovernanceEngineImpl rejects paid model and allows zero-priced free model before inference; no provider request |
| #7–14, #18–19 | BLOCKED | Still require a real orchestrator job/input-version harness, pause/resume recovery, usage persistence and process-recreation evidence |
| TeamValidation visual/device gate | BLOCKED | No current Emulator/device evidence |

### Persistent continuity
- Last area worked: TeamValidation governance and provider rate-limit safety.
- Next exact step: verify the new HTTP-date/error-classification tests and final APK workflow to terminal state. Then implement the first independently testable orchestrator job lifecycle criterion (#14 pause/resume) using the existing TeamOrchestrator fixture harness, without issuing paid/live provider requests. Keep TeamValidation open until remaining job/persistence and RTL/LTR/device gates are resolved.


## 2026-11-19 — DAG checkpoint recovery and concurrent execution integrity

### Implementation
- DagOrchestrator restores completed steps from persisted execution snapshots instead of rerunning completed work after a process interruption.
- Persisted IN_PROGRESS steps are returned to PENDING with a bounded retry-count increment. The execution receives an explicit recovery message because a provider response that was not persisted may be billed again.
- Sequential collaboration now enforces one concurrent request. Parallel/DAG workers retain configured concurrency while execution snapshots and team-member status writes are serialized to prevent lost updates and whole-team document overwrites.
- Dependency cycles, missing dependencies and empty plans fail closed; the orchestrator no longer composes a final result while steps remain unresolved.
- Provider failover accounting uses the replacement model for model identity and estimated cost. Text-length-derived token/cost values are marked unconfirmed rather than measured.
- TeamOrchestratorImpl.resumeExecution can recover stale PAUSED / EXECUTING jobs for sequential, parallel and DAG styles after the previous worker is gone. Ownership checks and an atomic worker claim prevent duplicate recovery workers. Unsupported orchestration styles remain blocked rather than replaying non-idempotent work.

### Regression coverage
- Added process-recreation coverage proving a completed step is not repeated and an interrupted in-progress step is retried from its persisted checkpoint.
- Added parallel-worker coverage for concurrent execution updates and serialized team-status writes.
- Added a cyclic dependency test proving the job fails rather than being reported as completed.
- Existing active-worker pause/resume, cancellation and persistence-failure tests remain in place.

### Verification boundary
- Baseline before this batch: main HEAD 93e9e07af131908fadc27df5d24a675dc21305e9; Android CI, APK packaging and Code Quality workflows all reached terminal SUCCESS. The JUnit artifact contained 309 tests, 0 failures, 0 errors and 4 skipped; TeamOrchestratorExecutionFixtureTest had 8 passing tests and TeamValidationEngineImplTest had 12 passing tests.
- Baseline artifacts: Debug APK, signed Release APK and JUnit reports were uploaded by the workflows. These are evidence for the baseline commit only, not this new batch.
- First CI attempt for commit 2e609960a08aa52b5f7d63c797d4bd14e6e7c249 failed one regression test: sequentialExecutionPausesAndResumesFromAChildCoroutineCheckpoint observed PAUSED while the step was still IN_PROGRESS. Android CI reported 187 tests with 1 failure and 1 skipped; the APK workflow reported 186 tests with 1 failure and 1 skipped. The cause was a usage-summary update emitting a pause checkpoint before the step result had been committed.
- Correction commit a3e534c4a366c3d4aba5d7427d13f1070319c0f2 commits usage totals, evaluation and the terminal step state in one execution snapshot. The rerun passed: 312 tests, 0 failures, 0 errors and 4 skipped (308 passed); TeamOrchestratorExecutionFixtureTest passed all 11 tests. Android CI/CD run 44416472780, APK run 44416472803 and Code Quality run 44416472812 all reached terminal SUCCESS. Debug APK artifact 14479802449 and signed Release APK artifact 14479892331 were uploaded; JUnit artifact 14479103772 was inspected. The Code Quality lint artifact 14479663309 reports 193 warnings and no workflow failure.
- No local Gradle execution, Android emulator/device screenshot, RTL/LTR visual review, live provider request, Supabase write or production-data mutation is claimed.

### Criterion status
| TeamValidation criterion | State | Evidence / blocker |
|---|---|---|
| #14 Active-worker pause/resume | IN_PROGRESS | Deterministic orchestration fixture verifies persisted pause/resume checkpoints; live authenticated provider/device verification remains open |
| #19 Sequential/parallel DAG process recovery | IN_PROGRESS | New process-recreation fixture verifies persisted checkpoint recovery; production database durability and Android process-restart verification remain open |
| #7–#13 Real execution evidence | BLOCKED | User-facing checks still require configured provider jobs with persisted versioned inputs/outputs; unit fixtures are not reported as runtime PASS |
| #18 Persisted member usage ledger | BLOCKED | No confirmed remote usage-ledger table/RLS contract is available; estimated usage is not presented as measured billing |
| TeamValidation visual/device gate | BLOCKED | No Android emulator/device screenshot or RTL/LTR interaction evidence is available |

### Persistent continuity
- Last area worked: DAG execution integrity, pause/resume, process recovery, parallel state serialization and truthful retry accounting.
- Next exact step: inspect the rerun Android CI, APK and Code Quality workflows for the correction commit to terminal status; fix any remaining failures. Then continue with the next independent TeamValidation acceptance item that can be verified without production writes, starting with the usage-accounting contract and its measured-versus-estimated semantics.
- Repository AGENTS.md / agents.md files: none found in the recursive main tree inspected for this batch.


## 2026-11-19 — Usage ledger persistence and estimate semantics

### Implementation
- TeamGovernanceEngineImpl.recordUsage now propagates a failed local usage-ledger write instead of returning a successful record when persistence failed.
- TeamUsageRecord defaults isCostConfirmed to false. The current orchestration path estimates token counts from text length, so its recorded costs are not presented as provider-measured billing.
- Team Usage now labels token totals as estimates, identifies the source as usage records rather than measured telemetry, and explains that token counts are derived from text length.

### Regression coverage
- Added tests proving newly created usage records are unconfirmed by default, recordUsage persists an unconfirmed record, and a failed ledger write is surfaced to the caller.
- TeamUsageViewModel already has coverage separating confirmed cost from unconfirmed cost and excluding blocked paid attempts from successful usage.

### Verification boundary
- First CI attempt for usage commit 94ffe5004d788ef41ceceb04cae051f6d1a00876 failed at Kotlin test compilation because TeamGovernanceEngineImplTest.kt was missing the class-closing brace. The unit tests did not run on that attempt; the failure was a test-source syntax error, not an application-source failure.
- Added the missing class closure in the correction commit. Android CI, APK and Code Quality reruns are pending.
- No remote usage table, Supabase schema, RLS policy or production data was changed. The existing TeamUsage ledger remains local Room storage; cross-device/server durability is still unverified.
- The UI's token totals are estimates, not provider-reported token usage. The UI now labels them accordingly.

### Criterion status
| TeamValidation criterion | State | Evidence / blocker |
|---|---|---|
| #18 Persisted member usage ledger | IN_PROGRESS | Local Room write failures now propagate and estimates are labelled unconfirmed; remote usage-ledger contract and cross-device durability remain BLOCKED |

### Persistent continuity
- Last area worked: local usage-ledger integrity and truthful measured-versus-estimated reporting.
- Next exact step: inspect CI for the usage-ledger test correction to terminal status. Then inspect the VibeLivePreviewPanel WebView security boundary; JavaScript is enabled for generated workspace HTML, so verify CSP, navigation restrictions and network access before changing the preview sandbox.


## 2026-11-20 — VibeStudio preview isolation and truthful runtime states

### Confirmed defects
- VibeLivePreviewPanel rendered workspace-controlled HTML with JavaScript enabled under the shared http://localhost:3000 origin. It had no CSP, allowed unrestricted navigation and network requests, and retained WebView file/content access defaults.
- Native Android/iOS workspaces were represented by a Compose mock labelled as Android Emulator API 36, with claims that an APK was installed and Dalvik was loaded; no emulator or installation was involved.
- When no index.html existed, WorkspaceSandboxEngine displayed a generic interactive demo instead of the workspace application.
- startDevServer fabricated a PID, open port, CPU/memory figures and RUNNING status without spawning a process. connectLocalAgent returned CONNECTED without a transport or token verification.
- The generated-project flow logged Crash Intelligence as enabled although it did not install or verify an SDK.

### Implementation
- Added a per-workspace opaque preview origin and restrictive CSP. The WebView now blocks network requests, top-level navigation, file/content access, mixed content, popups and permission requests. DOM storage remains enabled only on the isolated per-workspace origin.
- Console output is secret-redacted and length-limited before entering app state.
- Replaced the fabricated native emulator and generic application demo with explicit unavailable states. Missing runtime or build capability is not represented as a successful preview.
- Dev-server start and Local Agent connection now fail closed until a real isolated process runner and authenticated transport exist. They do not create fake RUNNING/CONNECTED states or persist unverified tokens.
- Project generation now describes the result as saved scaffold files; it no longer claims that Crash Intelligence was activated or that a live build/server started.

### Regression coverage
- Added preview-origin, CSP ordering, navigation/resource blocking and generated-HTML tests.
- Added repository tests proving server start and Local Agent connection remain unavailable without real execution/transport backends.
- Corrected the TeamGovernanceEngineImplTest dynamic-proxy fixture to match Kotlin's mangled suspend method name; CI verification is pending.

### Verification boundary
- GitHub Code Quality and unit-test/APK workflows for this batch are pending. No local Gradle result, Emulator/device screenshot, native build, or actual Local Agent connection is claimed.
- The preview security boundary is implemented in source but remains IN_PROGRESS until CI passes and rendered RTL/LTR/device evidence is available.
- Real native execution, dev-server lifecycle, and authenticated Local Agent transport remain BLOCKED because the repository currently has no process runner or transport implementation. No fake success status is emitted.

### Persistent continuity
- Last area worked: VibeStudio preview sandbox, runtime truthfulness and Local Agent status.
- Next exact step: inspect the current main-head CI jobs and JUnit results to terminal status; fix any failures. Then continue with the highest-priority independent AIModels provider-connection criterion, while leaving real VibeStudio execution blocked until a verifiable sandbox/agent transport is implemented.
- Repository AGENTS.md / agents.md files: none found in the recursive tree inspected at main HEAD 45d5448a7fce2874f95fc29294dc7452ee989319.


## 2026-11-20 — VibeStudio terminal execution fails closed

### Confirmed defect
- The workspace terminal returned fabricated successful output for npm install/run/test/build, pip, Python, ports, ps, kill, env and unknown commands. No OS process or isolated runtime was invoked. This could make build/test controls report success without executing anything.

### Implementation
- Kept workspace-only read and file operations (help, pwd, ls/dir, cat, grep, touch, clear).
- Process execution commands now return exit code 126 with an explicit missing-runner error. Process inspection and kill commands also fail closed; unknown commands return exit code 127.
- Removed fabricated port, PID, package-install, test, build, Python and environment output.
- Updated the audit event wording so it records a terminal request rather than claiming execution before the result exists.
- Process termination now reports unavailable instead of fabricating memory/CPU cleanup.

### Regression coverage
- Added tests for blocked process commands, unknown commands, workspace-only touch, dangerous command rejection and absence of fabricated process/port state.

### Verification and continuity
- CI for the terminal fail-closed change is pending; local Gradle and emulator/device checks are not available in this run.
- Last area worked: VibeStudio preview security and truthful terminal/runtime states.
- Next exact step: inspect CI/JUnit results for current main. If green, continue the highest-priority AIModels provider connection criterion; keep real command execution BLOCKED until a verified process runner and authenticated Local Agent transport exist.


## 2026-11-20 — AIModels built-in provider connection status

### Confirmed defect
- The platform-managed Firebase AI Logic provider was constructed with status CONNECTED before any request was made.
- updateProviderStatus returned early for the platform provider, so a failed connection test could not change that status. The UI could therefore continue to show CONNECTED even when the live probe failed.

### Implementation
- The built-in provider now starts DISCONNECTED with no lastSyncAt.
- Added a per-user in-memory connection-state flow for the platform provider. Provider lists and direct lookups observe status changes without requiring a database row.
- A successful Firebase AI Logic probe during syncModels or testConnection is the only path that marks the platform provider CONNECTED. ViewModel failure mapping can now set ERROR/RATE_LIMITED/UNSUPPORTED and the UI receives the update.
- The transient status intentionally resets to DISCONNECTED after process restart, requiring a fresh live check.

### Regression coverage
- Updated the default-provider test to expect DISCONNECTED.
- Added successful and failed Firebase AI probe tests, including status-flow updates and lastSyncAt.
- Added a ViewModel success-path test; existing failure-path tests remain.

### Verification boundary
- Tests use an injected deterministic runner to verify state transitions; they do not prove a live Firebase AI network request or project configuration.
- CI for the current main head is pending. No live provider credentials, real Firebase AI request, Gradle build or device screenshot is claimed.

### Persistent continuity
- Last area worked: AIModels provider connection-state truthfulness.
- Next exact step: inspect CI/JUnit results to terminal status, then audit the remaining AIModels provider catalog, model selection and usage/cost paths for false availability or success states.


## 2026-11-20 — AIModels selectable models require connected providers

### Confirmed defect
- The model catalog and inference routing treated every stored model as available even when its provider was DISCONNECTED or ERROR. The default-model selector could return a model whose provider had never passed a live check, and inference could attempt it before provider verification.

### Implementation
- Repository getAllAvailableModels now combines the model catalog with provider status and returns only models belonging to CONNECTED providers.
- Default-model lookup and assignment use that verified-availability list. Inference failover candidates are also restricted to connected providers; an unavailable model cannot be selected or used as an automatic fallback.
- AIModels view state therefore exposes only selectable models. The empty state now distinguishes no providers, disconnected providers requiring a live test, and connected providers whose catalog still needs syncing.

### Regression coverage
- Added tests for disconnected model filtering, default-model rejection, failed live probes, successful connection before model availability, and the UI prompt shown when all providers are disconnected.
- Existing inference tests now explicitly seed a provider with a successful connection state before expecting inference.

### Verification boundary and continuity
- CI for this change is pending. Repository tests use deterministic fake API runners; they do not establish a live external provider connection.
- Last area worked: AIModels provider verification, selectable model catalog and inference routing.
- Next exact step: inspect CI/JUnit results to terminal status, then audit provider usage/cost accounting and model failover for success or cost claims that are not backed by returned provider usage data.


## 2026-11-20 — prevent forged CONNECTED provider states

### Confirmed defect
- AIModelRepositoryImpl.addProvider accepted the caller-supplied status field, so a caller could insert a provider already marked CONNECTED without a live request.
- updateProviderStatus also accepted CONNECTED from any caller, independently of testConnection or syncModels.

### Implementation
- New providers are always stored DISCONNECTED with lastSyncAt cleared, regardless of input values.
- CONNECTED transitions require an in-process proof recorded only after verifyOpenRouterConnection, verifyGeminiConnection or verifyFirebaseAiConnection succeeds. Failure/disconnect status clears that proof.
- BYOK inference fixtures now seed a previously verified database status directly; the normal repository API no longer provides a way to forge it.

### Regression coverage
- Added a test that attempts to add a pre-connected provider and then directly set CONNECTED; both paths are rejected.
- Existing Firebase AI probe tests cover successful and failed live-verification transitions.

### Continuity
- Last area worked: AIModels live connection truthfulness and availability gating.
- Next exact step: verify CI/JUnit after the new guard; then audit provider usage/cost accounting and failover response metadata.


## 2026-11-20 — BYOK sync records a verified connection

- Corrected the OpenRouter/Gemini sync path to record its successful live probe before using the guarded CONNECTED transition. A catalog fetch alone is insufficient; OpenRouter must also complete the verification request.
- Added deterministic OpenRouter success/failure probe tests. A failed completion probe leaves the provider DISCONNECTED and its models unavailable.


## 2026-11-20 — AIModels billing-tier and usage-accounting safety

### Confirmed defect
- The built-in Firebase AI Logic catalog marked Gemini models as guaranteed-free and logged every successful request with `estimatedCost = 0.0`. Firebase documents that AI Logic itself is free, but Gemini API usage and billing depend on the selected backend and project billing tier; the app cannot read that tier from its current runtime.
- Official references checked 2026-11-20: https://firebase.google.com/docs/ai-logic/pricing and https://firebase.google.com/pricing.

### Implementation
- Code commit: `e21f00f7671f78176c5ed83184e14f46357a8ed5`.
- Firebase AI Logic now reuses the current Gemini paid-tier catalog and is excluded from the guaranteed-free filter until the app can verify the project's billing tier.
- Missing provider usage metadata is recorded as `AIUsageLog.UNKNOWN_COST` (-1.0), not as zero-cost usage. Cost totals exclude unknown sentinels and the AIModels dashboard shows an explicit warning.
- Paid inference is blocked for the user while any unpriced usage exists in the last 30 days; known free models remain available in Free AI Only mode. Timeouts and ambiguous provider failures are also recorded as unpriced, and paid failover stops after an ambiguous outcome.
- Added repository, ViewModel and Compose regression coverage for paid-tier catalog classification, priced Firebase usage, missing usage metadata, paid-request blocking and the dashboard warning.
- Corrected the VibeStudio app-builder regression test to assert the absence of a successful server log. The earlier assertion matched the phrase `لم يتم تشغيل خادم` inside the intended warning and therefore failed despite the runtime remaining unavailable.

### Verification
- CI for commit `79d98885fdc5a07b31c277d2c67c2e012abc34f8`: Android Code Quality passed; the unit-test job ran 21 tests with 1 failure and 1 skipped. The only failure was the VibeStudio assertion described above. APK build was skipped because the unit-test step failed.
- The new AIModels tests and the corrected VibeStudio assertion are committed in this batch; fresh GitHub Actions results are pending. No Gradle test/build success is claimed yet.
- AIModels and VibeStudio remain IN_PROGRESS; neither screen is closed.

### Next step
- Inspect the new CI run to terminal status, fix any failing regression or unrelated test, then continue the AIModels provider/usage audit without repeating earlier compile checks.


## 2026-11-20 — AIModels follow-up: legacy usage and timeout failover

- CI on `87bb803a78425b7b683ec59104d4a01ff3e09c3a` reached Kotlin test compilation but failed because the new repository test omitted the `AIUsageLog` import. The application source compiled to the unit-test compilation stage; the import is corrected in this commit. No test or APK success is claimed from that run.
- The unpriced-usage query now also treats historical successful zero-cost entries for Firebase AI Logic and any non-free/missing model catalog entry as unpriced. This prevents usage written by older app versions from bypassing the new 30-day paid-inference guard.
- Paid timeouts now stop the current failover chain; free-model failures remain zero-cost and do not unnecessarily block paid use. A shared estimator treats absent token counts, zero-token paid responses, and missing paid model prices as unknown instead of zero.
- Added regression coverage for legacy Firebase zero-cost logs and paid timeout failover, and fixed the missing test import.
- Next: inspect the fresh CI run to terminal status. If green, continue the AIModels usage audit; if not, use the actual compiler/test report rather than repeating the same source checks.


## 2026-11-20 — CI regression follow-up

- CI for `80931cf61f0183c6abff682e373538f3d9666798` completed with a single unit-test failure in `AIFreeSystemValidationTest.modelReadsDefaultsAndDeletesAreScopedToTheOwningUser`. The repository correctly excludes models belonging to providers whose status is DISCONNECTED; the test fixture had inserted both providers with the default DISCONNECTED status. The fixture now explicitly inserts both providers as CONNECTED so the test isolates ownership rather than connection gating.
- Removed Gradle test `failFast` so a failing test no longer prevents later test classes (including the new AIModels repository regressions) from executing and reporting.
- Extended historical zero-cost detection to include legacy GOOGLE_GEMINI BYOK logs by joining the provider table. A regression test verifies that legacy zero-cost Gemini usage is treated as unpriced while a genuinely free OpenRouter model remains unblocked.
- CI run `44630184570` (Build Android APK) and `44630184637` (Android CI/CD) failed at the ownership fixture test; code-quality run `44630184585` was still in progress at the time of this update. The current commit will be revalidated from a clean run.
- Next: inspect the new CI test report through completion; do not mark AIModels complete until repository tests, lint, APK build and device verification have actual passing evidence.


## 2026-11-21 — AIModels CI regression correction

### Evidence reviewed
- Main HEAD before this batch: `183a8115b4a6ac422970a85e2d3103c6f06ae077`.
- Android CI run `44630920359` and APK run `44630920278` completed with 343 unit tests, 8 failures and 4 skipped. Kotlin app and test compilation completed; `:app:testDebugUnitTest` failed, so APK packaging was skipped.
- Code Quality run `44630920179` completed successfully.
- The retained JUnit XML artifact identified four independent causes: an invalid saved default model silently fell back to another model; three legacy inference fixtures used DISCONNECTED providers despite testing already-connected inference; an error-message assertion still expected the pre-gating wording; and the retry test attempted to call the guarded CONNECTED transition without a live verification proof.
- Two Compose assertions targeted parent containers whose semantics did not merge child text. The actual warning copy also did not contain the exact phrase asserted by the test.

### Corrections
- `AIModelRepositoryImpl.getDefaultModel` now falls back to a free model only when no default is saved. A stale or unavailable saved model returns null instead of silently selecting a different model.
- Extended the regression test to verify both stale-default rejection and first-free fallback when no default is configured.
- Updated the inference fixtures to represent previously verified persisted providers and aligned the no-connected-model assertion with the current fail-closed message.
- Updated the retry test to seed its already-verified persisted provider state through the DAO, rather than bypassing the repository's live-connection proof guard.
- Made the unpriced-usage warning explicitly say “تكلفة غير مؤكدة” and added text-level test tags for the warning and disconnected-provider empty state so Compose tests assert the actual visible message rather than a non-merging parent.

### Verification
- These corrections are source/test changes only at commit creation. A fresh GitHub Actions run is required; no passing Gradle test, APK, emulator, or device result is claimed yet.
- AIModels remains IN_PROGRESS. Its live provider requests, visual RTL/LTR review, compact-screen/IME/accessibility review, and device evidence are still open.

### Persistent continuity
- Last area worked: AIModels provider verification, default-model selection and usage-cost disclosure.
- Next exact step: inspect the fresh Android CI and APK runs for this correction to terminal status. If the unit suite passes, continue the remaining AIModels design and runtime acceptance criteria; do not close the screen without rendered device evidence.


## 2026-11-21 — Canonicalize legacy Gemini model cache before selection and team costing

### Confirmed defect
- Existing Room rows from older versions could still mark Gemini BYOK models as free with zero prices after the app moved to a conservative paid-tier catalog.
- `getAllAvailableModels`, `getModelsByProvider` and `getModelById` previously trusted those cached rows until the user manually synchronized the provider.
- This was a billing-integrity issue beyond the dashboard: `DagOrchestrator` reads the model through `getModelById` and derives its team usage cost from `activeModel.isFree` and its price fields. A legacy row could therefore produce a zero-cost team record even while the inference repository used a newer catalog.

### Implementation
- Google Gemini provider model lists now resolve from the current conservative `GeminiModelCatalog`, not stale Room pricing/free flags.
- Available-model selection substitutes canonical Gemini models only for connected providers and excludes legacy/retired cached Gemini rows.
- Direct model lookup also returns the canonical Gemini model or null for a retired/unknown model ID, so orchestration cannot calculate cost from an obsolete free-model row.
- Added a regression test with a legacy zero-price/free cache row and a retired Gemini model. It verifies canonical paid prices, exclusion from Free AI Only, and consistent provider/direct lookups.

### Verification and continuity
- This is a source/test change; fresh CI is required. No test/build success is claimed until the new Actions runs finish.
- Last area worked: AIModels provider/model availability and billing integrity.
- Next exact step: inspect the Android CI, APK and Code Quality runs for the CI-regression correction and this cache-normalization change. If green, continue the remaining AIModels interaction/design acceptance checks; live provider credentials and rendered device review remain unverified.


## 2026-11-21 — Compose test semantics follow-up

- The fresh Android CI run `44823699847` completed 343 tests with 2 failures and 4 skips. All repository/provider and AI usage regressions passed; only the two new AIModels child-text assertions failed.
- The JUnit XML shows both child text tags were found and their complete Arabic text was present. The failure was that Compose `assertTextContains` defaults to an exact match, while both tests intentionally asserted a phrase contained within a longer message.
- Corrected both assertions to use `substring = true`. The earlier hypothesis about merged semantics was incorrect; no UI semantics change was required.
- No APK build or emulator/device result is claimed from this run. The APK workflow is still running its independent test task.
- Next exact step: verify the rerun on current main to terminal status, including the legacy Gemini cache regression introduced in the same batch.


### Assertion matching correction
- JUnit XML from APK run `44823699825` confirmed the child text nodes were found. Both failures were exact-versus-substring assertion mismatches, not missing semantics or missing UI text.
- The two Compose assertions now explicitly set `substring = true`; the previous note attributing the failure to merged semantics has been corrected.
- Next: inspect the latest main-head Android CI, APK and lint runs to terminal status.

## 2026-11-21 — AIModels default-selection integrity and CI regression follow-up

### Confirmed defects and corrections
- The ViewModel duplicated default-model resolution and fell back to the first free model whenever a saved model ID was stale or unavailable. This contradicted the repository contract and could make the UI imply that a different model was still the user's saved default.
- AIModelsViewModel now delegates resolution to AIModelRepository.getDefaultModel and only exposes the returned model when it is present in the currently filtered model list. A stale saved model or a paid default hidden by Free AI Only now produces no selected model rather than silently selecting a substitute.
- Added ViewModel regression coverage for a deleted/stale saved ID, no saved default (first-free fallback remains valid), and a saved paid model filtered out by Free AI Only.
- CI also exposed a brittle catalog test: GeminiModelCatalog.models() creates a fresh model value on each call, so the data-class lastSyncAt timestamp differs even when model identity and pricing are identical. The test now compares stable identity and billing fields instead of a volatile timestamp.
- Fixed the missing JUnit assertFalse import that prevented test-source compilation in the preceding run.

### Commits on main
- 9e24c247976443bb2c03ed717f556188ab1bae7a — restore the missing JUnit assertion import.
- cb0765bba4a394b3c4ba2c0084615058cfa3ca31 — resolve the default through the repository contract.
- 6789feabbcc736ec42771d73cd32329757794e73 — add stale/default-free ViewModel regression coverage and settings-aware fake repository.
- 169025077badf0ab423b8dab93a03bb294b8ccd8 — assert stable Gemini catalog fields rather than volatile object equality.
- 3621e7329e68417236de787fd9e09c8d5a3915a2 — verify a saved paid default remains unselected when Free AI Only filters it out.

### Verification evidence and limitations
- Code Quality run 44825040840 for commit 9e24c247 completed successfully.
- Android CI run 44825040887 completed 344 tests with 1 failure and 4 skipped. Kotlin app/test compilation succeeded; the sole failure was the volatile lastSyncAt equality assertion in legacyGeminiCacheCannotMasqueradeAsFreeOrUnderpriced. APK packaging was skipped because the test task failed.
- APK run 44825040930 failed at the same unit-test assertion. It uploaded the unit-test-reports artifact (14673392452); the report was inspected and the exact failing assertion was corrected in 169025077.
- The CI, APK and Code Quality runs for the newer commits, including current HEAD 3621e732, were still in progress at this update. No passing test suite, APK build, or device/emulator verification is claimed for current HEAD.
- AIModels remains IN_PROGRESS. Its provider live-request checks, localized English LTR/Arabic RTL review, compact-screen/IME/accessibility review, and rendered device evidence remain open.

### Persistent continuity
- Last area worked: AIModels model-default integrity and Gemini catalog regression coverage.
- Next exact step: inspect CI, APK and Code Quality runs for current HEAD 3621e732 to terminal status. Fix any reported regression from the actual test output. Once those gates pass, continue the AIModels UI localization and RTL/LTR/compact-layout acceptance work; do not close the screen without rendered device evidence.


## 2026-11-22 — AIModels bilingual resources, provider feedback and accessibility

### Confirmed UX defect
- The AIModels screen and its component tree rendered Arabic literals even when the device/app locale was English. Provider status, empty states, pricing labels, security guidance, destructive confirmations and ViewModel snackbar feedback therefore ignored the selected locale.
- The Free AI Only and automatic model-selection switches had visible adjacent labels but no explicit localized accessibility descriptions.

### Implementation
- Moved the screen shell, dashboard, cost controls, model selector, provider cards/status badges, empty states, setup guide, add-provider dialog, key-visibility descriptions and destructive confirmation text to Android string resources in both English and Arabic.
- Added localized resource IDs to ViewModel feedback while retaining safe diagnostic strings for existing domain/view-model assertions. Typed provider failures map to localized messages for invalid credentials, access denial, quota, rate limits, timeouts, empty responses, network/service failures and unsupported providers.
- Added explicit localized content descriptions for the two cost/model-selection switches.
- Added Compose locale tests for English and Arabic rendering; updated UI assertions to use resource values rather than hard-coded Arabic text.

### Files changed
- `app/src/main/java/com/example/presentation/ui/screens/AIModelsScreen.kt`
- `app/src/main/java/com/example/presentation/ui/screens/aimodels/AIModelsComponents.kt`
- `app/src/main/java/com/example/presentation/viewmodel/AIModelsViewModel.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ar/strings.xml`
- `app/src/test/java/com/example/presentation/ui/screens/AIModelsScreenTest.kt`

### Verification
- Previous main HEAD `a9decc585bfa65f6a731d0c408ea41fb99087639` has three terminal successful checks:
  - Android CI run [44825739333](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/44825739333): unit tests and Debug APK build succeeded; `app-debug-apk` artifact `14673513269`.
  - APK run [44825739230](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/44825739230): unit tests, Debug APK and Release APK packaging succeeded; artifacts `CrashIntelligence-Debug-APK` (`14673803198`), `unit-test-reports` (`14673786488`) and `CrashIntelligence-Release-APK-signed` (`14673663586`) were uploaded.
  - Code Quality run [44825739309](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/44825739309): `lintDebug` succeeded; lint report artifact `14673603529`.
- These results validate the previous HEAD only. Fresh push-triggered CI, APK and lint runs are required for this batch.
- No emulator/device screenshot, live provider credential test or live provider request is claimed. This automation environment has no Android SDK/ADB/device; provider credentials were not available in this run.

### Persistent continuity
- Last domain worked: AIModels — bilingual UI, localized provider feedback and accessibility labels.
- Exact next step: inspect the new main-head Android CI, APK and Code Quality runs to terminal status; fix only failures supported by job/JUnit evidence. If green, continue AIModels compact-layout/IME and TalkBack review when an Android device is available.
- Screen status: IN_PROGRESS — source and regression coverage added; fresh CI and device/live-provider gates remain open.


## 2026-11-22 — AIModels locale-test compile correction

- The first push-triggered Android CI run `44955402031` and APK run `44955402124` both reached `compileDebugUnitTestKotlin` and failed only because `createComposeRule()` exposes no `activity` property. The five unresolved references were in the newly added locale/resource assertions; production Kotlin and Android resource compilation had already completed.
- Corrected the tests to obtain the application `Context` through the repository's existing `ApplicationProvider` test dependency. Locale rendering now uses `createConfigurationContext` and provides that context through Compose `LocalContext`, avoiding deprecated global `Resources.updateConfiguration`.
- No production behavior was changed in this correction. The new main commit must rerun unit tests, APK packaging and lint; the screen remains OPEN until those results and device/live-provider gates are satisfied.
- Exact next step: inspect the fresh run IDs for this correction; fix any further compiler/test/lint failures using their logs. Do not mark the locale tests as passed until the test task completes successfully.


## 2026-11-22 — AIModels locale pass CI verified; provider-dialog IME hardening

### Verified previous main commit
- Commit `eaf99b8a11bccbee9f3064512666d3309215576d` completed all three push-triggered workflows successfully:
  - Android CI run [44955715602](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/44955715602): unit tests and Debug APK build succeeded. Artifact `app-debug-apk` ID `14727497727`.
  - APK run [44955715628](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/44955715628): unit tests, Debug APK and signed Release APK build succeeded. Artifacts: `CrashIntelligence-Debug-APK` ID `14727586474`, `CrashIntelligence-Release-APK-signed` ID `14727289278`, and `unit-test-reports` ID `14727114473`.
  - Code Quality run [44955715608](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/44955715608): `lintDebug` succeeded. Lint artifact ID `14727945348`.
- The downloaded JUnit XML report contains 57 suites, 349 tests, 0 failures, 0 errors and 4 skipped. `AIModelsScreenTest` passed all 7 tests, including English and Arabic resource rendering.
- Lint emitted two existing ExperimentalCoroutinesApi opt-in warnings in `AIModelsViewModel.kt` lines 51 and 55; no lint task failed.
- Android CI and APK workflows produced actual Debug and signed Release APK artifacts. No device/emulator execution or live provider credential request is claimed.

### New implementation in this commit
- Made the add-provider dialog content vertically scrollable with a bounded height and IME padding so the security note and actions remain reachable on short screens while the keyboard is visible.
- Configured the API-key field for a password keyboard and Done IME action; both the Done action and Save button use the same validated, trimmed submit callback.
- Added regression assertions for the Free AI Only and auto-selection switch descriptions, API-key visibility descriptions, and keyboard Done submission.
- Corrected the Arabic free-model empty-state copy so it no longer inserts the English feature label.

### Verification state
- The previous commit is fully verified by the successful runs above. The current IME/accessibility changes require their own new Android CI, APK and lint runs; do not inherit the previous green status.
- No emulator/device screenshot, TalkBack session or live provider request is available in this run. AIModels remains OPEN; provider setup/connectivity and rendered RTL/LTR/device gates remain outstanding.

### Persistent continuity
- Last domain worked: AIModels — bilingual resources, localized provider feedback, switch/key accessibility and IME-safe provider setup.
- Exact next step: inspect the new main-head Android CI, APK and Code Quality runs to terminal status. Fix only failures backed by job logs/JUnit reports. If green, mark the locale and IME source/test criteria verified, leave device/live-provider gates BLOCKED, and move to the next independent screen in the priority order rather than repeating AIModels styling without a device.
- Screen status: IN_PROGRESS — source and CI-verified localization pass; IME hardening is awaiting fresh CI; device/live-provider acceptance remains open.


## 2026-11-22 — AIModels IME compile correction

- The first push-triggered APK run for `86796debc25d80f5bb863eff2102de9fd9b250b6` failed at `compileDebugKotlin`: `KeyboardActions` and `KeyboardOptions` were imported from `androidx.compose.ui.text.input`, but this Compose version exposes them from `androidx.compose.foundation.text`. The error was isolated to the new provider-dialog IME imports; no tests ran and no APK was built from that commit.
- Corrected those two imports. `ImeAction` and `KeyboardType` remain in `androidx.compose.ui.text.input`.
- The Android CI, APK and Code Quality runs for the failed commit must be recorded separately from the corrected commit; no success is inferred from the import fix.
- Exact next step: inspect the new main-head Android CI/APK/lint runs. Confirm the IME Done regression test and switch/key accessibility assertions actually execute and pass; then verify Debug/Release artifacts and lint.


## 2026-11-23 — LiveTesting and LiveSession fail-closed behavior and bilingual UI

### Confirmed defects
- The application model could retain an old AgentConnectionStatus.ONLINE value even though this build has no authenticated Android Agent transport. The screen used that persisted value to enable the start action.
- The LiveTesting dialog still exposed an enabled confirmation action, and the ViewModel could attempt to create a local session when invoked directly.
- LiveSession could load locally stored events, show a live status header, offer stop/command controls, and wait indefinitely for events even though no authenticated stream existed. Historical local records could therefore be mistaken for verified live results.
- The crash alert claimed that an AI engine had analyzed the report without evidence that such an analysis had occurred.
- Both screens contained Arabic UI literals that did not follow the selected English locale.

### Implementation
- Added LiveTestingRepository.isAgentTransportAvailable with a fail-closed default; the production implementation explicitly reports false until a functioning authenticated transport is implemented.
- Guarded ViewModel start/connect/stop/send operations and event observation. A stale persisted ONLINE status no longer bypasses the capability check.
- The repository now rejects ending live sessions, writing live test events, or marking an Agent ONLINE/BUSY while no verified transport exists. Existing local records remain readable but are not represented as live evidence.
- LiveTesting now shows a localized unavailable notice, does not expose Connect Agent without a transport, disables Start regardless of persisted status, and prevents dialog submission. Historical records use an explicit local/unverified label and neutral status icon.
- LiveSession hides local event rows, session lifecycle indicators, stop controls and command input when transport is unavailable. Its header identifies the record as local/unverified instead of presenting it as a live session.
- Localized the visible LiveTesting/LiveSession labels, SDK and Agent statuses, session lifecycle states, event types, crash alert and command descriptions in English and Arabic. Replaced the unsupported AI-analysis claim with a neutral statement that the connected app reported a crash.

### Files changed
- app/src/main/java/com/example/domain/repository/LiveTestingRepository.kt
- app/src/main/java/com/example/data/repository/LiveTestingRepositoryImpl.kt
- app/src/main/java/com/example/presentation/viewmodel/LiveTestingViewModel.kt
- app/src/main/java/com/example/presentation/ui/screens/LiveTestingScreen.kt
- app/src/main/java/com/example/presentation/ui/screens/LiveSessionScreen.kt
- app/src/main/res/values/strings.xml
- app/src/main/res/values-ar/strings.xml
- app/src/test/java/com/example/IntegrationWorkflowTest.kt
- app/src/test/java/com/example/presentation/ui/screens/LiveTestingAvailabilityTest.kt

### Commits on main
- b88fc7cb01b970db566cbf47162b0de35eb07f82 — declare Agent transport capability.
- 6f6e540d26d8ada14869a65630e763ca54b755a4 — fail closed for unverified live-session writes.
- 46c8a267317f6844916f17a50314924f816002b8 — guard LiveTesting ViewModel operations.
- 373e948191e658c6d9719e36a496328aa8310018 — expose truthful LiveTesting availability.
- 0d0c5c65783337411b930295e21d01dd4af93895 — hide unverified LiveSession state and controls.
- 8d4411b670fade2ebf30e90711da3aa845d3e559 and c47ea9de33dd60c5bc8ed04865615be519092c0e — add English and Arabic unavailable-state resources.
- 9c6acc06e206fe648fe0999d651b4964309dff5b — add repository/ViewModel fail-closed regression coverage.
- a8dfa8fd80504dfed4e4af44c48ebeff8aef70e7 — add Compose availability-state regressions.
- c1c244a154b4f6da3b4f0f817ab051ad46e36e3d — identify stored session headers as unverified.
- 92fe773ec5fb61d939763d6f978b72285c989dfa and 6e7da8f2931498fc8dfd08954cc488f75fdee8a8 — localize LiveTesting and LiveSession controls/statuses.
- e68d15e1f8c25898a32df03239b2dc43253106bc and 4ea13160f70d5b6c42bae345a52d2f3d0cebdc04 — add bilingual screen resources.
- a647cab346636adeb86e8908c5c5ad68878dec41 — add English/Arabic rendering regression coverage.

### Verification boundary
- Source consistency check: all 56 resource IDs referenced by the two screens exist in both locale resource files; no duplicate resource names were found; no hard-coded Arabic UI literals remain in these two screen files. This is source inspection only, not Android resource compilation.
- Added one IntegrationWorkflowTest regression covering stale ONLINE state, session creation, event exposure, stop-session mutation and repository write guards. Added five Compose tests covering persisted ONLINE state, disabled submission, unavailable messaging, local-record labeling and English/Arabic resource rendering.
- Tests were added but not executed. The environment has Java and Kotlin compiler binaries, but no Gradle executable, Android SDK or ADB; no Gradle test/build or emulator/device check was run.
- The latest code HEAD before this log entry is a647cab346636adeb86e8908c5c5ad68878dec41. Its combined commit-status response contained no status contexts, and the available workflow-run listing did not expose push-triggered runs. Therefore Android CI, APK packaging, lint and the new tests remain unverified; no pass is claimed.
- The push workflows were inspected before writing: Android CI, Code Quality and APK build are push-triggered; the release workflow is tag-triggered. No release tag or external deployment was created.
- No Supabase schema, Auth, policy, secret or production data was changed. No Agent transport was fabricated or enabled.

### Persistent continuity
- Last area worked: LiveTesting/LiveSession — transport capability, fail-closed session/event handling, truthful unavailable UI and bilingual copy.
- AIModels IME import correction 328fdd0841e944f61f5bc841b9dce20d465346b0 remains unverified: no push-run result was exposed, so its tests/build must not be marked PASS.
- Exact next step: inspect Android CI, APK and Code Quality terminal results for the latest code HEAD and confirm the new IntegrationWorkflowTest/Compose tests actually execute. Fix only failures supported by compiler/JUnit/lint evidence. If green, proceed to route #14 SetupGuide and implement evidence-backed per-step validation/persisted progress. Keep LiveTesting/LiveSession open until a real authenticated Agent transport, real event stream and emulator/device verification exist.
- Screen status: LiveTesting and LiveSession remain IN_PROGRESS. Agent transport and device/event-stream acceptance are BLOCKED; the added source and regression coverage are awaiting execution.
- Repository AGENTS.md / agents.md search: no matching instruction files were found in the inspected main tree.


## 2026-11-23 — SetupGuide: platform-aware guides and resumable progress

### Confirmed defects and CI corrections
- The initial SetupGuide test changes exposed a compile failure because the pinned Compose UI test dependency does not provide assertDoesNotExist. Both affected tests now query the semantics tree with onAllNodesWithText/onAllNodesWithTag.
- The follow-up CI run compiled and executed 362 tests but one LiveTesting locale test failed with IllegalStateException because it called setContent twice. The test was changed to switch locale through observable state in one composition. The latest result for that correction is still being checked; no pass is inferred.

### Implementation
- Replaced generic cross-platform Sentry/BugSnag snippets with platform-aware Android Kotlin/Java guides. Unsupported platforms and Firebase Crashlytics now return explicit unavailable reasons instead of receiving incorrect Android or React Native instructions.
- Sentry uses the official Android Wizard command and includes upload-token handling and debug-only test-event guidance. BugSnag uses the documented Android dependency and Application lifecycle setup.
- Added ordered step identifiers, prerequisites, official documentation links, copyable configuration blocks, explicit per-step unverified status, and navigation to project Integrations and Crashes.
- Localized SetupGuide content in English and Arabic. Automatic source inspection and authenticated per-step validation remain unavailable; copying or reviewing instructions never marks a step complete.
- Added a private SharedPreferences progress store scoped by a SHA-256 key derived from project and application IDs. It persists only the selected platform/provider so the user can resume; it stores no credentials and no completion flags.

### Files changed
- app/src/main/java/com/example/domain/model/SdkIntegrationModels.kt
- app/src/main/java/com/example/domain/repository/SetupGuideProgressStore.kt
- app/src/main/java/com/example/data/local/SharedPreferencesSetupGuideProgressStore.kt
- app/src/main/java/com/example/data/repository/SdkIntegrationRepositoryImpl.kt
- app/src/main/java/com/example/presentation/viewmodel/SetupGuideViewModel.kt
- app/src/main/java/com/example/presentation/ui/screens/SetupGuideScreen.kt
- app/src/main/java/com/example/di/DependencyProvider.kt
- app/src/main/java/com/example/presentation/navigation/AppNavigation.kt
- app/src/main/res/values/strings.xml
- app/src/main/res/values-ar/strings.xml
- app/src/test/java/com/example/presentation/ui/screens/SetupGuideAvailabilityTest.kt
- app/src/test/java/com/example/data/local/SetupGuideProgressStoreTest.kt

### Verification boundary
- Added tests for Android guide compatibility, provider instructions, persisted selection scoping/restoration, and English/Arabic rendering.
- Tests in this commit are pending CI execution. No Gradle build, emulator/device screenshot, TalkBack session, real source scan, or authenticated diagnostic-event delivery is claimed.
- SetupGuide remains IN_PROGRESS. Automatic source inspection, per-step validation, and end-to-end event confirmation are BLOCKED until an authenticated project-scoped Agent/diagnostic receiver exists. Rendered device verification remains pending.

### Persistent continuity
- Last area worked: SetupGuide — accurate Android provider instructions, bilingual UI, copyable steps, project navigation, and resumable project/application selection.
- Exact next step: inspect Android CI, APK, and Code Quality runs for this commit to terminal status and fix only failures supported by compiler/JUnit/lint evidence. If green, keep external validation/device gates BLOCKED and proceed to route #15 GitHubConnect rather than repeating SetupGuide source changes without a new capability.


## 2026-11-23 — SetupGuide CI corrections

- CI on commit 2d4303c failed Kotlin compilation because the progress-store methods passed nullable constructor properties inside lambdas. The ViewModel now snapshots nonblank project/application IDs into local non-null values before loading or saving.
- CI on commit 9b8e0a1 failed Android resource merging because an unescaped apostrophe in the English BugSnag prerequisite string was interpreted by AAPT2 as an invalid escape. The sentence now avoids the apostrophe.
- The first CI failure also reported the new load-failed string as unresolved because the core and resource commits were separate. The resource was added in the subsequent UI/resource commit; the current source now contains it.
- The final commit fe9e442 was pushed while its Android CI/APK/Code Quality runs were queued or in progress. Those results must be checked to terminal status after this correction; no success is inferred.
- Exact next step: run CI/APK/Code Quality on the correction commit to terminal status, fix any further compiler/resource/JUnit issues with evidence, then verify the progress-store and locale tests execute. Keep SetupGuide open until device/RTL and authenticated event verification gates are addressed.


## 2026-11-23 — GitHubConnect least-privilege token flow and mapping validation

### Confirmed defects
- The screen still exposed an OAuth option that submitted the literal `simulated_github_code`, while the repository had deliberately disabled client-side OAuth exchange. That button could never complete a real connection.
- The PAT was rendered as ordinary visible text and the instructions requested broad classic `repo, workflow` scopes even though the API client does not call workflow endpoints.
- The connected card claimed the integration was read-only although the repository client supports creating branches, committing file updates and creating pull requests.
- GitHubViewModel discarded connection-load, repository-list, branch-list, disconnect and repository-mapping failures. A failed repository request could appear as an empty list; a failed disconnect cleared the UI state even though the encrypted token could remain stored.
- Repository mappings were written without checking that the application existed or belonged to the selected project.

### Implementation
- Removed the nonfunctional simulated OAuth action from the user-facing screen. The current client flow is explicitly fine-grained PAT only; OAuth remains unavailable until a trusted backend exchange exists.
- Replaced broad scope guidance with least-privilege fine-grained repository permissions: Metadata read-only, Contents read/write, Pull requests read/write; the UI explicitly says Workflow permission is not required and recommends limiting repository selection and token lifetime.
- Masked the token by default, added an accessible show/hide control, password keyboard and Done submission, and linked to GitHub's fine-grained token creation page. The UI states that the token is encrypted with Android Keystore and saved locally.
- Localized the GitHub connection screen and token guidance in English and Arabic. Corrected the connection status to avoid the false read-only claim, added empty/error/retry states, and displayed existing project mappings.
- GitHubViewModel now maps HTTP 401/403 and operation failures to localized resource IDs without exposing raw provider exception text. Disconnect failure preserves the connection state; failed list and mapping operations remain visible.
- GitHubRepositoryImpl validates all mapping fields and verifies that the selected application exists and belongs to the supplied project before writing the local Room mapping.

### Files changed
- `app/src/main/java/com/example/presentation/ui/screens/GitHubConnectScreen.kt`
- `app/src/main/java/com/example/presentation/viewmodel/GitHubViewModel.kt`
- `app/src/main/java/com/example/data/repository/GitHubRepositoryImpl.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ar/strings.xml`
- `app/src/test/java/com/example/presentation/ui/screens/GitHubConnectScreenTest.kt`
- `app/src/test/java/com/example/presentation/viewmodel/GitHubViewModelTest.kt`
- `app/src/test/java/com/example/data/repository/GitHubRepositoryImplTest.kt`

### Verification boundary
- Previous HEAD `2298f024aa529938e31bcc446addb86ca78ce673` completed all push-triggered workflows:
  - Android CI run [45077711771](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/45077711771): unit tests and Debug APK succeeded; artifact `app-debug-apk` ID `14775688808`.
  - APK run [45077711775](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/45077711775): unit tests, Debug APK and signed Release APK succeeded; artifacts `CrashIntelligence-Debug-APK` ID `14775529360`, `CrashIntelligence-Release-APK-signed` ID `14776102591`, and `unit-test-reports` ID `14776276356`.
  - Code Quality run [45077711813](https://github.com/Ali-awwww/-Vibe-coding/actions/runs/45077711813): `lintDebug` succeeded; artifact `lint-results` ID `14776137167`.
- New Compose, ViewModel and repository regression tests are added in this commit; their execution is pending the new CI runs. No real GitHub token was used and no live GitHub account was linked.
- No emulator/device screenshot, TalkBack session or rendered RTL/LTR verification is available in this execution environment. No OAuth backend exchange or remote/cloud repository-mapping persistence is implemented; those criteria remain open.

### Persistent continuity
- Last area worked: GitHubConnect — token-only connection UX, least-privilege permission guidance, truthful errors and project/application mapping validation.
- Exact next step: inspect the new Android CI, APK and Code Quality runs for this commit to terminal status. Fix only failures supported by compiler/JUnit/lint evidence; confirm all new GitHubConnect tests execute. If green, continue route #16 DebugSymbols and retain the GitHubConnect device/live-token/cloud persistence gates as OPEN/BLOCKED rather than claiming screen completion.
- Screen status: IN_PROGRESS — source and regression tests added; fresh CI execution and device/live-provider/cloud persistence gates remain open.
