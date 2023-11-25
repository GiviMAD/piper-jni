#!/bin/bash
set -xe
build_lib() {
    cmake -Bbuild -DCMAKE_INSTALL_PREFIX=src/main/resources/debian-$AARCH
    cmake --build build --config Release
    cmake --install build
}
AARCH=$(dpkg --print-architecture)
case $AARCH in
  amd64)
    build_lib
    ;;
  arm64)
    build_lib
    ;;
  armhf|armv7l)
    AARCH=armv7l
    build_lib
    ;;
esac
