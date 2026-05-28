#!/bin/bash
# 一键 AndroidGo 真机测试脚本
# 用法: ./test_on_device.sh [--logs] [--test]
#   --logs  启动后实时跟踪 AndroidGo 日志
#   --test  运行单元测试（不需要手机）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
ADB="$HOME/Android/Sdk/platform-tools/adb"

red()  { echo -e "\033[31m$*\033[0m"; }
green(){ echo -e "\033[32m$*\033[0m"; }
cyan() { echo -e "\033[36m$*\033[0m"; }

# --- 单元测试（不需要手机） ---
run_unit_tests() {
    cyan ">>> 运行 GoGame 单元测试..."
    cd "$SCRIPT_DIR"
    ./gradlew test 2>&1 | tail -15
    echo
    green "测试报告: app/build/reports/tests/testDebugUnitTest/index.html"
}

# --- 设备测试 ---
run_device_test() {
    # 1. 构建
    cyan ">>> 构建 APK..."
    cd "$SCRIPT_DIR"
    ./gradlew assembleDebug 2>&1 | grep -E "BUILD|FAILED|ERROR|Task.*app"
    echo

    # 2. 检查设备
    if ! command -v "$ADB" &>/dev/null; then
        red "adb 未找到: $ADB"
        exit 1
    fi

    DEVICE=$("$ADB" devices 2>/dev/null | tail -n +2 | head -1 | awk '{print $1}')
    if [ -z "$DEVICE" ]; then
        red "没有连接的设备，尝试无线连接..."
        red "先在手机上开启无线调试，然后: adb connect <IP>:<PORT>"
        exit 1
    fi
    green ">>> 设备: $DEVICE"

    # 3. 安装
    cyan ">>> 安装 APK..."
    "$ADB" install -r "$APK" 2>&1
    echo

    # 4. 启动并清日志
    cyan ">>> 启动应用..."
    "$ADB" shell am force-stop org.snailtrail.androidgo
    "$ADB" logcat -c
    "$ADB" shell am start -n org.snailtrail.androidgo/.MainActivity
    sleep 3

    green ">>> 应用已启动，日志 tag=AndroidGo"
    echo
    echo "  接下来的操作用 adb 命令行完成，例如:"
    echo "  # 点击新局按钮"
    echo "  adb shell input tap 916 1919"
    echo ""
    echo "  # 实时查看日志"
    echo "  adb logcat -s AndroidGo:D"
    echo ""
    echo "  # 实时过滤关键日志"
    echo "  adb logcat -s AndroidGo:D | grep -E 'trigger|init|engine'"
    echo ""

    if [[ "${1:-}" == "--logs" ]]; then
        cyan ">>> 开始跟踪日志 (Ctrl-C 停止)..."
        "$ADB" logcat -s AndroidGo:D
    fi
}

# --- main ---
case "${1:-}" in
    --test)  run_unit_tests ;;
    --logs)  run_device_test --logs ;;
    *)       run_device_test ;;
esac
