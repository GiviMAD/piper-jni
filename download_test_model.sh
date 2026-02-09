#!/bin/bash
# This script downloads the models required for the PiperJNITest as documented in the README.
set -e

TEST_DATA_DIR="test-data"
MODEL_NAME="es_ES-sharvard-medium.onnx"
CONFIG_NAME="es_ES-sharvard-medium.onnx.json"
BASE_URL="https://huggingface.co/rhasspy/piper-voices/resolve/main/es/es_ES/sharvard/medium"

mkdir -p "$TEST_DATA_DIR"

echo "Downloading voice model..."
curl -L "$BASE_URL/$MODEL_NAME" -o "$TEST_DATA_DIR/$MODEL_NAME"

echo "Downloading voice configuration..."
curl -L "$BASE_URL/$CONFIG_NAME" -o "$TEST_DATA_DIR/$CONFIG_NAME"

echo "Done. Models are in $TEST_DATA_DIR"
