import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// The release key. Android installs a new APK over the old one only when both carry the same key,
// so every release build must use this one. It never enters git: on Rafael's PC it is in
// ~/.wallet/android, in CI it comes from repository secrets (README, "App no tablet Android").
// Either the four ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and
// ANDROID_KEY_PASSWORD, or ANDROID_KEY_PROPERTIES with the path of a key.properties (storeFile,
// storePassword, keyAlias, keyPassword; a relative storeFile is next to it). Null when neither.
val releaseKey: Map<String, String>? = run {
    val variables = mapOf(
        "storeFile" to System.getenv("ANDROID_KEYSTORE_PATH"),
        "storePassword" to System.getenv("ANDROID_KEYSTORE_PASSWORD"),
        "keyAlias" to System.getenv("ANDROID_KEY_ALIAS"),
        "keyPassword" to System.getenv("ANDROID_KEY_PASSWORD"),
    ).mapValues { it.value.orEmpty() }
    val propertiesPath = System.getenv("ANDROID_KEY_PROPERTIES").orEmpty()
    when {
        variables.values.all { it.isNotBlank() } -> variables
        variables.values.any { it.isNotBlank() } -> throw GradleException(
            "Release signing needs all four of ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, " +
                "ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD, or none",
        )
        propertiesPath.isNotBlank() -> {
            val propertiesFile = file(propertiesPath)
            if (!propertiesFile.isFile) {
                throw GradleException("ANDROID_KEY_PROPERTIES points at $propertiesFile, which does not exist")
            }
            val properties = Properties().apply { propertiesFile.inputStream().use { load(it) } }
            val values = variables.keys.associateWith { properties.getProperty(it).orEmpty() }
            val missing = values.filterValues { it.isBlank() }.keys
            if (missing.isNotEmpty()) {
                throw GradleException("$propertiesFile lacks $missing")
            }
            values + ("storeFile" to propertiesFile.parentFile.resolve(values.getValue("storeFile")).path)
        }
        else -> null
    }
}

android {
    namespace = "com.wallet.wallet"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.wallet.wallet"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        releaseKey?.let { key ->
            create("release") {
                storeFile = file(key.getValue("storeFile"))
                storePassword = key.getValue("storePassword")
                keyAlias = key.getValue("keyAlias")
                keyPassword = key.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Without the release key, the debug key, so `flutter run --release` and a quick local
            // build still work. It is different on every machine: that APK installs, but a newer
            // one only goes over it after uninstalling, which erases the app's data.
            signingConfig = signingConfigs.getByName(if (releaseKey != null) "release" else "debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    // Theme.AppCompat for the launch theme; see res/values/styles.xml.
    implementation("androidx.appcompat:appcompat:1.7.1")
}
