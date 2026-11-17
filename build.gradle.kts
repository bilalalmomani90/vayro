// ملف البناء الجذري: يعرّف إضافات البناء فقط ولا يطبقها
// إصلاح من سجل البناء الفعلي: GeckoView يجلب kotlin-stdlib 2.2.10، والمترجم 2.0.21 لا يقرأ بياناته،
// فرُفع Kotlin إلى 2.2.10 ليطابقها
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
}
