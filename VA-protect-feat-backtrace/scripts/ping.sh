#!/bin/bash
source "${0%/*}/common.sh"

echo "$DEVICE_IP:$PORT (Raw TCP)..."


if echo "ping" | nc -w 3 "$DEVICE_IP" "$PORT"; then
    echo -e "\n pong!"
else
    echo -e "\n lose"
    echo "1. adb logcat | grep RemoteControl"
    echo "2. adb forward --list"
fi