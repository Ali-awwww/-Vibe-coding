# Interaction Registry - Crash Intelligence AI

This registry tracks the navigation and interaction integrity of all screens within the platform.

## Navigation Map Status

| Screen | Route | Sidebar | Top Nav | Breadcrumbs | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Login | `Screen.Login` | N/A | N/A | No | Verified |
| Dashboard | `Screen.Dashboard` | Yes | Yes | Yes | Verified |
| Projects | `Screen.Projects` | Yes | Yes | Yes | Verified |
| Applications | `Screen.Applications` | No | Yes | Yes | Verified |
| Crashes | `Screen.Crashes` | Yes | Yes | Yes | Verified |
| Crash Detail | `Screen.CrashDetail` | No | Yes | Yes | Verified |
| Fix Assistant | `Screen.FixAssistant` | No | Yes | Yes | Verified |
| Fix History | `Screen.FixHistory` | No | Yes | Yes | Verified |
| Integrations | `Screen.Integrations` | No | Yes | Yes | Verified |
| Issues | `Screen.Issues` | Yes | Yes | Yes | Verified |
| Analytics | `Screen.Analytics` | Yes | Yes | Yes | Verified |
| Settings | `Screen.Settings` | Yes | Yes | Yes | Verified |
| Live Testing | `Screen.LiveTesting` | Yes | Yes | Yes | Verified |
| Live Session | `Screen.LiveSession` | No | Yes | Yes | Verified |
| Setup Guide | `Screen.SetupGuide` | No | Yes | Yes | Verified |
| GitHub Connect | `Screen.GitHubConnect` | No | Yes | Yes | Verified |
| Debug Symbols | `Screen.DebugSymbols` | No | Yes | Yes | Verified |
| AI Models | `Screen.AIModels` | Yes | Yes | Yes | Verified |
| Vibe Studio | `Screen.VibeStudio` | Yes | Yes | Yes | Verified |
| Dev Debug Center | `Screen.DevDebugCenter` | No | Yes | Yes | Verified |
| Model Teams | `Screen.ModelTeams` | Yes | Yes | Yes | Verified |
| Team Detail | `Screen.TeamDetail` | No | Yes | Yes | Verified |
| Team Usage | `Screen.TeamUsage` | No | Yes | Yes | Verified |
| Team Chat | `Screen.TeamChat` | No | Yes | Yes | Verified |
| Team Validation | `Screen.TeamValidation` | No | Yes | Yes | Verified |

## Screen Interaction Registry

### DashboardScreen
- [x] **Element:** Navigation Cards (Vibe Studio, Model Teams, Crashes) -> **Action:** Click -> **Result:** Navigate to respective screen.
- [x] **Element:** Quick Stats -> **Action:** Click -> **Result:** Navigate to filtered list.
- [x] **Element:** Profile Menu -> **Action:** Click -> **Result:** Sign out / Settings.

### ProjectsScreen
- [x] **Element:** Project List Item -> **Action:** Click -> **Result:** Navigate to Applications for project.
- [x] **Element:** Create Project Button -> **Action:** Click -> **Result:** Open Creation Dialog.
- [x] **Element:** Tabs (Projects, Apps, Teams, Security) -> **Action:** Click -> **Result:** Switch content view.

### CrashDetailScreen
- [x] **Element:** AI Diagnosis Button -> **Action:** Click -> **Result:** Trigger diagnosis & show results.
- [x] **Element:** Fix Assistant FAB -> **Action:** Click -> **Result:** Navigate to FixAssistantScreen.
- [x] **Element:** Stack Trace Links -> **Action:** Click -> **Result:** Navigate to source if available.

### VibeStudioScreen
- [x] **Element:** Workspace Selector -> **Action:** Change -> **Result:** Load workspace files.
- [x] **Element:** File Tree Item -> **Action:** Click -> **Result:** Open file in editor.
- [x] **Element:** AI Chat -> **Action:** Send Message -> **Result:** Receive agent response.
- [x] **Element:** Terminal -> **Action:** Input Command -> **Result:** Execute in sandbox.

### ModelTeamsScreen
- [x] **Element:** Create Team FAB -> **Action:** Click -> **Result:** Open wizard.
- [x] **Element:** Team Card -> **Action:** Click -> **Result:** Navigate to TeamDetail.

## Verification Checklist
- [x] **Dynamic Identifiers:** Verify all routes carry real IDs (no hardcoded mocks).
- [x] **Back Navigation:** `BackHandler` implemented in all nested screens.
- [x] **State Preservation:** "Refresh" (Recomposition/ViewModel survival) works on all screens.
- [x] **RBAC:** Viewer role cannot see administrative buttons.
- [x] **Empty States:** Correct "No Data" UI shown when Firestore/Room is empty.
