#!/bin/bash
set -e

# Ensure we are in the project root
cd "$(dirname "$0")"

echo "Building native libraries for all supported platforms using Docker..."

# Initialize submodules if not already done
if [ ! -f "src/main/native/piper/VERSION" ]; then
    echo "Initializing submodules..."
    git submodule update --init --recursive
fi

# Build for Linux amd64, arm64, and armv7l
# The --output flag extracts the built libraries from the 'export' stage
# and places them into src/main/resources/
docker buildx build \
    --platform linux/amd64,linux/arm64,linux/arm/v7 \
    --target export \
    --output "type=local,dest=src/main/resources/temp_build" \
    .

mv src/main/resources/temp_build/linux_amd64/*.zip src/main/resources/
mv src/main/resources/temp_build/linux_amd64/* src/main/resources/debian-amd64/
mv src/main/resources/temp_build/linux_arm64/* src/main/resources/debian-arm64/
mv src/main/resources/temp_build/linux_arm_v7/* src/main/resources/debian-armv7l/

rm -rf src/main/resources/temp_build

echo "Build complete. Binaries are located in src/main/resources/"
ls src/main/resources
ls -R src/main/resources/debian-*
