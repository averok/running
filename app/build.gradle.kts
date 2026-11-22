import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Versión de la app. Al publicar un release se cambia SOLO esto: versionCode se deriva de aquí
// (major*10000 + minor*100 + patch), así que siempre crece con la versión y nunca se desincronizan.
val appVersion = "0.3.0"

// Repositorio donde se publican los releases y que la app consulta para buscar actualizaciones.
val githubRepo = "averok/running"

// Datos de firma del release. Viven FUERA del repo (la clave es lo que impide que nadie suplante las
// actualizaciones). Si el archivo no existe, el release sale sin firmar y se puede compilar igualmente.
val signingPropsFile = file(
    System.getenv("RUNNING_SIGNING_PROPS") ?: "${System.getProperty("user.home")}/.config/running-signing/keystore.properties",
)
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) signingPropsFile.inputStream().use { load(it) }
}

android {
    namespace = "app.running"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.running"
        minSdk = 26
        targetSdk = 35
        val (major, minor, patch) = appVersion.split(".").map { it.toInt() }
        versionCode = major * 10_000 + minor * 100 + patch
        versionName = appVersion
    }

    signingConfigs {
        if (signingProps.isNotEmpty()) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Solo en debug se puede apuntar a otra URL (-PupdateUrl=...) para probar las actualizaciones
            // con un servidor local sin publicar nada.
            val url = (project.findProperty("updateUrl") as String?) ?: "https://api.github.com/repos/$githubRepo/releases/latest"
            buildConfigField("String", "UPDATE_URL", "\"$url\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "UPDATE_URL", "\"https://api.github.com/repos/$githubRepo/releases/latest\"")
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
}
