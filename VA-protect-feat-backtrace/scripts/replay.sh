#!/bin/bash
source "${0%/*}/common.sh"

echo "正在重置回传通道..."
# 清理旧规则
adb reverse --remove tcp:12345 2>/dev/null
# 建立新的反向代理
adb reverse tcp:12345 tcp:12345

echo "当前 ADB 端口映射列表:"
adb reverse --list

echo "准备接收音频..."

# --- 后台发送指令 ---
(
    # 等待 ncat 启动
    sleep 3
    echo ">> 后台任务: 发送非流式回放指令 (SampleRate: 16000)..." >&2
    # 使用 nc 发送命令
    echo "{ \"ServerAddress\": \"127.0.0.1\", \"ServerPort\": 12345, \"Duration\": 15000, \"Stream\": false, \"SampleRate\": 16000 }" | nc -w 3 "$DEVICE_IP" "$PORT"
) &
# ------------------

echo "正在监听端口 12345..."
# 创建临时文件
TMP=$(mktemp)

# 启动监听并保存到临时文件
ncat -v -l 12345 > "$TMP"

echo "录音完成，正在播放..."

# 播放录音
ffplay -f s16le -ar 16000 -ac 1 -autoexit "$TMP"

# 清理
rm "$TMP"
adb reverse --remove tcp:12345

echo "脚本已停止。"
