#!/usr/bin/env bash
# 查看当前部署单元运行状态：运行中返回 0，已停止返回 1。
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "[$APP_NAME] RUNNING pid=$(cat "$PID_FILE")"
    exit 0
fi

echo "[$APP_NAME] STOPPED"
exit 1
