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

