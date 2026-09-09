#!/usr/bin/env bash
# 公共环境：定位应用主目录、启动 jar、pid 与日志路径、JVM 参数。
# 由 start.sh / stop.sh / status.sh 共同 source，不单独执行。
#
# 目录约定（CI/CD 规范 §4 / §7）：
#   <APP_HOME>/
#   ├── aicr-xxx.jar   瘦身 jar，仅含自身 class
#   ├── libs/          全部运行时依赖
#   ├── conf/          application.yml 等配置
#   └── bin/           本脚本所在目录

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 自动探测部署目录下唯一的启动 jar（aicr-web / aicr-webhook / aicr-worker）
APP_JAR="$(find "$APP_HOME" -maxdepth 1 -name '*.jar' -type f | sort | head -n 1)"
if [ -z "$APP_JAR" ]; then
    echo "[env] 错误：$APP_HOME 下未找到启动 jar" >&2
    exit 1
fi
APP_NAME="$(basename "$APP_JAR" .jar)"

RUN_DIR="$APP_HOME/run"
LOG_DIR="$APP_HOME/logs"
PID_FILE="$RUN_DIR/$APP_NAME.pid"
LOG_FILE="$LOG_DIR/$APP_NAME.log"

# 瘦身 jar 必须把外置依赖与配置挂到 classpath，否则启动失败（CI/CD 规范 §7）
LOADER_PATH="${LOADER_PATH:-libs,conf}"

# 可用环境变量覆盖：JAVA_OPTS="-Xms1g -Xmx2g" ./bin/start.sh
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m}"
# 优雅停止等待秒数，超时后 kill -9
STOP_TIMEOUT="${STOP_TIMEOUT:-30}"

mkdir -p "$RUN_DIR" "$LOG_DIR"
