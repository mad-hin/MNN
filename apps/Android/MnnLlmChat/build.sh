#!/bin/bash
set -e

# Set ANDROID_HOME if not already set
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [ -n "$ANDROID_SDK_ROOT" ]; then
        export ANDROID_HOME="$ANDROID_SDK_ROOT"
    fi
fi

# Set ANDROID_NDK if not already set
if [ -z "$ANDROID_NDK" ]; then
    if [ -d "$ANDROID_HOME/ndk" ]; then
        ANDROID_NDK=$(ls -d "$ANDROID_HOME/ndk/"*/ 2>/dev/null | sort -V | tail -1)
        ANDROID_NDK=${ANDROID_NDK%/}
        export ANDROID_NDK
    fi
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

cd "$SCRIPT_DIR/../../../project/android"
mkdir -p build_64
cd build_64
../build_64.sh "\
-DMNN_LOW_MEMORY=true \
-DMNN_CPU_WEIGHT_DEQUANT_GEMM=true \
-DMNN_BUILD_LLM=true \
-DMNN_SUPPORT_TRANSFORMER_FUSE=true \
-DMNN_ARM82=true \
-DMNN_USE_LOGCAT=true \
-DMNN_OPENCL=true \
-DLLM_SUPPORT_VISION=true \
-DMNN_BUILD_OPENCV=true \
-DMNN_IMGCODECS=true \
-DLLM_SUPPORT_AUDIO=true \
-DMNN_BUILD_AUDIO=true \
-DMNN_BUILD_DIFFUSION=ON \
-DMNN_SEP_BUILD=OFF \
-DBUILD_PLUGIN=ON \
-DMNN_QNN=OFF \
-DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' \
-DCMAKE_INSTALL_PREFIX=."
make install
cd "$SCRIPT_DIR"
./gradlew assembleStandardDebug
