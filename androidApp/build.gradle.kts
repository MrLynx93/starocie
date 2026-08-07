import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    // Reads androidApp/google-services.json and generates the string resources
    // that Firebase's FirebaseInitProvider reads at startup. This is the whole
    // of Android Firebase initialisation — there is no init call in code.
    alias(libs.plugins.googleServices) apply false
}

// The real books and somewhere to try things out. They differ in exactly two
// ways: which workspace they read and write, and their applicationId — the
// second is what lets both sit on one phone, which is the whole point of having
// two. Everything else about them is the same build.
val prodApplicationId = "pl.starocie"
val testApplicationId = "$prodApplicationId.test"
val prodWorkspaceId = "starocie-prod"
val testWorkspaceId = "starocie"

// google-services.json is gitignored, so a fresh clone has none. Applying the
// plugin only when the file is present keeps the app buildable and runnable
// meanwhile — Firebase is simply inert until the config arrives.
val googleServicesConfig = file("google-services.json")
if (googleServicesConfig.exists()) {
    deriveTestGoogleServicesConfig()
    apply(plugin = libs.plugins.googleServices.get().pluginId)
} else {
    logger.lifecycle("androidApp: no google-services.json — Firebase disabled for this build")
}

android {
    namespace = "pl.starocie"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = prodApplicationId
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    // Both off by default in AGP 9. The flavours are the only thing that needs
    // them: one string constant for the workspace, one for the launcher label.
    buildFeatures {
        buildConfig = true
        resValues = true
    }

    // Which workspace a build talks to is decided here and nowhere else: the app
    // takes it as a parameter, so there is no default for a screen to fall back
    // to and no way for a test build to reach the real books by omission.
    flavorDimensions += "audience"

    productFlavors {
        create("prod") {
            dimension = "audience"
            buildConfigField("String", "WORKSPACE_ID", "\"$prodWorkspaceId\"")
            resValue("string", "app_name", "starocie")
        }

        // Gradle reserves the flavour name "test" — it would collide with the
        // unit-test source set — so the flavour is "dev" and everything a person
        // ever sees says test: the label, the id and the APK's own name.
        create("dev") {
            dimension = "audience"
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            buildConfigField("String", "WORKSPACE_ID", "\"$testWorkspaceId\"")
            resValue("string", "app_name", "starocie (test)")
        }
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

// One runner per build, so which of the two starts is a button rather than a
// setting remembered from last time. Android Studio's own ▶ takes the variant
// from the Build Variants panel instead of from the run configuration, which is
// exactly the kind of state that gets left where it was — and here that means
// reaching for the test build and getting the real books, or the other way
// round. These install and launch outright; the panel and ▶ are still the way
// in when a debugger has to be attached.
//
// A phone is picked with -Pdevice=<serial> when two are plugged in, which is
// what `adb devices` prints.
listOf("Prod" to prodApplicationId, "Dev" to testApplicationId).forEach { (flavour, appId) ->
    tasks.register<Exec>("run${flavour}Debug") {
        group = "install"
        description = "Installs and starts the ${flavour.lowercase()} build on a connected phone."
        dependsOn("install${flavour}Debug")

        val device = providers.gradleProperty("device").orNull
        commandLine(
            buildList {
                add(adbExecutable())
                if (device != null) addAll(listOf("-s", device))
                addAll(listOf("shell", "am", "start", "-n", "$appId/pl.starocie.MainActivity"))
            },
        )
    }
}

/**
 * Where the SDK is, asked in the order the machine answers it: the path Studio
 * wrote into `local.properties`, then the environment, then whatever is on PATH.
 * The last one is the fallback that keeps this a task failure with adb's own
 * message rather than a configuration error about a file nobody set.
 */
fun adbExecutable(): String {
    val sdkDir = rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.readLines()
        ?.firstOrNull { it.startsWith("sdk.dir=") }
        ?.substringAfter('=')
        ?.trim()
        ?: System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")

    val adb = sdkDir?.let { File(it, "platform-tools/adb") }
    return if (adb != null && adb.exists()) adb.absolutePath else "adb"
}

/**
 * Writes `src/dev/google-services.json` for the test build's own applicationId.
 *
 * The google-services plugin fails any variant whose package name has no client
 * in the file, so a second applicationId normally means a second Android app
 * registered in the Firebase console. Deriving the client from the real one
 * instead keeps the test build a checkbox rather than an errand — same project,
 * same API key, only the package name changed, and neither Firestore nor
 * e-mail sign-in reads anything else.
 *
 * Registering the test app properly is still the right move when it needs Google
 * sign-in, which is keyed to the package name and the signing fingerprint
 * together. The moment the real file carries a client for it, that one wins and
 * the derived copy is deleted — this is a stopgap that steps aside, not a fork.
 */
fun deriveTestGoogleServicesConfig() {
    val derived = file("src/dev/google-services.json")

    fun packageNameOf(client: Any?): String? {
        @Suppress("UNCHECKED_CAST")
        val clientInfo = (client as? Map<String, Any?>)?.get("client_info") as? Map<String, Any?>
        val androidInfo = clientInfo?.get("android_client_info") as? Map<String, Any?>
        return androidInfo?.get("package_name") as? String
    }

    val slurper = JsonSlurper()
    val root = runCatching { slurper.parse(googleServicesConfig) }.getOrNull() as? MutableMap<String, Any?>
    val clients = root?.get("client") as? List<Any?>
    if (root == null || clients == null) {
        logger.warn("androidApp: google-services.json is not readable as a Firebase config — the test build will not compile against it")
        return
    }

    if (clients.any { packageNameOf(it) == testApplicationId }) {
        // The console knows about the test app now. A copy left by an earlier
        // build would shadow the real file, so it goes.
        if (derived.exists()) {
            derived.delete()
            logger.lifecycle("androidApp: google-services.json now carries $testApplicationId — dropped the derived copy")
        }
        return
    }

    val template = clients.firstOrNull { packageNameOf(it) == prodApplicationId }
    if (template == null) {
        logger.warn("androidApp: google-services.json has no client for $prodApplicationId — nothing to derive the test build's config from")
        return
    }

    // Round-tripping through JSON is the deep copy: the slurped maps are nested
    // and shared, so editing the package name in place would rename the real app.
    @Suppress("UNCHECKED_CAST")
    val copy = slurper.parseText(JsonOutput.toJson(template)) as MutableMap<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val copyInfo = copy["client_info"] as MutableMap<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val copyAndroidInfo = copyInfo["android_client_info"] as MutableMap<String, Any?>
    copyAndroidInfo["package_name"] = testApplicationId

    root["client"] = clients + copy
    derived.parentFile.mkdirs()
    derived.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(root)) + "\n")
    logger.lifecycle("androidApp: derived src/dev/google-services.json for $testApplicationId")
}
