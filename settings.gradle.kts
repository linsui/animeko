/*
 * Ani
 * Copyright (C) 2022-2024 Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

rootProject.name = "ani"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") // Compose Multiplatform pre-release versions
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

fun includeProject(projectPath: String, dir: String? = null) {
    include(projectPath)
    if (dir != null) project(projectPath).projectDir = file(dir)
}

// Utilities shared by client and server (targeting JVM)
includeProject(":utils:platform") // 适配各个平台的基础 API
includeProject(":utils:logging") // shared by client and server (targets JVM)
includeProject(":utils:serialization", "utils/serialization")
includeProject(":utils:coroutines", "utils/coroutines")
includeProject(":utils:ktor-client", "utils/ktor-client")
includeProject(":utils:io", "utils/io")
includeProject(":utils:testing", "utils/testing")
includeProject(":utils:xml")
includeProject(":utils:bbcode", "utils/bbcode")
includeProject(":utils:bbcode:test-codegen")
includeProject(":utils:ip-parser", "utils/ip-parser")


includeProject(":torrent:torrent-api", "torrent/api") // Torrent 系统 API
includeProject(":torrent:anitorrent")
includeProject(":torrent:anitorrent:anitorrent-native")

// client
includeProject(":app:shared", "app/shared") // shared by clients (targets JVM)
includeProject(":app:desktop", "app/desktop") // desktop JVM client for macOS, Windows, and Linux
includeProject(":app:android", "app/android") // Android client
includeProject(":app:shared:image-viewer")
includeProject(":client")

// server
//includeProject(":server:core", "server/core") // server core
//includeProject(":server:database", "server/database") // server database interfaces
//includeProject(":server:database-xodus", "server/database-xodus") // database implementation with Xodus

// data sources
includeProject(":datasource:datasource-api", "data-sources/api") // data source interfaces: Media, MediaSource 
includeProject(":datasource:datasource-api:test-codegen", "data-sources/api/test-codegen") // 生成单元测试
includeProject(
    ":datasource:datasource-core",
    "data-sources/core",
) // data source managers: MediaFetcher, MediaCacheStorage
includeProject(":datasource:bangumi", "data-sources/bangumi") // https://bangumi.tv
//   BT 数据源
includeProject(":datasource:dmhy", "data-sources/bt/dmhy") // https://dmhy.org
includeProject(":datasource:acg-rip", "data-sources/bt/acg.rip") // https://acg.rip
includeProject(":datasource:mikan", "data-sources/bt/mikan") // https://mikanani.me/
//   Web 数据源
includeProject(":datasource:web-base", "data-sources/web/web-base") // web 基础
includeProject(":datasource:nyafun", "data-sources/web/nyafun") // https://nyafun.net/
includeProject(":datasource:mxdongman", "data-sources/web/mxdongman") // https://mxdm4.com/
includeProject(":datasource:ntdm", "data-sources/web/ntdm") // https://ntdm.tv/
includeProject(":datasource:gugufan", "data-sources/web/gugufan")
includeProject(":datasource:xfdm", "data-sources/web/xfdm")
includeProject(":datasource:jellyfin", "data-sources/jellyfin")
includeProject(":datasource:ikaros", "data-sources/ikaros") // https://ikaros.run/

// danmaku
includeProject(":danmaku:danmaku-api", "danmaku/api")
//includeProject(":danmaku:ui", "danmaku/ui") // danmaku UI composable
//includeProject(":danmaku:dandanplay", "danmaku/dandanplay")
//includeProject(":danmaku:ani:client", "danmaku/ani/client") // danmaku server

includeProject(
    ":datasource:dmhy:dataset-tools",
    "data-sources/bt/dmhy/dataset-tools",
) // tools for generating dataset for ML title parsing

// ci
includeProject(":ci-helper", "ci-helper") // 

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")


/// Composite Builds


// https://github.com/aclassen/ComposeReorderable
fun getMissingSubmoduleMessage(moduleName: String) = """
        未找到 $moduleName, 这是因为没有正确 clone 或有新 submodule 导致的. 可尝试下列任意一种方法解决:
        1. `git submodule update --init --recursive`
        2. 使用 Android Studio 的 New Project from Version Control 创建项目, 而不要使用命令行 clone
        3. 使用命令行时确保带上 recursive 选项: `git clone --recursive git@github.com:open-ani/ani.git`
        """.trimIndent()
if (file("app/shared/reorderable").run { !exists() || listFiles().isNullOrEmpty() }) {
    error(getMissingSubmoduleMessage("""app/shared/reorderable"""))
}

if (file("torrent/anitorrent/anitorrent-native/libs/boost").run { !exists() || listFiles().isNullOrEmpty() }) {
    error(getMissingSubmoduleMessage("""torrent/anitorrent/anitorrent-native/libs/boost"""))
}

includeBuild("app/shared/reorderable") {
    dependencySubstitution {
        substitute(module("org.burnoutcrew.composereorderable:reorderable")).using(project(":reorderable"))
    }
}
