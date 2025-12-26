plugins {
    id("com.android.library") version "8.2.0"
    id("org.jetbrains.kotlin.android") version "1.9.22"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    id("maven-publish")
}

android {
    namespace = "com.speechmatics.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.annotation:annotation:1.7.1")

    // Unit Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.google.truth:truth:1.2.0")

    // Instrumented Testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("com.google.truth:truth:1.2.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "dev.dreamteam"
            artifactId = "speechmatics-android"
            version = "1.0.1"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Speechmatics Android SDK (Unofficial)")
                description.set("Unofficial Android SDK for Speechmatics speech recognition APIs - Community maintained")
                url.set("https://github.com/alexsorokoletov/speechmatics-kotlin-sdk")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("alexsorokoletov")
                        name.set("Alex Sorokoletov")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/alexsorokoletov/speechmatics-kotlin-sdk.git")
                    developerConnection.set("scm:git:ssh://github.com/alexsorokoletov/speechmatics-kotlin-sdk.git")
                    url.set("https://github.com/alexsorokoletov/speechmatics-kotlin-sdk")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/alexsorokoletov/speechmatics-kotlin-sdk")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String? ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") as String? ?: ""
            }
        }
    }
}
