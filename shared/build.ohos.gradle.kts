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
                // 注：主构建（build.gradle.kts）里的 kotlinx-coroutines / kotlinx-serialization 此处不加：
                // ① 业务代码与 chart/table 均未直接使用（全仓 grep 无引用）；
                // ② 官方与腾讯镜像均未发布这两个库的 ohosArm64 klib 变体，加了会直接依赖解析失败。
                implementation(project(":chart"))
                implementation(project(":table"))
            }
        }
    }
}

dependencies {
    add("kspOhosArm64", "com.tencent.kuikly-open:core-ksp:${Version.getKuiklyOhosVersion()}")
}
