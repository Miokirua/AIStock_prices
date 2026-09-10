@echo off
rem AiStock_prices 鸿蒙跨端产物构建（Windows）
rem 前置：安装 DevEco Studio 5.1.0+（API >= 18），并设置环境变量
rem   OHOS_SDK_HOME = <DevEco>\sdk\default\openharmony
rem   TOOL_HOME     = <DevEco 安装根目录>
rem 用法：build_ohos.bat [Debug|Release]，默认 Debug

setlocal

if "%OHOS_SDK_HOME%"=="" (
  echo [ERROR] 未设置 OHOS_SDK_HOME，例如 D:\DevEco Studio\sdk\default\openharmony
  exit /b 1
)

set BUILD_TYPE=%1
if "%BUILD_TYPE%"=="" set BUILD_TYPE=Debug

if /i "%BUILD_TYPE%"=="Debug" (
  set LINK_TASK=linkDebugSharedOhosArm64
  set OUT_DIR=shared\build\bin\ohosArm64\sharedDebugShared
) else (
  set LINK_TASK=linkReleaseSharedOhosArm64
  set OUT_DIR=shared\build\bin\ohosArm64\sharedReleaseShared
)

echo [1/3] 编译鸿蒙跨端产物（Kotlin 2.0.21-KBA-010）
call gradlew.bat -c settings.ohos.gradle.kts :shared:%LINK_TASK%
if errorlevel 1 (
  echo [ERROR] Gradle 编译失败
  exit /b 1
)

echo [2/3] 拷贝 libshared.so
if not exist ohosApp\entry\libs\arm64-v8a mkdir ohosApp\entry\libs\arm64-v8a
copy /Y "%OUT_DIR%\libshared.so" ohosApp\entry\libs\arm64-v8a\ || exit /b 1

echo [3/3] 拷贝 libshared_api.h
if not exist ohosApp\entry\src\main\cpp\thirdparty\biz_entry mkdir ohosApp\entry\src\main\cpp\thirdparty\biz_entry
copy /Y "%OUT_DIR%\libshared_api.h" ohosApp\entry\src\main\cpp\thirdparty\biz_entry\ || exit /b 1

echo [OK] 产物已就位，可用 DevEco Studio 打开 ohosApp 运行
endlocal
