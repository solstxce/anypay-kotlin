#!/bin/bash

# Local Signing Setup Script
# Creates keystore.properties for local builds

set -e

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

KEYSTORE_FILE="anypay-release.jks"
KEYSTORE_PROPERTIES="keystore.properties"

echo -e "${GREEN}Setting up local signing configuration...${NC}"
echo ""

# Check if keystore exists
if [[ ! -f "$KEYSTORE_FILE" ]]; then
    echo -e "${RED}Error: $KEYSTORE_FILE not found!${NC}"
    echo "Please run ./generate_keystore.sh first to create a keystore."
    exit 1
fi

# Check if keystore.properties already exists
if [[ -f "$KEYSTORE_PROPERTIES" ]]; then
    echo -e "${YELLOW}Warning: $KEYSTORE_PROPERTIES already exists!${NC}"
    read -p "Do you want to overwrite it? (yes/no): " OVERWRITE
    if [[ "$OVERWRITE" != "yes" ]]; then
        echo -e "${RED}Aborted.${NC}"
        exit 1
    fi
fi

# Get absolute path to keystore
KEYSTORE_PATH="$(pwd)/$KEYSTORE_FILE"

# Prompt for passwords
echo "Please enter your keystore details:"
echo ""
read -sp "Keystore password: " STORE_PASSWORD
echo ""
read -p "Key alias [anypay]: " KEY_ALIAS
KEY_ALIAS=${KEY_ALIAS:-anypay}
read -sp "Key password: " KEY_PASSWORD
echo ""
echo ""

# Create keystore.properties
cat > "$KEYSTORE_PROPERTIES" << EOF
# Keystore configuration for local builds
# WARNING: Do not commit this file to version control!

storePassword=$STORE_PASSWORD
keyPassword=$KEY_PASSWORD
keyAlias=$KEY_ALIAS
storeFile=$KEYSTORE_PATH
EOF

echo -e "${GREEN}✓ Created $KEYSTORE_PROPERTIES${NC}"
echo ""
echo -e "${YELLOW}Important:${NC}"
echo "  - This file contains sensitive information"
echo "  - It is already in .gitignore to prevent accidental commits"
echo "  - You can now build release APKs locally with: ./gradlew assembleRelease"
echo ""
