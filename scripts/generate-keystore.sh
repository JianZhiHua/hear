#!/bin/bash
# Generate a fixed keystore for signing
# This should be run once and the keystore committed to the repo

KEYSTORE_DIR="app/signing"
KEYSTORE_FILE="$KEYSTORE_DIR/debug.keystore"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "Keystore already exists at $KEYSTORE_FILE"
    exit 0
fi

mkdir -p "$KEYSTORE_DIR"

keytool -genkey -v \
  -keystore "$KEYSTORE_FILE" \
  -alias androiddebugkey \
  -keyalg RSA \
  -keysize 2048 \
  -validity 36500 \
  -storepass android \
  -keypass android \
  -dname "CN=Hear,O=QingYi,C=CN"

echo "✅ Keystore generated: $KEYSTORE_FILE"
echo "⚠️  Commit this file to ensure consistent signing"
