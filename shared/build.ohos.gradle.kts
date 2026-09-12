// 鸿蒙专用构建脚本：仅在 -c settings.ohos.gradle.kts 下生效
// 工具链为 Kotlin 2.0.21-KBA-010（见根 build.ohos.gradle.kts），产物 libshared.so
// 依赖统一使用 -2.0.21-ohos 变体（Kuikly 内核与 Markdown 均有 ohos 变体）
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
}

kotlin {
    ohosArm64 {
        binaries.sharedLib("shared") {
            freeCompilerArgs += "-Xadd-light-debug=enable"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyOhosVersion()}")
                implementation("com.tencent.kuikly-open:core-annotations:${Version.getKuiklyOhosVersion()}")
                // Markdown 渲染（AI 问答/结果页）：ohos 变体仅提供 2.0.21 版本，与鸿蒙工具链匹配
                implementation("com.tencent.kuiklybase:KuiklyMarkdown:1.0.6-2.0.21-ohos")
                // ⚠️ KuiklyMarkdown 的 klib 内部 dependsOn 了 atomicfu / coroutines / serialization，
                // 但它的发布元数据漏声明这些传递依赖（.module 里只有 kotlin-stdlib）→ 需在此显式补齐，
                // 否则 KLIB resolver 报 "Could not find org.jetbrains.kotlinx:atomicfu-cinterop-interop"。
                // 版本用腾讯镜像的鸿蒙适配版（-KBA / -kn 后缀，root module 含 ohosArm64 变体）。
                implementation("org.jetbrains.kotlinx:atomicfu:0.24.2.5-kn")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:2.0.21-coroutines-KBA-001")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.1-KBA-003")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1-KBA-003")
                implementation(project(":chart"))
                implementation(project(":table"))
            }
        }
    }
}

dependencies {
    add("kspOhosArm64", "com.tencent.kuikly-open:core-ksp:${Version.getKuiklyOhosVersion()}")
}
