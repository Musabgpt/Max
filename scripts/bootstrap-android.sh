#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PROJECT_DIR"
if [ ! -f app/src/main/cpp/llama.cpp/CMakeLists.txt ]; then
    git submodule update --init --recursive
fi
SDK_MANAGER="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDK_MANAGER" ]; then
    echo 'Install Android Studio or Android command-line tools, and set ANDROID_HOME.' >&2
    exit 1
fi
"$SDK_MANAGER" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools' 'ndk;27.2.12479018' 'cmake;3.22.1'
./gradlew --no-daemon testDebugUnitTest assembleDebug
