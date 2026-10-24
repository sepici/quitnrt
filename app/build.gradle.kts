import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.roborazzi)
    // For the AppFunctions compiler, which writes the agents' service and its function list.
    alias(libs.plugins.ksp)
}

// Release signing with the upload key comes from Gradle properties (for example in
// ~/.gradle/gradle.properties) or environment variables of the same names. It's used only when
// all four are set; otherwise the release build is signed with the debug key, so a shrunk build
// can still be installed for testing. See docs/RELEASE.md.
val uploadKey: Map<String, String?> = listOf(
    "LASTHIT_UPLOAD_STORE_FILE",
    "LASTHIT_UPLOAD_STORE_PASSWORD",
    "LASTHIT_UPLOAD_KEY_ALIAS",
    "LASTHIT_UPLOAD_KEY_PASSWORD",
).associateWith { name ->
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull?.takeIf { it.isNotBlank() }
}
val hasUploadKey = uploadKey.values.all { it != null }
if (!hasUploadKey && uploadKey.values.any { it != null }) {
    logger.warn(
        "Release signing: ${uploadKey.filterValues { it == null }.keys.joinToString()} not set, " +
            "so the release build is signed with the debug key.",
    )
}

android {
    namespace = "app.thelasthit.nrt"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.thelasthit.nrt"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = file(uploadKey.getValue("LASTHIT_UPLOAD_STORE_FILE")!!)
                storePassword = uploadKey.getValue("LASTHIT_UPLOAD_STORE_PASSWORD")
                keyAlias = uploadKey.getValue("LASTHIT_UPLOAD_KEY_ALIAS")
                keyPassword = uploadKey.getValue("LASTHIT_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasUploadKey) "upload" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric needs the merged resources to render Compose screens and their fonts.
        unitTests.isIncludeAndroidResources = true
        // Robolectric's Android 16, which the AppFunctions tests run on, reads a JDK internal.
        unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
}

/**
 * Fails when the release APK or bundle lacks a file from :core:domain's resources. The domain
 * reads them by name (the catalogue, sprites, backdrops and advice), so a missing one would only
 * show up as a crash on the phone.
 */
abstract class CheckReleaseResources : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val domainResources: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apkDirectory: DirectoryProperty

    @get:Internal
    abstract val apkLoader: Property<BuiltArtifactsLoader>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundle: RegularFileProperty

    @TaskAction
    fun check() {
        val root = domainResources.get().asFile
        val wanted = root.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()
        check(wanted.isNotEmpty()) { "No resources found in $root" }

        val apks = apkLoader.get().load(apkDirectory.get())?.elements?.map { File(it.outputFile) }.orEmpty()
        check(apks.isNotEmpty()) { "No release APK in ${apkDirectory.get().asFile}" }
        apks.forEach { requireEntries(it, wanted, prefix = "") }
        // A bundle keeps Java resources under its base module's root folder.
        requireEntries(bundle.get().asFile, wanted, prefix = "base/root/")
    }

    private fun requireEntries(archive: File, wanted: List<String>, prefix: String) {
        val missing = ZipFile(archive).use { zip -> wanted.filter { zip.getEntry(prefix + it) == null } }
        check(missing.isEmpty()) { "${archive.name} is missing ${missing.joinToString()}" }
        logger.lifecycle("${archive.name} has all ${wanted.size} domain resources: ${wanted.joinToString()}")
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        tasks.register<CheckReleaseResources>("check${variant.name.replaceFirstChar { it.uppercase() }}Resources") {
            group = "verification"
            description = "Checks the release APK and bundle carry every resource :core:domain loads by name."
            domainResources.set(layout.projectDirectory.dir("../core/domain/src/main/resources"))
            apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
            apkLoader.set(variant.artifacts.getBuiltArtifactsLoader())
            bundle.set(variant.artifacts.get(SingleArtifact.BUNDLE))
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // AppFunctions (Android 16 and newer), all in the app's agents package.
    implementation(libs.androidx.appfunctions)
    ksp(libs.androidx.appfunctions.compiler)

    // Compose UI tests and Roborazzi screenshots run on the JVM under Robolectric, so CI needs no emulator.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.appfunctions.testing)
}
