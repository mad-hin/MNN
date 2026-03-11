package com.alibaba.mnnllm.android.mainsettings

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import com.alibaba.mnnllm.android.R
import com.alibaba.mnnllm.android.modelsettings.DropDownLineView
import com.google.android.material.button.MaterialButton

class BackendSelectionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BackendSelectionActivity"
        private const val PREF_DEFAULT_BACKEND = "default_backend"

        init {
            System.loadLibrary("mnnllmapp")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backend_selection)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.backend_selection_title)

        setupBackendSelection()
        setupCudaTest()
    }

    private fun setupBackendSelection() {
        val tvCurrentBackend: TextView = findViewById(R.id.tv_current_backend)
        val dropdownBackend: DropDownLineView = findViewById(R.id.dropdown_default_backend)
        val rowAutoDetected: View = findViewById(R.id.row_auto_detected)
        val dividerAutoDetected: View = findViewById(R.id.divider_auto_detected)
        val tvAutoDetectedBackend: TextView = findViewById(R.id.tv_auto_detected_backend)

        val backendOptions = listOf("auto", "cpu", "opencl", "vulkan", "cuda")
        val currentBackend = getDefaultBackend()

        tvCurrentBackend.text = currentBackend
        Log.e(TAG, "Current default backend: $currentBackend")

        // If already set to auto, show which backend will be chosen
        if (currentBackend == "auto") {
            showAutoDetection(rowAutoDetected, dividerAutoDetected, tvAutoDetectedBackend)
        }

        dropdownBackend.setCurrentItem(currentBackend)
        dropdownBackend.setDropDownItems(
                backendOptions,
                itemToString = { it.toString() },
                onDropdownItemSelected = { _, item ->
                    val selected = item.toString()
                    setDefaultBackend(selected)
                    tvCurrentBackend.text = selected
                    Log.e(TAG, "Backend changed to: $selected")

                    if (selected == "auto") {
                        showAutoDetection(
                                rowAutoDetected,
                                dividerAutoDetected,
                                tvAutoDetectedBackend
                        )
                    } else {
                        rowAutoDetected.visibility = View.GONE
                        dividerAutoDetected.visibility = View.GONE
                    }

                    Toast.makeText(this, "Default backend set to: $selected", Toast.LENGTH_SHORT)
                            .show()
                }
        )
    }

    /** Reveals the "Auto will use: …" row and fills it on a background thread. */
    private fun showAutoDetection(row: View, divider: View, tvResult: TextView) {
        row.visibility = View.VISIBLE
        divider.visibility = View.VISIBLE
        tvResult.text = getString(R.string.detecting)
        tvResult.setTextColor(getColor(android.R.color.darker_gray))

        Thread {
                    try {
                        val detected = detectAutoBackendNative()
                        val display = backendDisplayName(detected)
                        runOnUiThread {
                            tvResult.text = display
                            Log.e(TAG, "Auto backend detection result: $detected ($display)")
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvResult.text = "Detection failed: ${e.message}"
                            Log.e(TAG, "Auto backend detection failed", e)
                        }
                    }
                }
                .start()
    }

    /** Human-readable label for each backend key. */
    private fun backendDisplayName(backend: String): String =
            when (backend) {
                "opencl" -> "OpenCL  (GPU — mobile GPU acceleration)"
                "vulkan" -> "Vulkan  (GPU — modern low-overhead API)"
                "cpu" -> "CPU  (no compatible GPU driver found)"
                else -> backend
            }

    private fun setupCudaTest() {
        val btnTestCuda: MaterialButton = findViewById(R.id.btn_test_cuda)
        val tvResult: TextView = findViewById(R.id.tv_cuda_test_result)

        btnTestCuda.setOnClickListener {
            tvResult.visibility = View.VISIBLE
            tvResult.text = getString(R.string.testing_cuda)
            tvResult.setTextColor(getColor(android.R.color.darker_gray))
            btnTestCuda.isEnabled = false

            Thread {
                        try {
                            val raw = testCudaBackendNative()
                            runOnUiThread {
                                btnTestCuda.isEnabled = true
                                displayCudaResult(tvResult, raw)
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                btnTestCuda.isEnabled = true
                                tvResult.text = "✗ Unexpected error: ${e.message}"
                                tvResult.setTextColor(getColor(android.R.color.holo_red_dark))
                                Log.e(TAG, "CUDA test: unexpected exception", e)
                            }
                        }
                    }
                    .start()
        }
    }

    /**
     * Native result uses a prefix convention: "OK:" → success (green) "WARN:" → expected
     * limitation, e.g. no NVIDIA GPU (orange) "ERR:" → unexpected failure (red)
     */
    private fun displayCudaResult(tvResult: TextView, raw: String) {
        val colonIdx = raw.indexOf(':')
        val prefix = if (colonIdx > 0) raw.substring(0, colonIdx) else ""
        val message = if (colonIdx > 0) raw.substring(colonIdx + 1).trim() else raw

        when (prefix) {
            "OK" -> {
                tvResult.text = "✓ $message"
                tvResult.setTextColor(getColor(android.R.color.holo_green_dark))
                Log.e(TAG, "CUDA test SUCCESS: $message")
            }
            "WARN" -> {
                tvResult.text = "⚠ $message"
                tvResult.setTextColor(getColor(android.R.color.holo_orange_dark))
                Log.e(TAG, "CUDA test NOT AVAILABLE: $message")
            }
            else -> {
                tvResult.text = "✗ $message"
                tvResult.setTextColor(getColor(android.R.color.holo_red_dark))
                Log.e(TAG, "CUDA test ERROR: $message")
            }
        }
    }

    private fun getDefaultBackend(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getString(PREF_DEFAULT_BACKEND, "auto") ?: "auto"
    }

    private fun setDefaultBackend(backend: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString(PREF_DEFAULT_BACKEND, backend).apply()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private external fun testCudaBackendNative(): String
    private external fun detectAutoBackendNative(): String
}
