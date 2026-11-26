# Crash Intelligence AI — ذكاء الأعطال (Vibe Coding)

> أداة أندرويد للمطوّرين لمراقبة الأعطال وتحليلها وحلّها، تعتمد على الذكاء الاصطناعي. بُني هذا المستودع بأسلوب Vibe Coding في Google AI Studio.

## ما هو التطبيق؟

«Crash Intelligence AI» تطبيق أندرويد أصلي (Native) يساعد مطوّري التطبيقات على فهم أعطال تطبيقاتهم بسرعة: يربط مصادر الأعطال، ويحلّل الأسباب الجذرية بالذكاء الاصطناعي عبر Firebase AI Logic (Gemini)، ويقترح إصلاحات مدعومة بسياق المشروع.

## ماذا يفعل؟ وأهم الميزات

- **مراقبة الأعطال:** ربط Sentry أو BugSnag أو مصادر التكامل الموثّقة لقراءة تقارير الأعطال، مع جمع أعطال التطبيق نفسه عبر Firebase Crashlytics.
- **تحليل بالذكاء الاصطناعي:** تشخيص الأعطال واقتراح الإصلاحات عبر Firebase AI Logic (`gemini-3.8-flash` مع احتياطي `gemini-3.5-flash-lite` عند الازدحام 503).
- **إدارة المهام والفرق:** تحويل الأعطال إلى مهام عمل، وإدارة فرق نماذج الذكاء الاصطناعي وسجل الاستخدام.
- **لوحات وشاشات منظمة:** شاشات متعددة لعرض الأعطال والتفاصيل والمهام، موثّقة في مجلد `docs/`.
- **تصميم «Vibe Engineering»:** نظام تصميم موحّد مبني حول فلسفة Vibe.
- **خلفية سحابية:** Firebase Authentication (تسجيل الدخول بالبريد/كلمة المرور أو Google عبر Credential Manager) وCloud Firestore وFirebase Crashlytics وFirebase App Check، مع تخزين محلي عبر Room.

## التقنيات المستخدمة

- **التطبيق:** أندرويد أصلي (Native Android — Kotlin / Jetpack Compose / Material 3) — انظر `app/`
- **الذكاء الاصطناعي:** Firebase AI Logic (Gemini)
- **الخلفية والمراقبة:** Firebase Authentication وCloud Firestore وFirebase Crashlytics وFirebase App Check
- **البناء:** Gradle (باستخدام Kotlin DSL)

## التثبيت والاستخدام

### للمطوّر (التشغيل من المصدر)

```bash
git clone https://github.com/Ali-awwww/-Vibe-coding.git
cd -Vibe-coding
```

1. افتح المشروع في Android Studio أو بيئة AI Studio Build.
2. تأكد من وجود ملف إعدادات Firebase (`app/google-services.json` و`app/src/main/res/values/firebase_applet_config.xml`) المهيّأ للمشروع.
3. ابنِ التطبيق وشغّله عبر Gradle:

```bash
gradle assembleDebug
```

## بنية المشروع

- `app/` — كود تطبيق أندرويد.
- `docs/` — وثائق الشاشات والمهام ونظام التصميم.
- `firebase.json` و`firestore.rules` — إعدادات Firebase وقواعد Firestore الأمنية.
- `.aistudio/` — ملفات Google AI Studio.
- `metadata.json` — بيانات وصف المشروع.
