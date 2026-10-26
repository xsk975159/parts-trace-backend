#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v 7z >/dev/null || { echo '请先安装7zip'; exit 1; }
mkdir -p build
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w' -o build/trace_anchor .
cd build
7z a -t7z trace_anchor.7z trace_anchor
