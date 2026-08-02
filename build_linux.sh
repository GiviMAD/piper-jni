#!/bin/bash
set -xe

# Use TARGETARCH if provided (e.g. from Docker)
# Otherwise, try to detect it using uname -m
AARCH=${TARGETARCH:-$(uname -m)}

case $AARCH in
    x86_64|amd64)
        AARCH="amd64" ;;
    aarch64|arm64)
        AARCH="arm64" ;;
    arm|armhf|armv7l)
        AARCH="armv7l" ;;
    *)
        echo "Unsupported architecture: ${AARCH}"
        exit 1 ;;
esac

TARGET_DIR="src/main/resources/debian-$AARCH"

cmake -Bbuild -DCMAKE_INSTALL_PREFIX="$TARGET_DIR"
cmake --build build --config Release -j$(nproc)
cmake --install build

find "$TARGET_DIR" -type l \( -name "*.so" -o -name "*.so.*" \) | while read -r link; do
    target=$(readlink -f "$link")
    [ -f "$target" ] || continue
    rm -f "$link"
    mv "$target" "$link"
    rmdir "$(dirname "$target")" 2>/dev/null || true
done
