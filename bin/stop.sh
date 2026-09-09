#!/usr/bin/env bash
# 停止当前部署单元：先 TERM 优雅退出，超时后 kill -9。
# 用法：[STOP_TIMEOUT=60] ./bin/stop.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

if [ ! -f "$PID_FILE" ]; then
    echo "[$APP_NAME] 未运行（无 pid 文件）"
    exit 0
fi

PID="$(cat "$PID_FILE")"
if ! kill -0 "$PID" 2>/dev/null; then
    echo "[$APP_NAME] 进程 $PID 已不存在，清理残留 pid 文件"
    rm -f "$PID_FILE"
    exit 0
fi

echo "[$APP_NAME] 正在停止 pid=$PID（最长等待 ${STOP_TIMEOUT}s）..."
kill -TERM "$PID"
for ((i = 0; i < STOP_TIMEOUT; i++)); do
    if ! kill -0 "$PID" 2>/dev/null; then
        break
    fi
    sleep 1
done

if kill -0 "$PID" 2>/dev/null; then
    echo "[$APP_NAME] ${STOP_TIMEOUT}s 内未退出，强制 kill -9"
    kill -9 "$PID"
    sleep 1
fi

rm -f "$PID_FILE"
echo "[$APP_NAME] 已停止"
