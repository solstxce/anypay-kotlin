#!/bin/bash

# Keystore Generation Script for AnyPay
# This script helps you create a keystore for signing your Android app

set -e

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Android Keystore Generator${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Check if keytool is available
if ! command -v keytool &> /dev/null; then
    echo -e "${RED}Error: keytool not found. Please install JDK.${NC}"
    exit 1
fi

# Keystore configuration
KEYSTORE_FILE="anypay-release.jks"
KEY_ALIAS="anypay"
VALIDITY_DAYS=10000  # ~27 years

echo -e "${YELLOW}This will create a keystore file for signing your Android app.${NC}"
echo ""
echo -e "Keystore file: ${GREEN}$KEYSTORE_FILE${NC}"
echo -e "Key alias: ${GREEN}$KEY_ALIAS${NC}"
echo -e "Validity: ${GREEN}$VALIDITY_DAYS days (~27 years)${NC}"
echo ""

# Check if keystore already exists
if [[ -f "$KEYSTORE_FILE" ]]; then
    echo -e "${YELLOW}Warning: $KEYSTORE_FILE already exists!${NC}"
    read -p "Do you want to overwrite it? (yes/no): " OVERWRITE
    if [[ "$OVERWRITE" != "yes" ]]; then
        echo -e "${RED}Aborted.${NC}"
        exit 1
    fi
    rm "$KEYSTORE_FILE"
fi

echo -e "${YELLOW}You will be prompted for:${NC}"
echo "  1. Keystore password (remember this!)"
echo "  2. Key password (can be same as keystore password)"
echo "  3. Your name, organization, etc."
echo ""
echo -e "${GREEN}Generating keystore...${NC}"
echo ""

# Generate keystore
keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY_DAYS" \
    -storetype PKCS12

if [[ $? -ne 0 ]]; then
    echo -e "${RED}Failed to generate keystore${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✓ Keystore created successfully!${NC}"
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  Next Steps${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${YELLOW}1. Keep your keystore safe!${NC}"
echo "   IMPORTANT: Back up $KEYSTORE_FILE to a secure location."
echo "   If you lose this file, you cannot update your app on Google Play!"
echo ""
echo -e "${YELLOW}2. Never commit your keystore to Git!${NC}"
echo "   The .gitignore has been updated to exclude *.jks files."
echo ""
echo -e "${YELLOW}3. For GitHub Actions, you need to set these secrets:${NC}"
echo ""
echo -e "   ${GREEN}KEYSTORE_BASE64${NC}"
echo "   Run this command and copy the output:"
echo -e "   ${BLUE}base64 -w 0 $KEYSTORE_FILE${NC}"
echo ""
echo -e "   ${GREEN}KEYSTORE_PASSWORD${NC}"
echo "   The password you just entered for the keystore"
echo ""
echo -e "   ${GREEN}KEY_ALIAS${NC}"
echo -e "   ${BLUE}$KEY_ALIAS${NC}"
echo ""
echo -e "   ${GREEN}KEY_PASSWORD${NC}"
echo "   The key password you entered (might be same as keystore password)"
echo ""
echo -e "${YELLOW}4. To add GitHub secrets:${NC}"
echo "   - Go to your repository on GitHub"
echo "   - Settings → Secrets and variables → Actions"
echo "   - Click 'New repository secret' for each secret"
echo ""
echo -e "${YELLOW}5. For local builds, create a keystore.properties file:${NC}"
echo "   Run: ${BLUE}./setup_local_signing.sh${NC}"
echo ""
