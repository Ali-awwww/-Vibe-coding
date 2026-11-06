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

**Current state:** Supabase project, protected initial schema and partial Auth source wiring exist. Multiple Firebase-backed repositories and DI/runtime references remain. Feature-slice migration has not been completed; all checkboxes above remain open. No Gradle build or Android end-to-end verification is claimed.

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
