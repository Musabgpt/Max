#!/bin/sh
# Source this file after installing Android Studio, JDK 17, and the SDK packages below.
# In this Codex workspace the toolchain is installed under /workspace/toolchains.
if [ -d /workspace/toolchains/jdk-17.0.17+10 ]; then
    export JAVA_HOME=/workspace/toolchains/jdk-17.0.17+10
fi
if [ -d /workspace/toolchains/android-sdk ]; then
    export ANDROID_HOME=/workspace/toolchains/android-sdk
fi
export ANDROID_SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
