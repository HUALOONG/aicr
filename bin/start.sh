#!/usr/bin/env bash
# 启动当前部署单元（后台运行，日志写入 logs/<app>.log）。
# 用法：[JAVA_OPTS="-Xms1g -Xmx2g"] ./bin/start.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "[$APP_NAME] 已在运行，pid=$(cat "$PID_FILE")，无需重复启动"
    exit 0
fi

cd "$APP_HOME"
# shellcheck disable=SC2086
nohup java $JAVA_OPTS -Dloader.path="$LOADER_PATH" -jar "$APP_JAR" >>"$LOG_FILE" 2>&1 &
echo $! >"$PID_FILE"

sleep 1
if kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "[$APP_NAME] 已启动，pid=$(cat "$PID_FILE")，日志：$LOG_FILE"
else
    echo "[$APP_NAME] 启动失败，请查看日志：$LOG_FILE" >&2
    rm -f "$PID_FILE"
    exit 1
fi
