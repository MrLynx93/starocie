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
// meanwhile — Firebase is simply inert until the config arrives.
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
