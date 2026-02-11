#!/bin/bash
source "${0%/*}/common.sh"

echo "正在重置流式回传通道..."
# 先清理旧规则
adb reverse --remove tcp:23456 2>/dev/null
# 建立新的反向代理
adb reverse tcp:23456 tcp:23456

echo "当前 ADB 端口映射列表:"
adb reverse --list

echo "准备接收音频流..."
echo "脚本启动后，请立即对着手机大声说话！"

# --- 后台发送指令 ---
(
    #确保 ncat 先完全启动
    sleep 3
    echo ">> 后台任务: 发送流式回放指令..." >&2
    echo "{ \"ServerAddress\": \"127.0.0.1\", \"ServerPort\": 23456, \"Duration\": 15000, \"Stream\": true, \"SampleRate\": 16000 }" | nc -w 3 "$DEVICE_IP" "$PORT"
) &
# ------------------

echo "正在监听端口 23456..."
# 启动监听和播放
# 如果看到 "Ncat: Connection from 127.0.0.1" 才说明连接成功
ncat -v -l 23456 | ffplay -f s16le -ar 16000 -ac 1 -fflags nobuffer -flags low_delay -autoexit -

# adb reverse --remove tcp:23456
echo "脚本已停止。"