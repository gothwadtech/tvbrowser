plugins {
    id("tvbrowser.android.library")
}

android {
    namespace = "com.gothwad.tvbrowser.webengine.gecko"
    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":app:common"))
    implementation(libs.androidx.appcompat)
    implementation(libs.geckoview)
    testImplementation(libs.junit)
}
