#!/usr/bin/env sh
set -e
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$ROOT_DIR/android"
if [ ! -x ./gradlew ]; then
  chmod +x ./gradlew 2>/dev/null || true
fi
exec ./gradlew "$@"
