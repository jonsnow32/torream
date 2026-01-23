#!/bin/zsh

# Script to build release APKs for all product flavors
# Created: January 21, 2026

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Project root directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  ZippyPlayer - Build All Release Flavors${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# Product flavors to build
FLAVORS=("arm64" "arm32" "x86" "x86_64" "universal")

# Clean build directory first
echo -e "${YELLOW}Cleaning previous builds...${NC}"
./gradlew clean
echo -e "${GREEN}✓ Clean completed${NC}"
echo ""

# Build each flavor
BUILD_SUCCESS=()
BUILD_FAILED=()

for flavor in "${FLAVORS[@]}"; do
    echo -e "${BLUE}================================================${NC}"
    echo -e "${BLUE}Building ${flavor}Release...${NC}"
    echo -e "${BLUE}================================================${NC}"

    # Capitalize first letter (universally compatible)
    flavorCapitalized=$(echo "$flavor" | awk '{print toupper(substr($0,1,1)) tolower(substr($0,2))}')

    if ./gradlew "assemble${flavorCapitalized}Release"; then
        echo -e "${GREEN}✓ ${flavor}Release build succeeded${NC}"
        BUILD_SUCCESS+=("$flavor")
    else
        echo -e "${RED}✗ ${flavor}Release build failed${NC}"
        BUILD_FAILED+=("$flavor")
    fi
    echo ""
done

# Summary
echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  Build Summary${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

if [ ${#BUILD_SUCCESS[@]} -gt 0 ]; then
    echo -e "${GREEN}Successful builds (${#BUILD_SUCCESS[@]}):${NC}"
    for flavor in "${BUILD_SUCCESS[@]}"; do
        echo -e "  ${GREEN}✓${NC} ${flavor}"
    done
    echo ""
fi

if [ ${#BUILD_FAILED[@]} -gt 0 ]; then
    echo -e "${RED}Failed builds (${#BUILD_FAILED[@]}):${NC}"
    for flavor in "${BUILD_FAILED[@]}"; do
        echo -e "  ${RED}✗${NC} ${flavor}"
    done
    echo ""
fi

# Output directory
OUTPUT_DIR="$PROJECT_DIR/app/build/outputs/apk"
if [ -d "$OUTPUT_DIR" ]; then
    echo -e "${BLUE}================================================${NC}"
    echo -e "${BLUE}  APK Locations${NC}"
    echo -e "${BLUE}================================================${NC}"
    echo ""

    for flavor in "${BUILD_SUCCESS[@]}"; do
        APK_DIR="$OUTPUT_DIR/$flavor/release"
        if [ -d "$APK_DIR" ]; then
            echo -e "${YELLOW}${flavor}:${NC}"
            find "$APK_DIR" -name "*.apk" -exec echo "  {}" \;

            # Show file size
            for apk in "$APK_DIR"/*.apk; do
                if [ -f "$apk" ]; then
                    SIZE=$(du -h "$apk" | cut -f1)
                    echo -e "  Size: ${GREEN}$SIZE${NC}"
                fi
            done
            echo ""
        fi
    done
fi

# Exit with error if any builds failed
if [ ${#BUILD_FAILED[@]} -gt 0 ]; then
    echo -e "${RED}Some builds failed. Please check the logs above.${NC}"
    exit 1
else
    echo -e "${GREEN}All builds completed successfully!${NC}"
    exit 0
fi
