#!/bin/bash

# 检查语音助手权限的脚本

PACKAGE_NAME="com.miui.voiceassist"

echo "=========================================="
echo "检查 $PACKAGE_NAME 的权限"
echo "=========================================="
echo ""

echo "方法 1: 使用 dumpsys 查看请求的权限"
echo "=========================================="
adb shell dumpsys package $PACKAGE_NAME | grep -A 100 "requested permissions:" | head -n 100
echo ""

echo "方法 2: 查看已授予的权限"
echo "=========================================="
adb shell dumpsys package $PACKAGE_NAME | grep "granted=true"
echo ""

echo "方法 3: 使用 pm list permissions"
echo "=========================================="
adb shell pm list permissions -g -d
echo ""

echo "方法 4: 获取 APK 路径"
echo "=========================================="
APK_PATH=$(adb shell pm path $PACKAGE_NAME | cut -d':' -f2 | tr -d '\r')
echo "APK 路径: $APK_PATH"
echo ""

echo "方法 5: 拉取 APK 并使用 aapt 分析"
echo "=========================================="
if [ ! -z "$APK_PATH" ]; then
    echo "正在拉取 APK..."
    adb pull "$APK_PATH" ./voiceassist.apk 2>/dev/null
    
    if [ -f "./voiceassist.apk" ]; then
        echo "使用 aapt 分析权限..."
        
        # 尝试使用 aapt
        if command -v aapt &> /dev/null; then
            aapt dump permissions ./voiceassist.apk
        else
            echo "aapt 未安装，尝试使用 aapt2..."
            if command -v aapt2 &> /dev/null; then
                aapt2 dump permissions ./voiceassist.apk
            else
                echo "未找到 aapt/aapt2，使用 unzip + strings 方式..."
                unzip -p ./voiceassist.apk AndroidManifest.xml | strings | grep -i "permission" | sort | uniq
            fi
        fi
        
        echo ""
        echo "APK 已保存到: ./voiceassist.apk"
    fi
fi

echo ""
echo "=========================================="
echo "检查完成"
echo "=========================================="
