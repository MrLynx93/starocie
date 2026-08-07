plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    // Reads androidApp/google-services.json and generates the string resources
    // that Firebase's FirebaseInitProvider reads at startup. This is the whole
    // of Android Firebase initialisation — there is no init call in code.
    alias(libs.plugins.googleServices) apply false
}

// google-services.json is gitignored, so a fresh clone has none. Applying the
// plugin only when the file is present keeps the app buildable and runnable
// meanwhile — Firebase is simply inert until the config arrives. The test build
// derives its own copy from this one; see androidAppTest.
val googleServicesConfig = file("google-services.json")
if (googleServicesConfig.exists()) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
} else {
    logger.lifecycle("androidApp: no google-services.json — Firebase disabled for this build")
}

android {
    namespace = "pl.starocie"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "pl.starocie"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        // The real books. Which workspace a build talks to is decided here and
        // nowhere else: App() takes it as a parameter, so there is no default
        // for a screen to fall back to and no way to reach these by omission.
        buildConfigField("String", "WORKSPACE_ID", "\"starocie-prod\"")
        resValue("string", "app_name", "starocie")
    }

    // Both off by default in AGP 9. One string constant for the workspace, one
    // for the launcher label, and that is the whole of what separates the two
    // builds from one another.
    buildFeatures {
        buildConfig = true
        resValues = true
    }

    // The activity, the manifest and everything about the app that is not its
    // identity live in androidHost/ and are compiled into both builds. Only the
    // launcher icons are this module's own.
    sourceSets["main"].apply {
        manifest.srcFile("../androidHost/AndroidManifest.xml")
        kotlin.srcDirs("../androidHost/kotlin")
        res.srcDirs("src/main/res", "../androidHost/res")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
}
