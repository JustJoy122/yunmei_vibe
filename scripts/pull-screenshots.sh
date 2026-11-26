#!/usr/bin/env bash
# 本地截图流水线：装包 → 跑 instrumentation → 拉图到 build/screenshots/
#
# 前置：
#   1. 设备已连接且**保持解锁亮屏**（UiAutomator 在锁屏/Doze 下看不到界面）：
#        adb shell svc power stayon true
#   2. 演示包已构建（务必带 -Pdemo，否则灌不进去演示数据与 Mock 网络）：
#        ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -Pdemo
#
# 用法：bash scripts/pull-screenshots.sh
#
# 说明：截图会同时覆盖到 docs/screenshots/（README 引用的位置），
# 提交与否由你自己决定；CI 上的等价流程见 .github/workflows/screenshots.yml。
set -euo pipefail

PKG="com.yunmei.vibe"
TEST_PKG="com.yunmei.vibe.test"
# 测试自己会把图复制到公共目录（/sdcard/Android/data 会随卸载被清掉），
# 所以这里统一从 Download 拉。
REMOTE_DIR="/sdcard/Download/yunmei-screenshots"
LOCAL_DIR="build/screenshots"

echo "==> 安装 debug 包与测试包"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

echo "==> 清掉上一轮的截图，避免新旧混在一起"
adb shell rm -rf "${REMOTE_DIR}"

echo "==> 运行截图测试（四种状态 × 三页）"
adb shell am instrument -w \
  -e class "${PKG}.ScreenshotTest" \
  "${TEST_PKG}/androidx.test.runner.AndroidJUnitRunner"

echo "==> 设备上的 PNG 清单（排查用）"
adb shell find /sdcard -name "*.png" -type f 2>/dev/null | head -40 || true

echo "==> 拉取到 ${LOCAL_DIR}"
rm -rf "${LOCAL_DIR}"
mkdir -p "${LOCAL_DIR}"
adb pull "${REMOTE_DIR}" "${LOCAL_DIR}"

echo "==> 同步到 docs/screenshots（README 引用目录）"
mkdir -p docs/screenshots
cp "${LOCAL_DIR}"/screenshots/*.png docs/screenshots/

echo "==> 完成，共 $(ls -1 "${LOCAL_DIR}/screenshots" 2>/dev/null | wc -l | tr -d ' ') 张"
ls -1 docs/screenshots/*.png
