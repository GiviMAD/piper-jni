set -xe

AARCH=${1:-$(uname -m)}
case "$AARCH" in
  x86_64|amd64)
    AARCH=x86_64
    AARCH_NAME=amd64
    TARGET_VERSION=14
    ;;
  arm64|aarch64)
    AARCH=arm64
    AARCH_NAME=arm64
    TARGET_VERSION=14
    ;;
  *)
    echo Unsupported arch $AARCH
    ;;

esac

TARGET=$AARCH-apple-macosx$TARGET_VERSION

TARGET_DIR="src/main/resources/macos-$AARCH"

cmake -Bbuild -DCMAKE_INSTALL_PREFIX="$TARGET_DIR" -DCMAKE_OSX_DEPLOYMENT_TARGET=$TARGET_VERSION -DCMAKE_OSX_ARCHITECTURES=$AARCH
cmake --build build --config Release
cmake --install build

find "$TARGET_DIR" -type l \( -name "*.dylib" -o -name "*.so" \) | while read -r link; do
    target_path=$(readlink "$link")
    link_dir=$(dirname "$link")
    target=$(cd "$link_dir" && cd "$(dirname "$target_path")" && echo "$(pwd -P)/$(basename "$target_path")")

    [ -f "$target" ] || continue
    rm -f "$link"
    mv "$target" "$link"
    rmdir "$(dirname "$target")" 2>/dev/null || true
done
