plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.xlines.app"
 compileSdk = 35
 defaultConfig { applicationId = "com.xlines.app"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.6.0" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildTypes { release { isMinifyEnabled = false } }
 packaging { jniLibs { useLegacyPackaging = true }; resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
}
dependencies {
 implementation(files("libs/libbox.aar"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation(platform("androidx.compose:compose-bom:2025.04.01"))
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.foundation:foundation")
 implementation("com.journeyapps:zxing-android-embedded:4.3.0")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
}
