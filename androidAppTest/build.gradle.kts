import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// Somewhere to try things out, so a change worth trying cannot land in the
// figures we rely on. It is the same app as :androidApp — same manifest, same
// activity, same shared module — differing only in its applicationId, its
// workspace and the mark on its icon.
//
// It is a module of its own rather than a product flavour because Android
// Studio's ▶ takes a flavour from the Build Variants panel rather than from the
// run configuration, and a choice that lives in a panel is a choice left
// wherever it was last: reach for the test build, get the real books. A module
// is a thing a run configuration can name, so there are two buttons and each
// one always does the same thing.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices) apply false
}

val applicationId = "pl.starocie.test"

val realConfig = rootProject.file("androidApp/google-services.json")
if (realConfig.exists()) {
    deriveGoogleServicesConfig()
    apply(plugin = libs.plugins.googleServices.get().pluginId)
} else {
    logger.lifecycle("androidAppTest: no google-services.json — Firebase disabled for this build")
}

android {
    namespace = "pl.starocie"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "pl.starocie.test"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-test"

        // The original workspace, everything entered while the app was being
        // built being exactly what a test build should have in it.
        buildConfigField("String", "WORKSPACE_ID", "\"starocie\"")
        resValue("string", "app_name", "starocie (test)")
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    sourceSets["main"].apply {
        manifest.srcFile("../androidHost/AndroidManifest.xml")
        kotlin.srcDirs("../androidHost/kotlin")
        // This module's own res holds the marked icons, and nothing else — every
        // other resource comes from the same place the real build's does.
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

/**
 * Writes this module's `google-services.json` from the real one next door.
 *
 * The google-services plugin fails any build whose package name has no client in
 * the file, so a second applicationId normally means a second Android app
 * registered in the Firebase console. Deriving the client from the real one
 * instead keeps the test build a checkbox rather than an errand — same project,
 * same API key, only the package name changed, and neither Firestore nor e-mail
 * sign-in reads anything else.
 *
 * Registering the test app properly is still the right move when it needs Google
 * sign-in, which is keyed to the package name and the signing fingerprint
 * together. The moment the real file carries a client for this applicationId,
 * that one is copied across untouched — this is a stopgap that steps aside.
 */
fun deriveGoogleServicesConfig() {
    val derived = file("google-services.json")

    fun packageNameOf(client: Any?): String? {
        @Suppress("UNCHECKED_CAST")
        val clientInfo = (client as? Map<String, Any?>)?.get("client_info") as? Map<String, Any?>
        val androidInfo = clientInfo?.get("android_client_info") as? Map<String, Any?>
        return androidInfo?.get("package_name") as? String
    }

    val slurper = JsonSlurper()
    val root = runCatching { slurper.parse(realConfig) }.getOrNull() as? MutableMap<String, Any?>
    val clients = root?.get("client") as? List<Any?>
    if (root == null || clients == null) {
        logger.warn("androidAppTest: androidApp/google-services.json is not readable as a Firebase config")
        return
    }

    if (clients.none { packageNameOf(it) == applicationId }) {
        val template = clients.firstOrNull { packageNameOf(it) == "pl.starocie" }
        if (template == null) {
            logger.warn("androidAppTest: no client for pl.starocie to derive the test build's config from")
            return
        }

        // Round-tripping through JSON is the deep copy: the slurped maps are
        // nested and shared, so editing the package name in place would rename
        // the real app.
        @Suppress("UNCHECKED_CAST")
        val copy = slurper.parseText(JsonOutput.toJson(template)) as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val copyInfo = copy["client_info"] as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val copyAndroidInfo = copyInfo["android_client_info"] as MutableMap<String, Any?>
        copyAndroidInfo["package_name"] = applicationId
        root["client"] = clients + copy
        logger.lifecycle("androidAppTest: derived google-services.json for $applicationId")
    }

    val text = JsonOutput.prettyPrint(JsonOutput.toJson(root)) + "\n"
    if (!derived.exists() || derived.readText() != text) derived.writeText(text)
}
