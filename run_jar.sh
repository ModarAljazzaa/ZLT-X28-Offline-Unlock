#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java -jar "$SCRIPT_DIR/dist/ZLT-X28-Unlock.jar"
