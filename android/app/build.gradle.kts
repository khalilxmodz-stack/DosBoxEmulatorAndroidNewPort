plugins {
    id("com.android.application")
}

// Directory containing SDL3's Java glue (org/libsdl/app/*.java). It MUST come from
// the same SDL release as the libSDL3.so that is packaged; the CI workflow clones the
// matching tag. Pass -Psdl.java.dir=/path/to/SDL/android-project/app/src/main/java
val sdlJavaDir: String = (findProperty("sdl.java.dir") as String?)
    ?: throw GradleException("Set -Psdl.java.dir to SDL3's android-project/app/src/main/java")

android {
    namespace = "io.dosboxstaging.android"
    compileSdk = 34
    defaultConfig {
        applicationId = "io.dosboxstaging.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.84.0-alpha-android1"
        // ARMv7 only. Nothing else may be packaged.
        ndk { abiFilters += "armeabi-v7a" }
    }
    sourceSets["main"].java.srcDir(sdlJavaDir)
    sourceSets["main"].jniLibs.srcDir("src/main/jniLibs")   // libmain.so + libSDL3.so (+ other shared deps)
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/dosbox-assets"))
    packaging { jniLibs { useLegacyPackaging = true } }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Bundle the repo's resources/ (shaders, keyboard layouts, code pages, ...) as APK
// assets under assets/resources/. They contain no commercial game data.
val copyDosboxResources by tasks.registering(Copy::class) {
    from("${rootDir}/../resources")
    into(layout.buildDirectory.dir("generated/dosbox-assets/resources"))
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(copyDosboxResources) }
