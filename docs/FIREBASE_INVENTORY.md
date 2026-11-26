# Firebase Backend Architecture & Inventory

## Overview

Firebase is the sole cloud backend for **Crash Intelligence AI** (`com.aistudio.crashintelligence.vyfrip`, Firebase project `gen-lang-client-0258473942`). All authentication, cloud persistence, AI diagnostics, App Check, and application crash reporting run directly on Firebase.

## Active Firebase Services

| Capability | Implementation | Role |
|---|---|---|
| Firebase Initialization & App Check | `CrashIntelligenceApp.kt`, `util/FirebaseUtil.kt` | Initializes Firebase from `app/google-services.json`, configures Play/reCAPTCHA or Debug App Check provider, and enables Crashlytics collection. |
| Authentication | `data/repository/FirebaseAuthRepository.kt`, `auth/AuthRedirects.kt` | Email/password sign-in and registration, Google Sign-In via Jetpack Credential Manager, password recovery, email verification enforcement, and Crashlytics user ID binding. |
| Cloud Persistence | `data/repository/CrashRepositoryImpl.kt`, `data/repository/AIModelTeamRepositoryImpl.kt`, `firestore.rules` | User-scoped Cloud Firestore collections (`users/{uid}/...`) for projects, applications, crashes, integrations, team definitions, executions, usage records, fix logs, and notifications, protected by zero-trust Firestore Security Rules. |
| Firebase Crashlytics (App Telemetry) | `app/build.gradle.kts`, `util/FirebaseUtil.kt` | Collects real runtime crashes and non-fatal diagnostics (`recordCrashlyticsNonFatal`) for this app itself, tagged with the authenticated user ID. |
| Firebase AI Logic (Gemini API) | `data/repository/AIModelRepositoryImpl.kt`, `presentation/viewmodel/FixAssistantViewModel.kt` | Runs root-cause analysis and Arabic fix generation via `Firebase.ai` (`gemini-3.8-flash` primary with 503 retry and `gemini-3.5-flash-lite` fallback; fast-fail on daily quota exhaustion). |

## External Crash Source Behavior

- **Sentry & Bugsnag:** Connected directly through their respective provider APIs (`SentryApi.kt`, `BugsnagApi.kt`).
- **External Firebase Crashlytics Projects:** There is no public client-side API to read Crashlytics issues from an arbitrary external Firebase project inside the mobile app. When no connected crash source or crash records exist, the Crashes screen displays an honest **"غير متصل بعد" / "Not connected yet"** state with a direct action to open the Setup Guide, alongside the working **Sentry API** and **in-app reporting SDK** options.
