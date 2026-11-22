# سجل التغييرات / Changelog

## 0.12.0 (build 17) - سطح المكتب وزر الرجوع + ROADMAP.md
- «موقع سطح المكتب» (menu_desktop_site) لكل تبويب عبر GeckoSessionSettings (USER_AGENT_MODE_DESKTOP / VIEWPORT_MODE_DESKTOP).
- زر الرجوع لم يعد يخرج من التطبيق: TabManager.closeToOpener و resetToHome، و Tab.openerId.
- ROADMAP.md: كل بنود المواصفات بحالتها.

## 0.11.1 (build 16) - تحصين بعد مراجعة يدوية لـ MainActivity
- فحص null صريح في handleExternalIntent بدل الاعتماد على smart cast من when على intent?.action.
- applyPrivateMode و كتابة نص العنوان لا يعملان إلا عند تغيّر الحالة (كانا يتكرران مع كل تحديث للتقدم).

## 0.11.0 (build 15) - بطاقة الثقة وزر الإنقاذ
- بطاقة ثقة محلية، زر «أنقذني من هذا الموقع»، ومنع التنزيل غير المطلوب (BlockReason.DOWNLOAD).
- ProtectionManager: isStrict/setStrict و shouldBlockAutoDownload، و Listener.onDownloadBlocked.

## 0.10.0 (build 14) - المواقع المحظورة ومشاركة القوائم
- قائمة مواقع محظورة بيد المستخدم (النطاق وفروعه)، منع التنقل إليها مع سماح مؤقت وسبب في التقرير.
- مشاركة قائمة مواقع كنص.
- BlockReason.BLOCKED، و ProtectionManager.Listener.onSiteBlocked.

## 0.9.1 (build 13) - البناء من ملف zip واحد
- الـ workflow يبني من vayro-*-full.zip في جذر المستودع إن وُجد، فيكفي رفع ملف واحد بدل عشرات الملفات بمساراتها.
- بدون zip يبني من ملفات المستودع كما كان.

## 0.9.0 (build 12) - الترجمة المحلية / On-device translation
- ترجمة الصفحة بمحرك GeckoView المحلي (TranslationsController.SessionTranslation)، إلى لغة التطبيق، مع "عرض الأصل".
- يُسأل المستخدم قبل تنزيل نموذج اللغة (يظهر الحجم)؛ النص لا يغادر الجهاز.

## 0.8.1 (build 11) - التحقق من اكتمال الملفات
- سجل البناء الأخير: 77 خطأ "Unresolved reference" في 6 ملفات فقط، كلها تشير إلى VayroApplication و MainActivity و RowAdapter.
  بقية الملفات التي تستعمل نفس الأصناف لم تُبلَّغ، وهذا نمط ملفات مفقودة من المستودع (الملفات الموجودة هي التي تشتكي من غياب الأخرى)، وليس خطأ في الكود.
- أُضيفت خطوة "Verify project files" أول الـ workflow: تفحص scripts/expected_files.txt وتطبع الملفات المفقودة بالاسم.

## 0.8.0 (build 10) - التشخيص واستعادة الانهيارات
- «حول التطبيق والتشخيص» مع تقرير قابل للمشاركة، وتقرير انهيار محلي يُعرض في التشغيل التالي.
- استعادة تبويب انهارت عملية محتواه (onCrash/onKill) بجلسة جديدة، مع حد 3 محاولات.
- BuildConfig.GECKOVIEW_VERSION.

## 0.7.0 (build 9) - المرحلتان 11 و12 / Phases 11 & 12
- ملء الشاشة للفيديو، والصورة داخل الصورة عند مغادرة التطبيق أثناءه.
- إضافة الموقع إلى الشاشة الرئيسية كاختصار (ShortcutManagerCompat).
- BrowserEngine: onFullScreenChanged و exitFullScreen.

## 0.6.0 (build 8) - المرحلتان 9 و10 / Phases 9 & 10
- فتح الروابط الخارجية (VIEW/SEND)، مشاركة الصفحة، التقدّم للأمام، تعيين المتصفح الافتراضي.
- اقتراحات العنوان من السجل والمفضلة المحليين.
- التصفح الخاص: تبويبات خاصة بجلسات معزولة، بلا سجل، ولا حفظ جلسة، ولا تسجيل حجب، مع FLAG_SECURE.
- MainActivity أصبح launchMode=singleTask.

## CI - حصة المرفقات
- فشل رفع المرفق بسبب امتلاء حصة تخزين Actions (لا علاقة له بالكود: الاختبارات والبناء نجحا).
- الـ APK يُنشر الآن كإصدار تجريبي واحد "latest-debug" يُستبدل في كل بناء ناجح على main، والمرفقات يوم واحد.

## 0.5.2 (build 7) - إصلاح أخطاء الترجمة / Compile fixes
- من سجل البناء الفعلي: ContentBlockingController.addException/removeException أُزيلت → استثناء الموقع صار إذن PERMISSION_TRACKING عبر StorageController.setPermission.
- GeckoRuntimeSettings.setAutoplayDefault أُزيل → منع التشغيل التلقائي عبر PermissionDelegate (AUTOPLAY_AUDIBLE/INAUDIBLE).
- خطأ في GeckoProtectionApplier: كان الصنف AntiTracking يُخزَّن في متغير (غير مسموح في Kotlin) → استُعملت الثوابت مباشرة.

## 0.5.1 (build 6) - إصلاح البناء / Build fix
- إصلاح فشل البناء الأول (سجل GitHub Actions): kotlin-stdlib 2.2.10 القادم مع GeckoView لا يقرؤه مترجم 2.0.21.
- رفع Kotlin إلى 2.2.10، واستبدال kotlinOptions (خطأ في 2.2) بـ compilerOptions.
- بنية المرحلتين 9/10 (التبويب الخاص، التقدّم للأمام، البحث المحلي) موجودة في الطبقات الداخلية فقط وغير مربوطة بالواجهة بعد.

## 0.5.0 (build 5) - المرحلتان 7 و8 / Phases 7 & 8
- صفحة رئيسية قابلة للتخصيص، مساحات عمل، اختصارات، مجموعات مواقع تُفتح بضغطة واحدة.
- قاعدة البيانات v4: المفضلة صارت لكل مساحة (ترحيل بيانات المستخدم إلى المساحة الرئيسية).
- TabManager: shutdown و openAllInNewTabs. مدير التبويبات صار تابعاً لمساحة العمل الحالية.
- اختبارات: HomeLayoutTest.

## 0.4.0 (build 4) - المرحلتان 5 و6 / Phases 5 & 6
- مدير التنزيلات: قائمة، استكمال، إعادة محاولة، بحث وتصنيف، خدمة أمامية، تحذير الملفات المشبوهة.
- توفير البيانات: منع التشغيل التلقائي، وضع صارم بلا صور مع استثناء لكل موقع، قياس الاستهلاك الفعلي.
- minSdk صار 29. قاعدة البيانات v3 (جدول التنزيلات).
- اختبارات: DownloadsTest و DataUsageMathTest.

## 0.3.0 (build 3) - المرحلتان 3 و4 / Phases 3 & 4
- مستويات الحماية الثلاثة، حجب الإعلانات والمتتبعات، النوافذ المنبثقة، التحويلات المريبة.
- حماية لكل موقع، سماح مؤقت، سجل حجب وتقارير (قاعدة البيانات v2).
- الكوكيز، مسح البيانات، أذونات الكاميرا والميكروفون والموقع.
- BrowserEngine: onOpenInNewTab و permissionHandler و setProtectionException (تعديل مقصود).
- اختبارات: ProtectionLogicTest.

## 0.2.0 (build 2) - المرحلة 2 / Phase 2
- تبويبات متعددة (TabManager) مع تعليق غير النشطة وتحميل كسول واستعادة الجلسة.
- سجل التصفح والمفضلة (SQLite محلي) بواجهة BottomSheet.
- BrowserEngine: إضافة onTitleChanged و detach (تعديل مقصود لدعم التبويبات).
- اختبارات: TabManagerTest + isWebUrl.

## 0.1.0 (build 1) - المرحلة 1 / Phase 1
- هيكل مشروع Android (com.vayro.browser) على GeckoView عبر واجهة BrowserEngine.
- الهوية البصرية، الوضعان الفاتح والداكن، العربية RTL والإنجليزية LTR.
- شريط عنوان وبحث، فتح، تحديث، تقدم التحميل، صفحة رئيسية.
- GitHub Actions + PROJECT_INFO.txt + UrlResolverTest.
