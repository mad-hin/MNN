#include <jni.h>
#include <string>
#include "MNN/MNNForwardType.h"
#include "MNN/Interpreter.hpp"
#include "mls_log.h"

static bool isBackendAvailable(MNNForwardType type) {
    MNN::ScheduleConfig config;
    config.type = type;
    auto runtimeInfo = MNN::Interpreter::createRuntime({config});
    if (runtimeInfo.first.empty()) return false;
    auto it = runtimeInfo.first.find(type);
    return it != runtimeInfo.first.end() && it->second != nullptr;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_alibaba_mnnllm_android_mainsettings_BackendSelectionActivity_testCudaBackendNative(
        JNIEnv *env,
        jobject thiz) {
    MNN_ERROR("Testing CUDA backend availability...");

    MNN::ScheduleConfig config;
    config.type = MNN_FORWARD_CUDA;

    auto runtimeInfo = MNN::Interpreter::createRuntime({config});
    if (runtimeInfo.first.empty()) {
        const char *msg = "ERR:Runtime initialisation failed — no MNN backends could be created.\n\n"
                          "This usually means the MNN native library did not load correctly "
                          "or the build configuration is missing required backend support.";
        MNN_ERROR("CUDA test: runtime init failed (empty map)");
        return env->NewStringUTF(msg);
    }
    auto it = runtimeInfo.first.find(MNN_FORWARD_CUDA);
    if (it == runtimeInfo.first.end() || it->second == nullptr) {
        const char *msg = "WARN:CUDA is not available on this device.\n\n"
                          "Why: CUDA requires a dedicated NVIDIA GPU with CUDA drivers installed. "
                          "Android devices use mobile GPUs (Qualcomm Adreno, ARM Mali, etc.) "
                          "which do not support CUDA.\n\n"
                          "Recommendation: use OpenCL or Vulkan for GPU acceleration on Android.";
        MNN_ERROR("CUDA test: not available — no NVIDIA GPU on Android hardware");
        return env->NewStringUTF(msg);
    }
    const char *msg = "OK:CUDA backend is available and working on this device.";
    MNN_ERROR("CUDA test: SUCCESS — CUDA runtime created successfully");
    return env->NewStringUTF(msg);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_alibaba_mnnllm_android_mainsettings_BackendSelectionActivity_detectAutoBackendNative(
        JNIEnv *env,
        jobject thiz) {
    MNN_ERROR("Detecting auto-selected backend...");

    // Probe in priority order: OpenCL > Vulkan > CPU
    if (isBackendAvailable(MNN_FORWARD_OPENCL)) {
        MNN_ERROR("Auto backend detection: selected opencl");
        return env->NewStringUTF("opencl");
    }
    if (isBackendAvailable(MNN_FORWARD_VULKAN)) {
        MNN_ERROR("Auto backend detection: selected vulkan");
        return env->NewStringUTF("vulkan");
    }
    MNN_ERROR("Auto backend detection: falling back to cpu");
    return env->NewStringUTF("cpu");
}
