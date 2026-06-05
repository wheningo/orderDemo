#!/bin/bash
# Generate Go stubs from proto
# Requires: protoc, protoc-gen-go, protoc-gen-go-grpc
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$SCRIPT_DIR/../../gateway/proto"
mkdir -p "$OUT_DIR"
protoc --proto_path="$SCRIPT_DIR" \
  --go_out="$OUT_DIR" --go_opt=paths=source_relative \
  --go-grpc_out="$OUT_DIR" --go-grpc_opt=paths=source_relative \
  "$SCRIPT_DIR/hotrank.proto"
echo "Go stubs generated in $OUT_DIR"