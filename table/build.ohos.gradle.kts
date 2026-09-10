// 鸿蒙专用构建脚本：仅在 -c settings.ohos.gradle.kts 下生效
plugins {
    kotlin("multiplatform")
}

kotlin {
    ohosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyOhosVersion()}")
                implementation("com.tencent.kuikly-open:core-annotations:${Version.getKuiklyOhosVersion()}")
            }
        }
    }
}
