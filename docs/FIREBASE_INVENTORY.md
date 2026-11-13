# Firebase usage inventory — 2026-11-14

## Scope and method

This is a source/configuration inventory for the Android repository at the code tree immediately preceding this document. It is not proof of deployed Firebase configuration or runtime connectivity. The reviewed code HEAD was `53031e383a53b5851b7673fd78b69822e221c4f4`; the then-current `main` HEAD `53f3d55e5e05d3d5dbd1c9995eedbc0e136cfb1c` adds only the CI investigation entry to `docs/SCREEN_TASKS.md`.

Reviewed direct references with GitHub code search for `FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`, `FirebaseMessaging`, `FirebaseFunctions`, `FirebaseCrashlytics`, `FirebaseApp`, and `com.google.firebase`; fetched the identified production files, Android manifest, app Gradle dependencies, version catalog and workflow. Search results are an inventory aid, not a complete compiler-level dependency graph.

## Confirmed production references

| Capability | Confirmed files / evidence | Current classification | Migration / retirement decision |
|---|---|---|---|
| Firebase initialization | `app/src/main/java/com/example/CrashIntelligenceApp.kt` calls `ensureFirebaseInitialized`; `app/src/main/java/com/example/util/FirebaseUtil.kt` initializes Firebase from generated resources and also contains hard-coded fallback FirebaseOptions (`remixedappid`, `remixed-api-key`, `remixed-project-id`). | Active initialization path, but fallback values are placeholders and do not establish a valid Firebase project. | Remove initializer and placeholder fallback only after all remaining production Firebase consumers are migrated and app startup no longer requires Firebase. Do not interpret placeholder initialization as a live service. |
| Firestore — crash/project/application/integration/issue/notification data | `CrashRepositoryImpl.kt` imports Firestore and contains user-scoped reads/writes for projects, applications, crash data, integrations, issues, audit logs and notifications. It receives nullable `FirebaseFirestore` from DI. | Active legacy data path. Cloud operations are conditional on Firestore availability; local Room fallback exists for selected reads. | Migrate each confirmed entity/operation to the dedicated Vibe Supabase schema and RLS before removing Firestore. Preserve owner/project isolation and offline semantics; do not bulk-switch repositories without schema and authorization tests. |
| Firebase Auth references in CrashRepository | `CrashRepositoryImpl.kt` accepts nullable `FirebaseAuth` and `requireUserId()` rejects missing auth. `DependencyProvider.provideCrashRepository` currently passes Firestore and repositories but does not pass the optional `auth` parameter. | DI currently leaves the Firebase identity dependency unset, so Firestore cloud paths that call `requireUserId()` cannot be assumed to work. Supabase Auth is separately wired for the app identity repository. | Replace the repository's identity dependency with the authenticated Supabase principal and verify authorization; do not pass Firebase UID as if it were a Supabase user ID. |
| Firebase Auth + Firestore — model teams/executions/usage | `AIModelTeamRepositoryImpl.kt` obtains `Firebase.auth` directly, resolves Firestore, reads/writes `users/{uid}/model_teams`, `team_executions` and `team_usage_records`, and keeps in-memory maps as fallback. Some writes catch and suppress Firestore errors after mutating local maps. | Active legacy persistence plus local in-memory fallback. It is not wired to the app's Supabase Auth authority. Silent write fallback can make remote persistence appear successful when it did not occur. | Migrate team/member/execution/usage records as a single authorized collaboration slice; bind identity to Supabase and surface remote failures. Retire local in-memory success paths only with explicit offline persistence behavior and tests. |
| Firebase dependency injection | `app/src/main/java/com/example/di/DependencyProvider.kt` retains FirebaseAuth/Firestore fields, lazy providers and Firebase-backed repository construction. | Active DI infrastructure. | Remove Firebase providers only after Crash and Teams repositories no longer depend on them; keep unrelated Room/Retrofit providers. |
| Firebase Crashlytics product integration | `CrashRepositoryImpl.syncCrashes` explicitly throws `UnsupportedOperationException` for `FIREBASE_CRASHLYTICS`, stating a backend proxy is required. `SetupGuideScreen`, `IntegrationsScreen` and `Models.kt` still expose the integration type and setup copy. | Product integration option is present, but ingestion/sync is explicitly unavailable; no Firebase Crashlytics SDK usage was found in the targeted search. | Keep unavailable until a backend proxy with verified Google Cloud IAM and project authorization is implemented. Do not label it connected or remove the product option without a product decision. |
| Firebase Vertex AI SDK | `gradle/libs.versions.toml` declares `firebase-ai` (`firebase-vertexai`) and `app/build.gradle.kts` includes it. The reviewed search for `com.google.firebase` did not identify production imports of the Vertex AI SDK; a separate Google Generative AI SDK is also declared and used. | Dependency appears unused by production source based on the reviewed search; this is not yet a full Gradle dependency analysis. | Confirm no reflective/generated usage and no tests rely on it, then remove the catalog alias/BOM dependency in a separate dependency-cleanup change after successful build. |
| Firebase Auth and Firestore SDK dependencies | `gradle/libs.versions.toml` declares Firebase BOM 33.9.0, Firestore and Auth; `app/build.gradle.kts` includes all three. | Required by the confirmed production imports above. | Retain until the Firestore and Firebase Auth code paths have been migrated and verified. |

## Not found in the targeted source search

No production source match was returned for `FirebaseStorage`, `FirebaseMessaging`, or `FirebaseFunctions`. No direct Firebase Crashlytics SDK class usage was returned. This does not rule out transitive dependencies, generated code, dynamic reflection, or provider-side configuration.

The reviewed `AndroidManifest.xml` contained no literal Firebase/Firestore declarations. The base `values/strings.xml` did not contain a `firestore_database_id` or Firebase app-ID entry. Repository-wide configuration and history were not exhaustively scanned in this pass.

## Safety and migration constraints

- The dedicated Supabase project is the target for this app; do not reuse the unrelated `qabasapp` project.
- No Firebase project, production data, Firestore rules, Supabase schema, RLS policy, Auth settings, credentials, or secrets were changed during this inventory.
- Do not remove Firebase SDKs or initialization until every confirmed consumer has a tested replacement.
- Do not treat Room or in-memory fallbacks as successful remote persistence. UI must identify local-only/unavailable behavior.
- Before any production data migration, obtain an approved export/backup and a documented field mapping, ownership mapping, retention plan and rollback plan.
- Supabase RLS tests must cover anonymous, owner, member, non-member and cross-user/project access before cloud feature migration is considered complete.

## Next executable migration sequence

1. Resolve identity authority for CrashRepository and AIModelTeamRepository: both must consume the same Supabase principal/session as the app. Confirm schema and ownership conventions first.
2. Migrate project/application/crash/integration/issue/notification persistence from Firestore into versioned Supabase migrations and repositories, with negative RLS tests.
3. Migrate teams, members, executions and usage records; replace swallowed Firestore failures and in-memory success fallbacks with explicit durable/offline semantics.
4. Add a real authorized backend proxy for Firebase Crashlytics ingestion only if the product still requires that provider.
5. Remove Firebase DI, initializer, SDK dependencies and configuration only after the preceding slices and Android runtime tests pass.

## Verification limits

- Source/configuration inspection only; no Gradle dependency report, Android build, unit test, emulator/device test, live Firebase request, Supabase request, or RLS test was run.
- GitHub code search is not a guaranteed exhaustive scanner. Re-run a repository-wide exact scan and inspect the complete Gradle dependency graph on a functioning runner before retiring any dependency.
