#!/bin/bash

# 自动检测活动的网络接口,优先使用非lo接口
INTERFACE=$(ip -o link show | awk -F': ' '$2 !~ /^lo$/ && $3 ~ /UP/ {print $2; exit}')
PORT=25000

# 如果自动检测失败，手动指定
if [ -z "$INTERFACE" ]; then
    INTERFACE=eth0  # 修改为实际接口名：eth0, wlan0, enp0s3 等
fi

# 自动设置ADB端口转发,适用于WSL2等虚拟网络环境
echo "设置ADB端口转发..."
adb forward tcp:$PORT tcp:$PORT 2>/dev/null
if [ $? -eq 0 ]; then
    echo "ADB端口转发已设置: tcp:$PORT -> tcp:$PORT"
else
    echo "ADB端口转发设置失败，请检查设备连接"
fi

# 配置为设备IP,使用ADB转发时用127.0.0.1
DEVICE_IP=${DEVICE_IP:-"127.0.0.1"}  # 通过ADB端口转发连接

while getopts "p:" OPT; do
  case ${OPT} in
    p )
      PORT="$OPTARG"
      ;;
    \? )
      echo "invalid option." 1>&2
      exit 1
      ;;
    : )
      echo "option requires an argument." 1>&2
      exit 1
      ;;
  esac
done
shift $((OPTIND -1))

echo "PORT=$PORT"

IPADDR=$(ip -o -4 addr show dev "$INTERFACE" | sed -nE 's/.*inet ([0-9.]+).*/\1/p')
ROUTE=$(ip -o -4 route show dev "$INTERFACE" | awk '/default/ {print $3}')

# 如果没有设置DEVICE_IP，尝试使用ROUTE作为后备
if [ -z "$DEVICE_IP" ]; then
    DEVICE_IP="$ROUTE"
    echo "⚠️  Warning: DEVICE_IP not set, using gateway IP: $DEVICE_IP"
    echo "   If connection fails, set your Android device IP:"
    echo "   export DEVICE_IP=<your_device_ip>"
fi

echo "IPADDR=$IPADDR (Your PC IP)"
echo "ROUTE=$ROUTE (Gateway IP)"
echo "DEVICE_IP=$DEVICE_IP (Android Device IP)"
