# خطة إضافة مسارات العمل الإضافية (Full CI/CD Workflows Suite)

بناءً على اختيارك، سيتم إضافة حزمة متكاملة من مسارات عمل **GitHub Actions** لتطوير واختبار وتوزيع مشروع **Crash Intelligence AI** بمواصفات احترافية عالية الدقة.

> [!IMPORTANT] **القرارات المؤكدة بناءً على إجاباتك:**
> 1. **Lint & Security Audit Workflow**: فحص جودة الكود والثغرات والأخطاء البرمجية تلقائيًا.
> 2. **Pull Request Validation Workflow**: فحص صرامة التغييرات المقترحة في طلبات السحب (PRs) لمنع تكسر البناء.
> 3. **Release Automation Workflow**: إنشاء Release آلي رفع ملفات الـ APK والـ AAB عند إرسال Git Tag جديد (مثل `v1.0.0`).

---

## 1. Overview & Core Concept

توسيع بنية الـ CI/CD في repository المشروع ليشمل 3 مسارات عمل إضافية متكاملة تعمل جنباً إلى جنب مع مسار البناء البسيط الحالي (`android.yml`):

- **فحص الجودة والأمان (`code_quality.yml`)**: تشغيل Android Lint وفحوصات السلامة والأمان للأكواد والمكتبات المستوردة.
- **التحقق من طلبات السحب (`pr_validation.yml`)**: تشغيل الفحوصات والاختبارات السريعة عند فتح أو تحديث أي Pull Request متجهة لـ `main` أو `master`.
- **أتمتة التوزيع والإصدارات (`release.yml`)**: عند عمل Tag جديد بنمط الإصدارات، يتم بناء APK و Release AAB وإصدار GitHub Release مرفق مع سجل التغييرات والملفات التنفيذية.

---

## 2. GitHub Workflows Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                      GitHub Event Triggers                             │
├─────────────────┬──────────────────────┬───────────────────────────────┤
│ Push / Scheduled│ Pull Request (PR)    │ Git Tag Push (v*.*.*)         │
└────────┬────────┴──────────┬───────────┴──────────────┬────────────────┘
         │                   │                          │
         ▼                   ▼                          ▼
┌─────────────────┐ ┌──────────────────┐ ┌─────────────────────────────┐
│ Quality & Security│ │ PR Validation    │ │ Release & Package Generator │
│ (code_quality)  │ │ (pr_validation)  │ │ (release.yml)               │
├─────────────────┤ ├──────────────────┤ ├─────────────────────────────┤
│ • Android Lint  │ │ • Fast Unit Tests│ │ • Build Release APK         │
│ • Dependency    │ │ • Build Check    │ │ • Build Bundle (AAB)        │
│   Check         │ │ • PR Summary Log │ │ • Create GitHub Release     │
└─────────────────┘ └──────────────────┘ └─────────────────────────────┘
```

---

## 3. Workflow Specifications

### 1. `code_quality.yml` (Quality & Security Audit)
- **المحفز (Triggers)**: `push` لفرع `main` و `master` بالإضافة لتشغيل أسبوعي مجدول (`cron`).
- **المهام**:
  - تشغيل `gradle lintDebug`.
  - توليد ورفع تقرير Android Lint كـ Artifact.
  - فحص ثغرات Dependency وتدقيق السلامة العامة للأكواد.

### 2. `pr_validation.yml` (Pull Request Gatekeeper)
- **المحفز (Triggers)**: `pull_request` المستهدفة لفروع `main` و `master`.
- **المهام**:
  - سحب الكود واختبار البناء التجميعي السريع.
  - تشغيل اختبارات الوحدة (`testDebugUnitTest`).
  - التأكد من عدم وجود أخطاء تكسيرية قبل الموافقة على الدمج (Merge).

### 3. `release.yml` (Automated Release Publisher)
- **المحفز (Triggers)**: `push` للوسوم (Tags) التي تبدأ بـ `v*` (مثل `v1.0.0`, `v1.1.0-beta`).
- **المهام**:
  - بناء `assembleRelease` لملف APK الخاص بالإصدار الرسمية.
  - بناء `bundleRelease` لملف AAB الخاص بالنشر على متجر Google Play.
  - استخدام `softprops/action-gh-release@v2` لإنشاء GitHub Release تلقائي وترفيق الملفات المبنية للتحميل المباشر.

---

## 4. Plan Execution Steps

1. إنشاء ملف `.github/workflows/code_quality.yml`
2. إنشاء ملف `.github/workflows/pr_validation.yml`
3. إنشاء ملف `.github/workflows/release.yml`
4. تشغيل `compile_applet` للتحقق من سلامة البناء وتوافق التطبيق.
