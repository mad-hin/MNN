# MnnLlmChat — How the Android App Uses the MNN Framework Backend

## Overview

The MnnLlmChat Android app uses the MNN inference engine via JNI. Backend selection (CPU, OpenCL, Vulkan, etc.) is configured at the Java/Kotlin layer and passed down through JNI to native C++ code, which ultimately calls MNN APIs with the selected `MNNForwardType` .

## Backend Selection Flow

```
User UI  →  Kotlin Config  →  JSON Serialization  →  JNI  →  C++ MNN Engine
```

### 1. UI Layer — Backend Dropdown

**LLM models** — [ `SettingsBottomSheetFragment.kt` ](app/src/main/java/com/alibaba/mnnllm/android/modelsettings/SettingsBottomSheetFragment.kt) (line ~140):

```kotlin
val backendOptions = listOf("cpu", "opencl")   // ← HARDCODED to only cpu/opencl
val currentBackend = currentConfig.backendType.takeIf { it in backendOptions } ?: "cpu"
```

* Default: `"cpu"`
* Only two options: `"cpu"` and `"opencl"`

**Diffusion/Sana models** — [ `DiffusionSettingsBottomSheetFragment.kt` ](app/src/main/java/com/alibaba/mnnllm/android/modelsettings/DiffusionSettingsBottomSheetFragment.kt) (line ~141):

```kotlin
val backendOptions = listOf("cpu", "opencl")   // ← HARDCODED to only cpu/opencl
val currentBackend = currentConfig.backendType.takeIf { it in backendOptions } ?: "opencl"
```

* Default: `"opencl"`
* Only two options: `"cpu"` and `"opencl"`

### 2. Configuration Data Class

[ `ModelConfig.kt` ](app/src/main/java/com/alibaba/mnnllm/android/modelsettings/ModelConfig.kt):

```kotlin
data class ModelConfig(
    @SerializedName("backend_type") var backendType: String?,
    // ... other fields
)
```

* `backendType` is stored as a string (`"cpu"`,  `"opencl"`, etc.)
* The `defaultConfig` companion object sets `backendType = ""` (empty string)

### 3. Session Layer — Backend Override

**LLM Session** — [ `LlmSession.kt` ](app/src/main/java/com/alibaba/mnnllm/android/llm/LlmSession.kt):

```kotlin
class LlmSession(
    ...
    var backendType: String? = null   // Can override config's backend
) {
    override fun load() {
        val llmConfig = ModelConfig.loadMergedConfig(configPath, getExtraConfigFile(modelId))!!
        if (backendType != null) {
            llmConfig.backendType = backendType   // Override if provided
        }
        nativePtr = initNative(configPath, ..., Gson().toJson(llmConfig), ...)
    }
}
```

**Sana/Diffusion Session** — [ `SanaSession.kt` ](app/src/main/java/com/alibaba/mnnllm/android/llm/SanaSession.kt):

```kotlin
val configMap = HashMap<String, Any>().apply {
    put("backend_type", config?.backendType ?: "opencl")   // ← HARDCODED default to "opencl"
}
nativePtr = initNative(configPath, Gson().toJson(configMap))
```

### 4. JNI / Native Layer

**LLM JNI** — [ `llm_mnn_jni.cpp` ](app/src/main/cpp/llm_mnn_jni.cpp):
* Receives the merged config JSON string
* Passes it to `LlmSession` constructor which calls `llm_->set_config(config_str)` internally
* The MNN LLM engine reads `backend_type` from the config JSON

**Sana JNI** — [ `sana_jni.cpp` ](app/src/main/cpp/sana_jni.cpp) (line ~29):

```cpp
int backend_type = MNN_FORWARD_OPENCL;   // ← HARDCODED default to OpenCL
if (config.contains("backend_type")) {
    std::string backend_str = config["backend_type"];
    if (backend_str == "cpu") {
        backend_type = MNN_FORWARD_CPU;
    } else if (backend_str == "opencl") {
        backend_type = MNN_FORWARD_OPENCL;
    } else if (backend_str == "vulkan") {
        backend_type = MNN_FORWARD_VULKAN;
    }
    // Note: "auto", "cuda" are NOT handled — they fall through to the OPENCL default
}
```

**Sana Session** — [ `sana_session.cpp` ](app/src/main/cpp/sana_session.cpp):

```cpp
diffusion_.reset(Diffusion::createDiffusion(
    resource_path_,
    (DiffusionModelType)2,
    (MNNForwardType)backend_type_,   // Uses the mapped integer
    memory_mode_
));
```

### 5. Benchmark

[ `BenchmarkModel.kt` ](app/src/main/java/com/alibaba/mnnllm/android/benchmark/BenchmarkModel.kt):

```kotlin
val backendId = if (backendType.equals("opencl", ignoreCase = true)) 3 else 0
// Only maps opencl→3, everything else→0 (CPU)
```

### 6. Build & Manifest

**CMakeLists.txt** — [ `app/src/main/cpp/CMakeLists.txt` ](app/src/main/cpp/CMakeLists.txt):
* Links only `libMNN.so` (single unified library)
* Backend-specific `.so` files (`libMNN_CL.so`,  `libMNN_Express.so`) are commented out

**AndroidManifest.xml** — [ `app/src/main/AndroidManifest.xml` ](app/src/main/AndroidManifest.xml):

```xml
<uses-native-library android:name="libOpenCL.so" android:required="false" />
<uses-library android:name="libcdsprpc.so" android:required="false"/>
```

**strings.xml** — [ `res/values/strings.xml` ](app/src/main/res/values/strings.xml):

```xml
<string name="backend">use opencl</string>   <!-- Label implies OpenCL only -->
```

## Summary of Hardcoded OpenCL References

| File | What's Hardcoded |
|------|-----------------|
| `SettingsBottomSheetFragment.kt` | Backend options limited to `listOf("cpu", "opencl")` |
| `DiffusionSettingsBottomSheetFragment.kt` | Backend options limited to `listOf("cpu", "opencl")` , default `"opencl"` |
| `SanaSession.kt` | Default backend `"opencl"` when config is null |
| `sana_jni.cpp` | Default `MNN_FORWARD_OPENCL` , no handling for `"auto"` or `"cuda"` |
| `BenchmarkModel.kt` | Only maps `"opencl"` → 3, everything else → 0 |
| `strings.xml` | `backend` string says "use opencl" |

## MNN Forward Type Constants

From `include/MNN/MNNForwardType.h` :

| Constant | Value | Description |
|----------|-------|-------------|
| `MNN_FORWARD_CPU` | 0 | CPU backend |
| `MNN_FORWARD_CUDA` | 2 | CUDA (NVIDIA GPU) |
| `MNN_FORWARD_OPENCL` | 3 | OpenCL (mobile GPU) |
| `MNN_FORWARD_AUTO` | 4 | Auto-select best available |
| `MNN_FORWARD_VULKAN` | 7 | Vulkan |
| `MNN_FORWARD_CPU_EXTENSION` | 13 | CPU with extensions |
