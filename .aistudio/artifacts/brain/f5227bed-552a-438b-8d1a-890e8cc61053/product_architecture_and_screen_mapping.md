# هندسة المنتج وخريطة الشاشات الشاملة (Product Architecture & Screen Mapping)
## Crash Intelligence AI Platform

---

## 1. الرؤية الهندسية والمعمارية للمنصة (Executive Architectural Summary)

منصة **Crash Intelligence AI** هي بيئة متكاملة تجمع بين هندسة البرمجيات بالذكاء الاصطناعي، ومراقبة استقرار التطبيقات، وتحليل الأعطال الجذرية، والتعاون الفعلي بين فرق النماذج المتعددة.

تتكون المنصة من **7 قطاعات وظيفية كبرى (Core Modules)** مترابطة بشكل وثيق وتمنع الشاشات المعزولة (Isolated Screens) أو الروابط الميتة (Dead Ends):
1. **إدارة الحساب والمنصة (Account & Platform Management)**
2. **استخبارات الأعطال والتشخيص (Crash Intelligence & Diagnosis)**
3. **التكاملات ومصادر البيانات (Integrations & Telemetry)**
4. **مركز نماذج الذكاء الاصطناعي وحماية التكلفة (AI Model Hub & Cost Protection)**
5. **فرق النماذج المتعددة والتعاون الفعلي (AI Model Teams & Collaboration)**
6. **أستوديو التطوير الذكي (AI Vibe Coding Studio)**
7. **الإدارة والحوكمة والمراقبة النظامية (Administration & System Governance)**

---

## 2. جرد الشاشات الشامل (Screen Registry by Module)

### القسم الأول: الحساب وإدارة المنصة (Account & Platform Management)
| # | Screen ID | Screen Name | Route / UI Host | Type | Scope | Description |
|---|-----------|-------------|-----------------|------|-------|-------------|
| 1 | `AUTH_LOGIN` | تسجيل الدخول (Sign In) | `Screen.Login` (`AuthMode.SIGN_IN`) | Standalone Screen | Global | مصادقة آمنة باستخدام البريد الإلكتروني وكلمة المرور المشفرة بـ PBKDF2. |
| 2 | `AUTH_REGISTER` | إنشاء حساب جديد (Register) | `Screen.Login` (`AuthMode.SIGN_UP`) | Screen Tab/Mode | Global | تسجيل مطور جديد مع تحديد المنظمة والتشفير الآلي للمفاتيح. |
| 3 | `AUTH_FORGOT_PW` | استعادة كلمة المرور (Forgot Password) | `Screen.Login` (`AuthMode.RECOVER_PASSWORD`) | Screen Tab/Mode | Global | إجراءات إعادة تعيين الرمز السري والتحقق الأمني. |
| 4 | `USER_PROFILE` | الملف الشخصي للمطور (User Profile) | `Screen.Settings` (Profile Card) | Modular Section | User-Scoped | عرض بيانات المطور النشط، المنظمة، وتاريخ آخر دخول آمن. |
| 5 | `ACCOUNT_SETTINGS` | إعدادات الحساب (Account Settings) | `Screen.Settings` | Standalone Screen | User-Scoped | تغيير كلمة المرور، مراجعة الجلسات النشطة، وتسجيل الخروج الآمن. |
| 6 | `WORKSPACE_SETTINGS` | إعدادات مساحة العمل (Workspace Settings) | `Screen.VibeStudio` (Resource Limits Dialog) | Modular Dialog | Workspace-Scoped | ضبط حدود الذاكرة، استهلاك CPU، ونطاق مساحة العمل المحلية. |
| 7 | `NOTIFICATIONS_CENTER` | مركز الإشعارات والتنبيهات (Notifications) | `Screen.Dashboard` (ModalBottomSheet) | Modal Drawer/Sheet | Project/User-Scoped | تنبيهات الانهيارات الحرجة، ANR، اكتمال التشخيص، واقتراحات PR. |
| 8 | `TEAM_MEMBERS` | أعضاء الفريق والأدوار (Team Members) | `Screen.Projects` (Tab 2: Team) | Modular Tab | Project-Scoped | إدارة أعضاء المشروع، إضافة مطورين، وتحديد الأدوار (Owner/Admin/Dev/Viewer). |
| 9 | `PERMISSIONS_POLICY` | الصلاحيات والسياسات الأمنية (Permissions) | `Screen.Projects` (Tab 3: Security) | Modular Tab | Project-Scoped | ضبط سياسات تنقيح البيانات الشخصية (PII Scrubbing)، وسياسات الاحتفاظ. |

---

### القسم الثاني: استخبارات الأعطال (Crash Intelligence)
| # | Screen ID | Screen Name | Route / UI Host | Type | Scope | Description |
|---|-----------|-------------|-----------------|------|-------|-------------|
| 10 | `CRASH_DASHBOARD` | لوحة القيادة العامة (Dashboard) | `Screen.Dashboard` | Top-Level Destination | User/Org | نظرة عامة على الانهيارات، مؤشرات الاستقرار، والوصول السريع لجميع الخدمات. |
| 11 | `CRASH_PROJECTS` | إدارة المشاريع (Projects) | `Screen.Projects` | Standalone Screen | User/Org | قائمة المشاريع، إنشاؤها، تحديد سياساتها، وفحص إحصائياتها. |
| 12 | `CRASH_APPLICATIONS` | إدارة التطبيقات (Applications) | `Screen.Applications(projectId)` | Standalone / Tab | Project-Scoped | قائمة التطبيقات التابعة لمشروع معين، مع حالة حزمة SDK والاتصال. |
| 13 | `CRASH_REPORTS` | تقارير الأعطال (Crash Reports) | `Screen.Crashes(filters)` | Standalone Screen | Project/App | تصفية الأعطال حسب الإصدار، الخطورة (Critical/High/Med/Low)، ونظام التشغيل. |
| 14 | `CRASH_DETAILS` | تفاصيل الانهيار (Crash Details) | `Screen.CrashDetail(crashId)` | Standalone Screen | Crash-Scoped | تحليل Stack Trace الكامل، De-obfuscation، وفحص الجلسة والعتاد. |
| 15 | `CRASH_ISSUES` | تصنيف المشاكل المجمعة (Issues) | `Screen.Issues(projectId)` | Standalone Screen | Project-Scoped | تجميع الانهيارات المتطابقة بواسطة Fingerprinting لتحديد الأولويات. |
| 16 | `CRASH_ISSUE_DETAILS` | تفاصيل المشكلة المجمعة (Issue Details) | `Screen.Issues` (Detail Inspector Card) | Modular Panel | Issue-Scoped | سجل تكرار الانهيار، عدد المستخدمين المتأثرين، وتحليل اتجاه الظهور (Trend). |
| 17 | `LIVE_TESTING` | مركز الاختبار المباشر (Live Testing) | `Screen.LiveTesting` | Standalone Screen | Project-Scoped | تشغيل اختبارات المحاكاة للانهيارات الحية، سيناريوهات الإجهاد، وSDK Test. |
| 18 | `LIVE_TEST_SESSION` | جلسة الفحص المباشر (Test Session) | `Screen.LiveSession(sessionId)` | Standalone Screen | Session-Scoped | مراقبة مباشرة لسجل الأحداث الحية أثناء تشغيل التطبيق واكتشاف الأعطال الفورية. |
| 19 | `CRASH_DIAGNOSIS` | تشخيص الذكاء الاصطناعي (AI Diagnosis) | `Screen.CrashDetail` (Diagnosis Card/Modal) | Embedded Engine | Crash-Scoped | تحديد السبب الجذري (Root Cause)، درجة التأكد، وموقع الملف والسطر الفعلي. |
| 20 | `CRASH_ANALYTICS` | التحليلات والرسوم البيانية (Analytics) | `Screen.Analytics` | Standalone Screen | Project-Scoped | نسب الاستقرار (Crash-free Users/Sessions)، الانهيارات عبر الزمن والمنصات. |
| 21 | `FIX_HISTORY` | سجل الحلول والاعتمادات (Fix History) | `Screen.FixHistory(projectId)` | Standalone Screen | Project-Scoped | سجل التعديلات المطبقة، روابط الـ PRs المنشأة، ونتائج التحقق التجريبي. |
| 22 | `FIX_VERIFICATION` | التحقق من الإصلاح (Fix Verification) | `Screen.FixAssistant` (Verify Step) | Action Engine | Fix-Scoped | إعادة اختبار الكود المعدل في جلسة عزل ومقارنة Stack Trace قبل وبعد. |

---

### القسم الثالث: التكاملات والاتصال (Integrations)
| # | Screen ID | Screen Name | Route / UI Host | Type | Scope | Description |
|---|-----------|-------------|-----------------|------|-------|-------------|
| 23 | `INTEG_HUB` | منصة التكاملات الآمنة (Integrations Hub) | `Screen.Integrations(projectId)` | Standalone Screen | Project-Scoped | إدارة كافة مصادر الاستقبال مع حماية المفاتيح بتشفير AES-GCM 256. |
| 24 | `INTEG_FIREBASE` | إعداد Firebase Crashlytics | `Screen.Integrations` & `SetupGuide` | Config Flow | App-Scoped | تعليمات دمج Google Services وتوليد ملفات الضبط واستقبال الـ Webhook. |
| 25 | `INTEG_SENTRY` | إعداد وتكامل Sentry | `Screen.Integrations` (Sentry Modal) | Config Flow | Project-Scoped | ربط DSN، ضبط Webhook Signatures للتحقق من هوية المرسل HMAC-SHA256. |
| 26 | `INTEG_BUGSNAG` | إعداد وتكامل Bugsnag | `Screen.Integrations` (Bugsnag Modal) | Config Flow | Project-Scoped | تهيئة المفاتيح ومزامنة تلقائية للأخطاء غير القاتلة والانهيارات. |
| 27 | `INTEG_GITHUB` | تكامل مستودعات GitHub | `Screen.GitHubConnect(projectId)` | Standalone Screen | Project-Scoped | ربط مستودع المشروع لقراءة الكود المصدري وفتح Pull Requests آليًا للإصلاحات. |
| 28 | `INTEG_REPOSITORIES` | قائمة المستودعات المتصلة (Repositories) | `Screen.GitHubConnect` (Repo List) | Modular Panel | Project-Scoped | عرض الفروع (Branches)، آخر Commit مطابقة لإصدار التطبيق، وحالة المزامنة. |
| 29 | `INTEG_REPO_DETAILS` | تفاصيل المستودع والفرع (Repo Details) | `Screen.GitHubConnect` (Branch Inspector) | Modular Card | Repo-Scoped | فحص شجرة الملفات واستخراج سياق الكود المحيط بالسطر المنهار. |
| 30 | `INTEG_DEBUG_SYMBOLS` | رموز التصحيح (Debug Symbols) | `Screen.DebugSymbols(projectId)` | Standalone Screen | Project-Scoped | رفع وفهرسة ملفات ProGuard mapping.txt وNDK syms لفك غموض الـ Stack Trace. |
| 31 | `INTEG_API_CONFIG` | إعدادات الـ API والـ Webhooks | `Screen.Integrations` (Security Modal) | Security Dialog | Project-Scoped | فحص تواقيع HMAC، توليد أسرار جديدة، واختبار وصول البيانات الحية. |

---

### القسم الرابع: مركز نماذج الذكاء الاصطناعي (AI Model Hub)
| # | Screen ID | Screen Name | Route / UI Host | Type | Scope | Description |
|---|-----------|-------------|-----------------|------|-------|-------------|
| 32 | `AI_PROVIDERS` | مزودو الذكاء الاصطناعي (AI Providers) | `Screen.AIModels` (Providers Section) | Modular Section | User (BYOK) | إدارة المزودين (Gemini, OpenRouter, HuggingFace) مع اختبار الاتصال الفعلي. |
| 33 | `AI_API_KEYS` | إدارة المفاتيح المشفرة (API Keys Vault) | `Screen.AIModels` (Add/Edit Dialog) | Security Dialog | User (BYOK) | تخزين مشفر بنظام AES-GCM؛ عزل مفاتيح كل مستخدم ومنع ظهورها في السجلات. |
| 34 | `AI_MODELS_CATALOG` | كتالوج النماذج المتاحة (Available Models) | `Screen.AIModels` (Model Picker) | Modular Section | Global Catalog | استعراض النماذج المتاحة فعليًا حسب نوع الترخيص (Free vs Paid). |
| 35 | `AI_MODEL_DETAILS` | تفاصيل النموذج وقدراته (Model Details) | `Screen.AIModels` (Capability Chips) | Embedded Details | Model-Scoped | عرض حجم نافذة السياق (Context Window)، قدرات الكود والاستدلال، والأسعار. |
| 36 | `AI_FREE_SETTINGS` | وضع النماذج المجانية (Free AI Settings) | `Screen.AIModels` (Cost Protection Card) | Control Panel | User-Scoped | تفعيل "Free AI Only" الافتراضي؛ إيقاف التنفيذ الصارم في حال عدم توفر نموذج مجاني. |
| 37 | `AI_USAGE_ANALYTICS` | تحليلات الاستهلاك والتكلفة (Usage Analytics) | `Screen.AIModels` & `Screen.TeamUsage` | Visual Dashboard | User/Team | تتبع الـ Tokens المستهلكة، عدد الاستدعاءات الناجحة والفاشلة، والتكلفة الصفرية/الفعلية. |

---

### القسم الخامس: فرق النماذج المتعددة (AI Model Teams)
| # | Screen ID | Screen Name | Route / UI Host | Type | Scope | Description |
|---|-----------|-------------|-----------------|------|-------|-------------|
| 38 | `TEAMS_MY_TEAMS` | فرق النماذج الخاصة بي (My Teams) | `Screen.ModelTeams` | Standalone Screen | User-Scoped | استعراض كافة الفرق المنشأة، حالتها التشغيلية، ومؤشرات استهلاكها. |
| 39 | `TEAMS_CREATE_TEAM` | إنشاء فريق جديد (Create Team) | `Screen.CreateTeam` / Wizard | Wizard Flow | User-Scoped | معالج خطوة بخطوة لاختيار النماذج، تعيين قائد الفريق، وتحديد نمط التعاون. |
| 40 | `TEAMS_BUILDER` | بناء الفريق من القوالب (Team Builder) | `Screen.ModelTeams` (Templates Bar) | Modular Bar | User-Scoped | اختيار قوالب هندسية جاهزة: Vibe Coding Squad, Crash Investigation Team, etc. |
| 41 | `TEAMS_CONFIG` | إعدادات وحوكمة الفريق (Team Config) | `Screen.TeamDetail(teamId)` (Settings) | Standalone Screen | Team-Scoped | ضبط مهلة التنفيذ، الحد الأقصى للمحاولات، تفعيل وضع النقاش (Debate Mode). |
| 42 | `TEAMS_MEMBERS` | أعضاء الفريق وتوزيع الأدوار (Members) | `Screen.TeamDetail` (Roster Tab) | Modular Panel | Team-Scoped | تحديد أدوار الأعضاء: Architect, Planner, Coder, Critic, Security Auditor. |
| 43 | `TEAMS_CHAT` | محادثة وتعاون الفريق (Team Chat) | `Screen.TeamChat(teamId, projectId)` | Interactive Workspace | Multi-Agent | غرفة عمليات حية للتكليف بالمهام، مراقبة عمل كل نموذج لحظيًا، وإدارة التعاون. |
| 44 | `TEAMS_ACTIVITY_LOG` | سجل نقاش ونشاط الفريق (Activity Log) | `Screen.TeamChat` (Activity Log Panel) | Live Stream | Team Execution | عرض خطة القائد، المهام الفرعية، نتائج الأعضاء، نقاط الخلاف، والقرار النهائي. |
| 45 | `TEAMS_EXEC_HISTORY` | سجل المهام السابقة (Execution History) | `Screen.TeamDetail` (History Tab) | Audit List | Team-Scoped | أرشيف لكافة المهام المنفذة مع تفاصيل النجاح والمدة والأخطاء إن وجدت. |
| 46 | `TEAMS_USAGE` | لوحة استهلاك الفريق (Team Usage) | `Screen.TeamUsage(teamId)` | Standalone Screen | Team-Scoped | إحصائيات دقيقة لاستهلاك كل عضو بالـ Tokens مع حماية الميزانية. |
| 47 | `TEAMS_VALIDATION` | مركز اعتماد الأنظمة (Validation Center) | `Screen.TeamValidation` | Standalone Screen | System-Scoped | فحص معايير التعاون الـ 20 عبر بيئات الـ Sandbox المعزولة قبل التشغيل الفعلي. |

---

### القسم السادس: أستوديو التطوير الذكي (AI Vibe Studio)
| # | Screen ID | Screen Name | Route / UI Host | Type | Scope | Description |
|---|-----------|-------------|-----------------|------|-------|-------------|
| 48 | `VIBE_WORKSPACES` | مساحات العمل والتطبيقات (Workspaces) | `Screen.VibeStudio` (Workspace Picker) | Control Header | User-Scoped | التبديل بين مساحات العمل أو إنشاء مساحة تطبيق جديدة (Web / Android). |
| 49 | `VIBE_CREATE_PROJECT`| إنشاء مشروع برمجي (Create Project) | `Screen.VibeStudio` (App Builder Dialog) | Modal Wizard | Workspace | تحديد فكرة التطبيق باللغة الطبيعية وتوليد شجرة الملفات الأساسية. |
| 50 | `VIBE_AI_CHAT` | المحادثة البرمجية مع الوكيل (AI Chat) | `Screen.VibeStudio` (VibeAgentChatPanel) | Embedded Panel | Workspace | حوار برمجي يحلل الملفات، يقترح التعديلات، ويشغل أوامر التطوير بأمان. |
| 51 | `VIBE_FILE_EXPLORER` | مستعرض الملفات الفعلي (File Explorer) | `Screen.VibeStudio` (File Tree Pane) | Embedded Panel | Workspace | استعراض وقراءة وتعديل الملفات الحقيقية دون أي ملفات وهمية. |
| 52 | `VIBE_CODE_EDITOR` | محرر الأكواد المتكامل (Code Editor) | `Screen.VibeStudio` (Editor Canvas) | Embedded Canvas | File-Scoped | محرر متطور مع أرقام الأسطر، تمييز البنية، كشف التغييرات، والحفظ الآمن. |
| 53 | `VIBE_LIVE_PREVIEW` | المعاينة التفاعلية المباشرة (Live Preview)| `Screen.VibeStudio` (VibeLivePreviewPanel)| Embedded Panel | Workspace | معاينة تشغيلية لتطبيق الويب أو واجهات المكونات في بيئة معزولة. |
| 54 | `VIBE_TERMINAL` | الطرفية المعزولة (Terminal Sandbox) | `Screen.VibeStudio` (VibeTerminalPanel) | Embedded Panel | Sandbox | تشغيل الأوامر المصرح بها، تنفيذ حزم الاختبارات، ومراقبة المخرجات. |
| 55 | `VIBE_BUILD_LOGS` | سجلات البناء والتشغيل (Build Logs) | `Screen.VibeStudio` & `DevDebugCenter` | Log Stream | Build-Scoped | عرض أخطاء الـ Compilation، سجلات الـ Runtime، وتنبيهات الأمان. |
| 56 | `VIBE_PROBLEMS` | قائمة المشاكل والأخطاء (Problems Center) | `Screen.VibeStudio` (VibeReviewIssues) | Modal Dialog | Workspace | تصنيف أخطاء الفحص الاستاتيكي (Linter/Compiler Warnings & Errors). |
| 57 | `VIBE_TESTING` | مركز الاختبارات الآلية (Testing Center) | `Screen.VibeStudio` (VibeTestingPanel) | Embedded Panel | Workspace | تشغيل اختبارات الوحدة واختبارات التكامل وعرض مؤشرات النجاح والتغطية. |
| 58 | `VIBE_GIT_CHANGES` | إدارة التغييرات والفروقات (Git Changes) | `Screen.VibeStudio` (Version Control/Diff) | Modal Tool | Workspace | فحص الفروقات (Unified Diff)، إنشاء Checkpoints، وتنفيذ الـ Commit. |
| 59 | `VIBE_SETTINGS` | حوكمة مساحة العمل (Project Settings) | `Screen.VibeStudio` (Governance Dialog) | Governance Tool | Workspace | إدارة صلاحيات الوكيل، الموافقة المسبقة على الإجراءات الحساسة، وحدود الذاكرة. |

---

### القسم السابع: الإدارة والحوكمة والمراقبة (Administration & Governance)
| # | Screen ID | Screen Name | Route / UI Host | Type | Scope | Description |
|---|-----------|-------------|-----------------|------|-------|-------------|
| 60 | `ADMIN_SETTINGS` | إعدادات النظام العامة (System Settings) | `Screen.Settings` | Standalone Screen | Global | تفضيلات المظهر، الوضع الليلي/النهاري، دعم اللغات (عربي/إنجليزي)، وإصدار النظام. |
| 61 | `ADMIN_SECURITY` | فحص درع الأمان (Security & Privacy) | `Screen.Settings` (Security Status Row) | Visual Audit | System-Wide | مؤشرات تشفير AES-GCM، عزل المطورين، تنقيح PII، وتواقيع Webhook. |
| 62 | `ADMIN_AUDIT_LOGS` | سجل العمليات الحساسة (Audit Logs) | `Screen.Projects` (Tab 4: Audit) | Audit Trail | Project/Org | تسجيل كافة التغييرات على الأعضاء، الصلاحيات، التكاملات، وعمليات مسح البيانات. |
| 63 | `ADMIN_ERROR_LOGS` | سجل أخطاء النظام (Error Logs) | `Screen.DevDebugCenter(workspaceId)` | Diagnostics | System/Dev | تتبع أي أعطال غير معالجة في النظام نفسه، وفشل الاتصال بمزودي الـ AI. |
| 64 | `ADMIN_BACKGROUND` | إدارة المهام الخلفية (Background Jobs) | `Screen.DevDebugCenter` (Monitoring) | Background Engine | Real-Time | تتبع مهام مزامنة الرموز (Symbolication)، تحليلات الأعطال، واختبارات النماذج. |
| 65 | `ADMIN_HEALTH` | صحة وجاهزية النظام (System Health) | `Screen.DevDebugCenter` (Context Card) | Telemetry Matrix | Real-Time | مراقبة اتصال حزم SDK، استجابة مزودي الذكاء الاصطناعي، وسلامة التخزين المحلي. |

---

## 3. خريطة وهندسة التنقل (Navigation Architecture & Flows)

### أ. الهيكل الهرمي للتنقل (Hierarchical Navigation Graph)

```
[Start App]
   │
   ├─► (Unauthenticated) ──► Screen.Login (Sign In / Register / Recover Password)
   │                             │ (Successful Auth)
   └─► (Authenticated) ──────────┴─► Screen.Dashboard (Top-Level Command Center)
                                        │
    ┌────────────────┬──────────────────┼─────────────────┬────────────────┐
    ▼                ▼                  ▼                 ▼                ▼
Screen.Projects   Screen.Crashes   Screen.Issues   Screen.Settings   Screen.Analytics
    │                │                  │                 │
    │ (Select App)   │ (Select Crash)   │ (Select Issue)  ├─► Security Policies
    ▼                ▼                  ▼                 ├─► Seed Sandbox Data
Screen.Apps     Screen.CrashDetail  Screen.CrashDetail    └─► Sign Out (-> Login)
    │                │
    │ (Integrations) ├─► Symbolication ──► Screen.DebugSymbols
    ▼                ├─► AI Diagnosis
Screen.Integ         ├─► Team Investigation (Screen.TeamChat)
    │                └─► Code Fix Assistant ──► Screen.FixAssistant
    ├─► GitHub Connect (Screen.GitHubConnect)        │
    ├─► Debug Symbols (Screen.DebugSymbols)         ├─► Verify Fix (Live Test)
    └─► Setup Guide (Screen.SetupGuide)             ├─► Create PR (GitHub)
                                                    └─► Screen.FixHistory
```

### ب. مسارات التعاون والتطوير (AI Teams & Vibe Studio Navigation Flows)

```
Screen.Dashboard
   │
   ├─► Screen.VibeStudio (AI Vibe Coding Environment)
   │      │
   │      ├─► Workspace Manager & New Project Wizard
   │      ├─► AI Coding Agent Panel (VibeAgentChatPanel)
   │      ├─► File Explorer & Code Editor Canvas
   │      ├─► Live Preview & Terminal Sandbox
   │      ├─► Git Version Control & Checkpoints
   │      └─► Dev & Debug Hub ──► Screen.DevDebugCenter
   │
   ├─► Screen.ModelTeams (AI Multi-Agent Teams)
   │      │
   │      ├─► Create Team Wizard ──► Screen.CreateTeam
   │      ├─► Team Detail & Governance ──► Screen.TeamDetail
   │      ├─► Team Usage & Cost Dashboard ──► Screen.TeamUsage
   │      ├─► Collaborative Team Workspace ──► Screen.TeamChat
   │      └─► End-to-End Validation Engine ──► Screen.TeamValidation
   │
   └─► Screen.AIModels (Free AI & BYOK Management)
          │
          ├─► AI Providers (Gemini, OpenRouter, HuggingFace)
          ├─► Encrypted API Key Storage (AES-GCM)
          └─► Cost Protection & Free-AI Enforcement
```

### ج. رحلة المستخدم الحرجة لإصلاح العطل (Critical CUJ Flow)
```
ProjectsScreen
   └─► Applications (Project Selected)
          └─► CrashesScreen (Filtered by App/Version)
                 └─► CrashDetailScreen (Symbolicated Stack Trace Loaded)
                        └─► AI Diagnosis (Root cause identified with confidence score)
                               └─► FixAssistantScreen (Diff inspected & validated)
                                      └─► Verify Fix (Automated test executed in sandbox)
                                             └─► LiveTestingScreen / FixHistoryScreen (Documented & Proven)
```

---

## 4. مصفوفة الشاشات الهندسية الشاملة (Engineering Matrix Table)

| Screen ID | Screen Name | Route (`Screen.*`) | Module | Permissions Required | Data Source | API Dependencies | Primary Actions | Implementation Status | Test Status |
|-----------|-------------|--------------------|--------|----------------------|-------------|------------------|-----------------|-----------------------|-------------|
| `AUTH_01` | تسجيل الدخول | `Login` | Auth | None (Public) | Local DB (`UserAccount`) | None (Local PBKDF2) | دخول، تبديل إلى تسجيل، استعادة | منجز ونشط | مختبر بوحدات JVM |
| `AUTH_02` | إنشاء حساب | `Login` (Sign Up) | Auth | None (Public) | Local DB (`UserAccount`) | None (Local PBKDF2) | تسجيل جديد، فحص قوة كلمة المرور | منجز ونشط | مختبر بوحدات JVM |
| `AUTH_03` | استعادة كلمة المرور | `Login` (Recover) | Auth | None (Public) | Local DB (`UserAccount`) | None (Local PBKDF2) | إرسال رمز الاستعادة، إعادة الضبط | منجز ونشط | مختبر بوحدات JVM |
| `DASH_01` | لوحة القيادة | `Dashboard` | Crash | `VIEWER`+ | Room DB / Flows | CrashRepository, AuthRepo | نظرة عامة، تنقل سريع، إشعارات | منجز ونشط | مختبر بوحدات JVM |
| `PROJ_01` | المشاريع | `Projects` | Crash | `VIEWER`+ | Room DB (`projects`) | CrashRepository | إضافة مشروع، تبديل، فحص الإحصاء | منجز ونشط | مختبر بوحدات JVM |
| `PROJ_02` | التطبيقات | `Applications` | Crash | `VIEWER`+ | Room DB (`applications`) | CrashRepository | إضافة تطبيق، ربط SDK، تصفح | منجز ومسجل بالمسار | تم التحقق بالبناء |
| `CRASH_01`| تقارير الانهيارات | `Crashes` | Crash | `VIEWER`+ | Room DB (`crashes`) | CrashRepository | فلترة، فرز حسب الخطورة والنسخة | منجز ونشط | مختبر بوحدات JVM |
| `CRASH_02`| تفاصيل الانهيار | `CrashDetail` | Crash | `DEVELOPER`+ | Room DB + Symbolication | CrashRepo, SymbolRepo, TeamOrch | فك الرموز، تشخيص، إحالة للفريق | منجز ونشط | مختبر بوحدات JVM |
| `DIAG_01` | تشخيص الذكاء | `CrashDetail` (Card) | Crash | `DEVELOPER`+ | AI Model Engine | AIProviderApi (Gemini/HF/OR) | تشخيص، استخراج السبب، اقتراح حل | منجز ونشط | مختبر بوحدات JVM |
| `FIX_01`  | مساعد الإصلاح | `FixAssistant` | Crash | `DEVELOPER`+ | Room DB (`fix_suggestions`) | FixAssistantViewModel, GitHubApi | فحص الـ Diff، إنشاء فرع وPR | منجز ونشط | مختبر بوحدات JVM |
| `FIX_02`  | سجل الإصلاحات | `FixHistory` | Crash | `VIEWER`+ | Room DB (`fix_history`) | FixHistoryViewModel | تصفح الإصلاحات السابقة، فحص PR | منجز ونشط | مختبر بوحدات JVM |
| `ISSUE_01`| المشاكل المجمعة | `Issues` | Crash | `VIEWER`+ | Room DB (`issues`) | CrashRepository | تصنيف حسب Fingerprint والاتجاه | منجز ونشط | مختبر بوحدات JVM |
| `TEST_01` | الاختبار المباشر | `LiveTesting` | Crash | `DEVELOPER`+ | Room DB (`test_sessions`) | LiveTestingRepository | بدء محاكاة، توليد سيناريو إجهاد | منجز ونشط | مختبر بوحدات JVM |
| `TEST_02` | جلسة الفحص الحي | `LiveSession` | Crash | `DEVELOPER`+ | Room DB (`test_events`) | LiveTestingRepository | مراقبة الـ SDK الحي، تسجيل عطل | منجز ونشط | مختبر بوحدات JVM |
| `ANLYT_01`| التحليلات البيانية | `Analytics` | Crash | `VIEWER`+ | Aggregated Crash Data | CrashRepository | تتبع معدل الاستقرار وتوزيع الأجهزة | منجز ونشط | مختبر بوحدات JVM |
| `INTEG_01`| التكاملات الآمنة | `Integrations` | Integ | `ADMIN`+ | Room DB (`integrations`) | CrashRepository, Webhook Engine | إضافة مزود، اختبار توقيع Webhook | منجز ونشط | مختبر بوحدات JVM |
| `GIT_01`  | تكامل GitHub | `GitHubConnect` | Integ | `DEVELOPER`+ | Room DB (`github_repos`) | GitHubApi | ربط مستودع، فحص الفروع والـ Commits | منجز ونشط | مختبر بوحدات JVM |
| `SYMB_01` | رموز التصحيح | `DebugSymbols` | Integ | `DEVELOPER`+ | Room DB (`debug_symbols`) | SymbolicationRepository | رفع mapping.txt، فحص التغطية | منجز ونشط | مختبر بوحدات JVM |
| `SETUP_01`| دليل الدمج | `SetupGuide` | Integ | `DEVELOPER`+ | SdkIntegrationModels | SdkIntegrationRepository | نسخ كود Gradle، فحص نبض الاتصال | منجز ونشط | مختبر بوحدات JVM |
| `AIMOD_01`| نماذج الذكاء وحماية التكلفة | `AIModels` | AI Hub | `DEVELOPER`+ | Room DB (`ai_providers`) | AIModelRepository, SecurityUtils | تفعيل Free AI Only، حفظ المفاتيح | منجز ونشط | مختبر بوحدات JVM |
| `TEAM_01` | قائمة الفرق | `ModelTeams` | Teams | `DEVELOPER`+ | Room DB (`model_teams`) | AIModelTeamRepository | استعراض، استخدام قوالب، تشغيل | منجز ونشط | مختبر بوحدات JVM |
| `TEAM_02` | إنشاء وتصميم الفريق | `CreateTeam` | Teams | `DEVELOPER`+ | Wizard Flow | AIModelTeamRepository | تعيين القائد، الأدوار، الحوكمة | منجز ومسجل بالمسار | مختبر بوحدات JVM |
| `TEAM_03` | تفاصيل وحوكمة الفريق | `TeamDetail` | Teams | `DEVELOPER`+ | Room DB (`team_members`) | AIModelTeamRepository | ضبط Debate Mode، فحص السجل | منجز ونشط | مختبر بوحدات JVM |
| `TEAM_04` | استهلاك الفريق | `TeamUsage` | Teams | `VIEWER`+ | Room DB (`team_usage`) | AIModelTeamRepository | مراقبة الحصص والتكلفة الصفرية | منجز ونشط | مختبر بوحدات JVM |
| `TEAM_05` | غرفة محادثة الفريق | `TeamChat` | Teams | `DEVELOPER`+ | TeamOrchestrator | TeamOrchestrator, AIProviderApi | تكليف بمهمة، إيقاف، استئناف، مراجعة | منجز ونشط | مختبر بوحدات JVM |
| `TEAM_06` | مركز الاعتماد E2E | `TeamValidation` | Teams | `ADMIN`+ | TeamValidationEngine | TeamValidationEngine | فحص معايير التعاون الـ 20 والـ Sandboxes | منجز ونشط | مختبر بـ 11 اختبار JVM |
| `VIBE_01` | أستوديو البرمجة | `VibeStudio` | Vibe | `DEVELOPER`+ | Room DB (`workspaces`) | VibeStudioRepository, AgentOrch | استعراض ملفات، تحرير، محاكاة حية | منجز ونشط | مختبر بوحدات JVM |
| `VIBE_02` | مركز تصحيح المطور | `DevDebugCenter` | Vibe | `DEVELOPER`+ | DevDebugCenterViewModel | DevDebugCenterViewModel | فحص بيئة الـ Loop الحية ومحاكاة SDK | منجز ونشط | مختبر بوحدات JVM |
| `SETT_01` | إعدادات النظام والأمان | `Settings` | Admin | `DEVELOPER`+ | UserAccount / SecurityEngine | AuthRepository, SecurityUtils | تغيير كلمة المرور، فحص التشفير | منجز ونشط | مختبر بوحدات JVM |

---

## 5. تقرير التدقيق المعماري والجاهزية (Architectural Audit Report)

### 1. إحصائيات الشاشات (Screen Count Summary):
- **إجمالي الشاشات الوظيفية المفهرسة بالمنصة:** 65 وظيفة وشاشة فرعية ضمن الأقسام الـ 7.
- **الشاشات الرئيسية المستقلة (Standalone Screens):** 24 شاشة رئيسية عبر `NavHost`.
- **المكونات واللوحات النمطية (Modular Panels & Dialogs):** 41 لوحة وحوار مدمج بتناغم (مثل File Explorer, Code Editor, Diff Viewer, Resource Limits, Activity Log).
- **الشاشات المكررة (Duplicate Screens):** **0 شاشات مكررة.** تم دمج الوظائف المتشابهة لتفادي التشتت (مثل دمج تبويبات المشاريع والتطبيقات والصلاحيات وسجل التدقيق في `ProjectsScreen`، وإدارة المفاتيح والتكلفة داخل `AIModelsScreen`).

### 2. تدقيق المسارات المفقودة وتصحيحها (Missing Routes Resolution):
- **المسار الأول المستكمل:** `Screen.Applications(projectId)`:
  - *الحالة السابقة:* كان معرفًا كفئة بيانات في `Screen.kt` ولكنه غير مسجل في `AppNavigation.kt`.
  - *الإجراء المتخذ:* تم تسجيل مسار `composable<Screen.Applications>` رسميًا في `AppNavigation.kt` مع ربطه بـ `ProjectsViewModel` لتحديد المشروع المختار تلقائيًا عند الانتقال المباشر.
- **المسار الثاني المستكمل:** `Screen.CreateTeam`:
  - *الحالة السابقة:* كان معرفًا في `Screen.kt` دون نقطة مسار في `AppNavigation.kt`.
  - *الإجراء المتخذ:* تم تسجيل `composable<Screen.CreateTeam>` في `AppNavigation.kt` بحيث يفتح شاشة إدارة الفرق مع تفعيل وضع المعالج `startCreateTeam()` تلقائيًا.

### 3. تدقيق الصفحات المعزولة (Orphaned / Disconnected Pages):
- **النتيجة:** لا توجد أي شاشات معزولة.
  - تم ربط كافة الشاشات بشريط التنقل السفلي (`Dashboard`, `Projects`, `Crashes`, `Issues`, `Settings`).
  - لوحة القيادة (`DashboardScreen`) تحتوي على بطاقات وصول مباشر إلى:
    - `Analytics` (التحليلات البيانية).
    - `LiveTesting` (مركز الاختبار المباشر).
    - `FixHistory` (سجل حلول الأعطال).
    - `VibeStudio` (أستوديو التطوير الذكي).
    - `DevDebugCenter` (مركز تصحيح ومحاكاة الانهيارات).
    - `ModelTeams` (فرق نماذج الذكاء الاصطناعي).
    - `TeamChat` (غرفة محادثة وتعاون الفريق).
    - `TeamValidation` (مركز اعتماد وتحقق الفرق الـ E2E).
    - `Notifications Sheet` (مركز الإشعارات الفوري).
  - تفاصيل الانهيار (`CrashDetailScreen`) ترتبط مباشرة بـ:
    - `DebugSymbols` (رفع وفك التشفير).
    - `TeamChat` (إحالة العطل لفريق النماذج المتعددة).
    - `FixAssistant` (مساعد الحل البرمجي).
    - `FixHistory` (سجل الاعتمادات السابقة).

### 4. حوكمة البيانات والوظائف المرتبطة بالـ Backend السحابي:
- **المصادقة والتشفير:**
  - مشفرة محليًا بـ PBKDF2 وAES-GCM (256-bit).
  - جاهزة للمزامنة السحابية عبر Firebase Authentication / Jetpack Credential Manager عند توفر الاتصال السحابي.
- **قواعد البيانات والتخزين الدائم:**
  - تدعم Room Database محليًا لسرعة الأداء والعمل دون اتصال (Offline-First).
  - تدعم المزامنة المعزولة مع Firebase Firestore لمشاريع المطورين والفرق.
- **حماية تكلفة الذكاء الاصطناعي (BYOK & Cost Protection):**
  - تفعيل وضع "Free AI Only" افتراضيًا يمنع أي استدعاء مدفوع ما لم يوافق المستخدم صراحة.
  - جميع مفاتيح الـ API مخزنة بتشفير لا يسمح بظهورها في الـ Logs أو واجهة المستخدم.

---
**الخلاصة الهندسية:**
تتمتع المنصة الآن ببنية معمارية متكاملة وقوية، وخريطة تنقل واضحة خالية من أي روابط ميتة أو شاشات وهمية، مع جاهزية كاملة للاستخدام المهني والإنتاجي.
