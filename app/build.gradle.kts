plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.eottadwotji"
    // v5.4: 36 — Android 16 Live Updates(승격 알림) API. targetSdk는 35 유지(동작 변경 회피)
    compileSdk = 36

    defaultConfig {
        applicationId = "com.eottadwotji"
        minSdk = 26
        targetSdk = 35
        // CI(GitHub Actions)가 -PversionCode/-PversionName으로 주입 — 앱 내 업데이트 비교 기준
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0-dev"
    }

    // 릴리스 서명: CI는 시크릿에서 복원한 release.keystore 사용 (로컬엔 gitignore로 보관)
    signingConfigs {
        create("release") {
            val ksFile = rootProject.file("release.keystore")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "carwhere2026"
                keyAlias = System.getenv("KEY_ALIAS") ?: "carwhere"
                keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: "carwhere2026"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (rootProject.file("release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true // 디버그 빌드 한정 감지 시뮬레이션 버튼용
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0") // setRequestPromotedOngoing/setShortCriticalText
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // 주차 확정 시 마지막 위치 1회 조회 (상시 추적 금지 — CLAUDE.md 절대 규칙 6)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // v2: 주차 히스토리 (Room)
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // v2: 홈 위젯 2종 (Glance)
    implementation("androidx.glance:glance-appwidget:1.1.1")

    // v4.2: 대시보드 지도 카드 — OSM 타일(API 키 불필요), 캐시는 앱 cacheDir
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
