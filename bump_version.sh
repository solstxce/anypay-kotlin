#!/bin/bash

# Version Bump Script for AnyPay
# Usage: ./bump_version.sh [major|minor|patch]
# Examples:
#   ./bump_version.sh major  -> increases by 1.0.0
#   ./bump_version.sh minor  -> increases by 0.1.0
#   ./bump_version.sh patch  -> increases by 0.0.1

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default to patch if no argument provided
BUMP_TYPE="${1:-patch}"

# Validate bump type
if [[ ! "$BUMP_TYPE" =~ ^(major|minor|patch)$ ]]; then
    echo -e "${RED}Error: Invalid bump type. Use 'major', 'minor', or 'patch'${NC}"
    echo "Usage: $0 [major|minor|patch]"
    exit 1
fi

# File paths
GRADLE_PROPERTIES="gradle.properties"
BUILD_GRADLE="app/build.gradle.kts"

# Check if files exist
if [[ ! -f "$GRADLE_PROPERTIES" ]]; then
    echo -e "${RED}Error: $GRADLE_PROPERTIES not found${NC}"
    exit 1
fi

if [[ ! -f "$BUILD_GRADLE" ]]; then
    echo -e "${RED}Error: $BUILD_GRADLE not found${NC}"
    exit 1
fi

# Read current version from gradle.properties
if grep -q "VERSION_NAME=" "$GRADLE_PROPERTIES"; then
    CURRENT_VERSION=$(grep "VERSION_NAME=" "$GRADLE_PROPERTIES" | cut -d'=' -f2 | tr -d '[:space:]')
else
    echo -e "${YELLOW}VERSION_NAME not found in gradle.properties, using default 1.0.0${NC}"
    CURRENT_VERSION="1.0.0"
fi

# Read current version code
if grep -q "VERSION_CODE=" "$GRADLE_PROPERTIES"; then
    CURRENT_VERSION_CODE=$(grep "VERSION_CODE=" "$GRADLE_PROPERTIES" | cut -d'=' -f2 | tr -d '[:space:]')
else
    echo -e "${YELLOW}VERSION_CODE not found in gradle.properties, using default 1${NC}"
    CURRENT_VERSION_CODE="1"
fi

# Parse version numbers
IFS='.' read -ra VERSION_PARTS <<< "$CURRENT_VERSION"
MAJOR="${VERSION_PARTS[0]:-0}"
MINOR="${VERSION_PARTS[1]:-0}"
PATCH="${VERSION_PARTS[2]:-0}"

# Bump version based on type
case "$BUMP_TYPE" in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
esac

NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

echo -e "${GREEN}Bumping version:${NC}"
echo "  Type: $BUMP_TYPE"
echo "  From: $CURRENT_VERSION (code: $CURRENT_VERSION_CODE)"
echo "  To:   $NEW_VERSION (code: $NEW_VERSION_CODE)"
echo ""

# Update gradle.properties
if grep -q "VERSION_NAME=" "$GRADLE_PROPERTIES"; then
    # Update existing VERSION_NAME
    sed -i "s/VERSION_NAME=.*/VERSION_NAME=$NEW_VERSION/" "$GRADLE_PROPERTIES"
else
    # Add VERSION_NAME
    echo "VERSION_NAME=$NEW_VERSION" >> "$GRADLE_PROPERTIES"
fi

if grep -q "VERSION_CODE=" "$GRADLE_PROPERTIES"; then
    # Update existing VERSION_CODE
    sed -i "s/VERSION_CODE=.*/VERSION_CODE=$NEW_VERSION_CODE/" "$GRADLE_PROPERTIES"
else
    # Add VERSION_CODE
    echo "VERSION_CODE=$NEW_VERSION_CODE" >> "$GRADLE_PROPERTIES"
fi

echo -e "${GREEN}✓ Updated $GRADLE_PROPERTIES${NC}"

# Update build.gradle.kts if it has hardcoded values
if grep -q 'versionCode = [0-9]' "$BUILD_GRADLE"; then
    sed -i "s/versionCode = [0-9]*/versionCode = findProperty(\"VERSION_CODE\")?.toString()?.toInt() ?: $NEW_VERSION_CODE/" "$BUILD_GRADLE"
    echo -e "${GREEN}✓ Updated versionCode in $BUILD_GRADLE${NC}"
fi

if grep -q 'versionName = "[^"]*"' "$BUILD_GRADLE"; then
    sed -i "s/versionName = \"[^\"]*\"/versionName = findProperty(\"VERSION_NAME\")?.toString() ?: \"$NEW_VERSION\"/" "$BUILD_GRADLE"
    echo -e "${GREEN}✓ Updated versionName in $BUILD_GRADLE${NC}"
fi

echo ""
echo -e "${GREEN}Version bump complete!${NC}"
echo -e "${YELLOW}Don't forget to commit these changes:${NC}"
echo "  git add $GRADLE_PROPERTIES $BUILD_GRADLE"
echo "  git commit -m \"Bump version to $NEW_VERSION\""
echo "  git tag v$NEW_VERSION"
echo "  git push && git push --tags"
