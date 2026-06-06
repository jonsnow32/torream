#!/bin/zsh

# Script to verify APK ABI filtering is working correctly
# This checks that each flavor APK contains only its own architecture

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

echo "================================================"
echo "  APK ABI Content Verification"
echo "================================================"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

APK_DIR="app/build/outputs/apk"

# Check if APKs exist
if [ ! -d "$APK_DIR" ]; then
    echo -e "${RED}Error: APK directory not found at $APK_DIR${NC}"
    echo "Please run: ./build-all-releases.sh"
    exit 1
fi

# Function to check APK ABI content
check_apk() {
    local flavor=$1
    local expected_abi=$2
    local apk_path="$APK_DIR/$flavor/release/app-${flavor}-release.apk"

    if [ ! -f "$apk_path" ]; then
        echo -e "${RED}✗ $flavor APK not found${NC}"
        return 1
    fi

    # Get file size
    local size=$(du -h "$apk_path" | cut -f1)

    # Count native libraries by architecture
    local arm64_count=$(unzip -l "$apk_path" 2>/dev/null | grep -c "arm64-v8a/.*\.so$" || echo 0)
    local arm32_count=$(unzip -l "$apk_path" 2>/dev/null | grep -c "armeabi-v7a/.*\.so$" || echo 0)
    local x86_count=$(unzip -l "$apk_path" 2>/dev/null | grep -c "x86/.*\.so$" || echo 0)
    local x86_64_count=$(unzip -l "$apk_path" 2>/dev/null | grep -c "x86_64/.*\.so$" || echo 0)

    echo "================================================"
    echo "Flavor: $flavor | Size: $size"
    echo "================================================"

    if [ "$expected_abi" = "arm64-v8a" ]; then
        if [ $arm64_count -gt 0 ] && [ $arm32_count -eq 0 ] && [ $x86_count -eq 0 ] && [ $x86_64_count -eq 0 ]; then
            echo -e "${GREEN}✓ Correctly contains ONLY arm64-v8a libraries ($arm64_count .so files)${NC}"
            return 0
        else
            echo -e "${RED}✗ Incorrectly contains multiple architectures:${NC}"
            echo "  arm64-v8a: $arm64_count"
            echo "  armeabi-v7a: $arm32_count"
            echo "  x86: $x86_count"
            echo "  x86_64: $x86_64_count"
            return 1
        fi
    elif [ "$expected_abi" = "armeabi-v7a" ]; then
        if [ $arm32_count -gt 0 ] && [ $arm64_count -eq 0 ] && [ $x86_count -eq 0 ] && [ $x86_64_count -eq 0 ]; then
            echo -e "${GREEN}✓ Correctly contains ONLY armeabi-v7a libraries ($arm32_count .so files)${NC}"
            return 0
        else
            echo -e "${RED}✗ Incorrectly contains multiple architectures:${NC}"
            echo "  arm64-v8a: $arm64_count"
            echo "  armeabi-v7a: $arm32_count"
            echo "  x86: $x86_count"
            echo "  x86_64: $x86_64_count"
            return 1
        fi
    elif [ "$expected_abi" = "x86" ]; then
        if [ $x86_count -gt 0 ] && [ $arm64_count -eq 0 ] && [ $arm32_count -eq 0 ] && [ $x86_64_count -eq 0 ]; then
            echo -e "${GREEN}✓ Correctly contains ONLY x86 libraries ($x86_count .so files)${NC}"
            return 0
        else
            echo -e "${RED}✗ Incorrectly contains multiple architectures:${NC}"
            echo "  arm64-v8a: $arm64_count"
            echo "  armeabi-v7a: $arm32_count"
            echo "  x86: $x86_count"
            echo "  x86_64: $x86_64_count"
            return 1
        fi
    elif [ "$expected_abi" = "x86_64" ]; then
        if [ $x86_64_count -gt 0 ] && [ $arm64_count -eq 0 ] && [ $arm32_count -eq 0 ] && [ $x86_count -eq 0 ]; then
            echo -e "${GREEN}✓ Correctly contains ONLY x86_64 libraries ($x86_64_count .so files)${NC}"
            return 0
        else
            echo -e "${RED}✗ Incorrectly contains multiple architectures:${NC}"
            echo "  arm64-v8a: $arm64_count"
            echo "  armeabi-v7a: $arm32_count"
            echo "  x86: $x86_count"
            echo "  x86_64: $x86_64_count"
            return 1
        fi
    elif [ "$expected_abi" = "universal" ]; then
        if [ $arm64_count -gt 0 ] && [ $arm32_count -gt 0 ] && [ $x86_count -gt 0 ] && [ $x86_64_count -gt 0 ]; then
            echo -e "${GREEN}✓ Correctly contains ALL architectures:${NC}"
            echo "  arm64-v8a: $arm64_count .so files"
            echo "  armeabi-v7a: $arm32_count .so files"
            echo "  x86: $x86_count .so files"
            echo "  x86_64: $x86_64_count .so files"
            return 0
        else
            echo -e "${RED}✗ Missing some architectures:${NC}"
            echo "  arm64-v8a: $arm64_count"
            echo "  armeabi-v7a: $arm32_count"
            echo "  x86: $x86_count"
            echo "  x86_64: $x86_64_count"
            return 1
        fi
    fi
}

# Check each flavor
all_passed=0
check_apk "arm64" "arm64-v8a" && ((all_passed++))
echo ""
check_apk "arm32" "armeabi-v7a" && ((all_passed++))
echo ""
check_apk "x86" "x86" && ((all_passed++))
echo ""
check_apk "x86_64" "x86_64" && ((all_passed++))
echo ""
check_apk "universal" "universal" && ((all_passed++))

echo ""
echo "================================================"
echo "  Verification Summary"
echo "================================================"
if [ $all_passed -eq 5 ]; then
    echo -e "${GREEN}✓ All APKs are correctly filtered by ABI!${NC}"
    echo -e "${GREEN}✓ Fix is working correctly!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some APKs have incorrect ABI filtering${NC}"
    echo -e "${RED}✗ Issue persists. Please check build.gradle.kts${NC}"
    exit 1
fi
