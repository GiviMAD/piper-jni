set -xe

AARCH=${1:-$(uname -m)}
case "$AARCH" in
  x86_64|amd64)
    AARCH=x86_64
    AARCH_NAME=amd64
    TARGET_VERSION=11
    ;;
  arm64|aarch64)
    AARCH=arm64
    AARCH_NAME=arm64
    TARGET_VERSION=13
    ;;
  *)
    echo Unsupported arch $AARCH
    ;;
    
esac

TARGET=$AARCH-apple-macosx$TARGET_VERSION

cmake -Bbuild -DCMAKE_INSTALL_PREFIX=src/main/resources/macos-$AARCH_NAME -DCMAKE_OSX_DEPLOYMENT_TARGET=$TARGET_VERSION -DCMAKE_OSX_ARCHITECTURES=$AARCH
cmake --build build --config Release
cmake --install build