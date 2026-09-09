#!/usr/bin/env bash
# 重启当前部署单元：先 stop 再 start。
set -uo pipefail
BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$BIN_DIR/stop.sh"
"$BIN_DIR/start.sh"
