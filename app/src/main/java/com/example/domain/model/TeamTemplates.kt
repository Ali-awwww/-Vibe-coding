package com.example.domain.model

object TeamTemplates {
    val VIBE_DEVELOPMENT_TEAM = ModelTeam(
        id = "vibe_dev_template",
        name = "Vibe Development Team",
        description = "فريق متكامل لبناء التطبيقات من الفكرة إلى التشغيل.",
        goal = "بناء تطبيقات عالية الجودة ومتكاملة.",
        collaborationStyle = CollaborationStyle.VIBE_DEVELOPMENT,
        members = listOf(
            TeamMember(role = TeamRole.TEAM_LEADER, name = "Lead Architect", instructions = "Manage the team, distribute tasks, and ensure quality."),
            TeamMember(role = TeamRole.CUSTOM, name = "Product Analyst", customRoleName = "Product Analyst", instructions = "Analyze requirements and create product plans."),
            TeamMember(role = TeamRole.SOFTWARE_ARCHITECT, name = "System Architect", instructions = "Design technical architecture and stack."),
            TeamMember(role = TeamRole.CUSTOM, name = "UI/UX Designer", customRoleName = "UI/UX Designer", instructions = "Design user interfaces and experience."),
            TeamMember(role = TeamRole.FRONTEND_DEVELOPER, name = "Frontend Engineer", instructions = "Implement the UI components."),
            TeamMember(role = TeamRole.BACKEND_DEVELOPER, name = "Backend Engineer", instructions = "Implement APIs and business logic."),
            TeamMember(role = TeamRole.DATABASE_ENGINEER, name = "Data Engineer", instructions = "Design and implement database schema."),
            TeamMember(role = TeamRole.SECURITY_AUDITOR, name = "Security Officer", instructions = "Review security and permissions."),
            TeamMember(role = TeamRole.CODE_REVIEWER, name = "Quality Lead", instructions = "Review code and enforce standards."),
            TeamMember(role = TeamRole.QA_ENGINEER, name = "Test Engineer", instructions = "Write and execute tests.")
        )
    )

    val CRASH_INVESTIGATION_TEAM = ModelTeam(
        id = "crash_investigation_template",
        name = "AI Crash Investigation Team",
        description = "فريق تحقيق تخصصي متعدد النماذج لاكتشاف وتحليل الأسباب الجذرية للأعطال والانهيارات البرمجية واقتراح واختبار الحلول.",
        goal = "التحقيق الدقيق في الانهيارات البرمجية وتحديد السبب الجذري واقتراح واختبار الإصلاحات دون اختلاق أدلة.",
        collaborationStyle = CollaborationStyle.CRASH_INVESTIGATION,
        members = listOf(
            TeamMember(
                id = "leader_investigator",
                name = "Investigation Leader",
                role = TeamRole.TEAM_LEADER,
                instructions = "إدارة مسار التحقيق، التوفيق بين مساهمات الأعضاء، وصياغة التقرير الشامل النهائي والتوصيات."
            ),
            TeamMember(
                id = "crash_analyst",
                name = "Crash Analyst",
                role = TeamRole.CRASH_ANALYST,
                instructions = "تحليل تقرير العطل وتحديد نوع المشكلة والبيئة وشدة التأثير، والتمييز بين Crash و ANR و Non-Fatal."
            ),
            TeamMember(
                id = "stack_specialist",
                name = "Stack Trace Specialist",
                role = TeamRole.STACK_TRACE_SPECIALIST,
                instructions = "تحليل Stack Trace واستخراج مسار التنفيذ والـ Call Stack وفك التعتيم (Symbolication) بدقة دون اختلاق أسطر."
            ),
            TeamMember(
                id = "source_investigator",
                name = "Source Code Investigator",
                role = TeamRole.SOURCE_CODE_INVESTIGATOR,
                instructions = "البحث داخل ملفات المشروع ومطابقة الانهيار مع أسطر الكود الحقيقية والإصدار المعني."
            ),
            TeamMember(
                id = "root_cause_analyst",
                name = "Root Cause Analyst",
                role = TeamRole.ROOT_CAUSE_ANALYST,
                instructions = "دراسة الأدلة وتحديد السبب الجذري والتمييز الصارم بين السبب المؤكد والمحتمل وغير المعروف."
            ),
            TeamMember(
                id = "security_auditor",
                name = "Security Auditor",
                role = TeamRole.SECURITY_AUDITOR,
                instructions = "مراجعة الأبعاد الأمنية للانهيار (تسريب بيانات، هجمات حقن، ثغرات معالجة، شروط سباق)."
            ),
            TeamMember(
                id = "code_reviewer",
                name = "Code Reviewer",
                role = TeamRole.CODE_REVIEWER,
                instructions = "مراجعة التشخيص والـ Patch المقترح للتأكد من الجودة وعدم إدخال انحدار برمجي أو أخطاء جديدة."
            ),
            TeamMember(
                id = "fix_developer",
                name = "Fix Developer",
                role = TeamRole.FIX_DEVELOPER,
                instructions = "اقتراح التعديلات البرمجية والـ Patch المناسب لعلاج المشكلة دون إدخال أخطاء جانبية."
            ),
            TeamMember(
                id = "qa_engineer",
                name = "QA Engineer",
                role = TeamRole.QA_ENGINEER,
                instructions = "تصميم سيناريوهات الاختبار وإعادة إنتاج العطل والتحقق الصارم من فعالية الإصلاح في بيئة الاختبار."
            )
        )
    )
}
