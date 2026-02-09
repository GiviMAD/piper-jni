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

build_lib() {
    cmake -Bbuild -DCMAKE_INSTALL_PREFIX=src/main/resources/debian-$AARCH
    cmake --build build --config Release -j$(nproc)
    cmake --install build
}

build_lib
