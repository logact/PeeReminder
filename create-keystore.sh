#!/bin/bash

# Script to create a keystore for signing the PeeReminder release APK
# This keystore will be used to sign your release builds

set -e

KEYSTORE_NAME="peeReminder-release.jks"
KEYSTORE_PATH="app/${KEYSTORE_NAME}"
KEY_ALIAS="peeReminder"
VALIDITY_YEARS=25

echo "=========================================="
echo "Creating Keystore for PeeReminder Release"
echo "=========================================="
echo ""

# Check if keystore already exists
if [ -f "$KEYSTORE_PATH" ]; then
    echo "⚠️  Warning: Keystore file already exists at: $KEYSTORE_PATH"
    read -p "Do you want to overwrite it? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted. Keystore creation cancelled."
        exit 1
    fi
    rm "$KEYSTORE_PATH"
fi

echo "You will be prompted to enter:"
echo "  1. A password for the keystore (store password)"
echo "  2. The same password again to confirm"
echo "  3. Your name and organization details (for the certificate)"
echo "  4. The same password again (for the key password)"
echo ""
echo "IMPORTANT: Remember these passwords! You'll need them to sign release builds."
echo ""

# Create the keystore
keytool -genkey -v \
    -keystore "$KEYSTORE_PATH" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity $((VALIDITY_YEARS * 365)) \
    -storetype JKS

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Keystore created successfully!"
    echo "   Location: $KEYSTORE_PATH"
    echo "   Alias: $KEY_ALIAS"
    echo ""
    echo "Next steps:"
    echo "1. Copy keystore.properties.template to keystore.properties"
    echo "2. Edit keystore.properties and fill in:"
    echo "   - storeFile=$KEYSTORE_PATH"
    echo "   - storePassword=(the password you just entered)"
    echo "   - keyAlias=$KEY_ALIAS"
    echo "   - keyPassword=(the same password)"
    echo "3. Build your release APK using: ./gradlew assembleRelease"
    echo ""
    echo "⚠️  IMPORTANT: Keep your keystore file and passwords safe!"
    echo "   You'll need them for all future release builds."
else
    echo ""
    echo "❌ Failed to create keystore. Please check the error messages above."
    exit 1
fi
