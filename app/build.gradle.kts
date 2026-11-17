plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// قراءة الإصدار ومحرك GeckoView من gradle.properties
val vayroVersionName = providers.gradleProperty("VAYRO_VERSION_NAME").get()
val vayroVersionCode = providers.gradleProperty("VAYRO_VERSION_CODE").get().toInt()
val geckoArtifact = providers.gradleProperty("GECKOVIEW_ARTIFACT").get()
val geckoVersion = providers.gradleProperty("GECKOVIEW_VERSION").get()

android {
    namespace = "com.vayro.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vayro.browser" // الحزمة ثابتة ولا تتغير مع التحديثات
        minSdk = 29 // المرحلة 5: الحفظ عبر MediaStore (مجلد Download) يتطلب Android 10 فأعلى
        targetSdk = 35
        versionCode = vayroVersionCode
        versionName = vayroVersionName
        // يظهر في تقرير التشخيص
        buildConfigField("String", "GECKOVIEW_VERSION", "\"$geckoVersion\"")
        // الحزمة المستخدمة لمعمارية arm64 فقط؛ غيّر GECKOVIEW_ARTIFACT لدعم معماريات أخرى
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // سيُفعَّل في مرحلة لاحقة بعد اختبار قواعد GeckoView
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true // مطلوب لـ buildConfigField
    }
}

// Kotlin 2.2: الصيغة الجديدة بدل kotlinOptions (التي أصبحت خطأً)
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("org.mozilla.geckoview:$geckoArtifact:$geckoVersion")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // المرحلة 2: قوائم التبويبات والسجل والمفضلة
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
}
