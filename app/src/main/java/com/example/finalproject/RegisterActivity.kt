package com.example.finalproject

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.finalproject.databinding.ActivityRegisterBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.registerButton.setOnClickListener {
            attemptRegister()
        }

        binding.loginPrompt.setOnClickListener {
            finish()
        }
    }

    private fun attemptRegister() {
        val name = binding.nameEditText.text?.toString()?.trim().orEmpty()
        val password = binding.registerPasswordEditText.text?.toString()?.trim().orEmpty()

        if (name.isBlank() || password.isBlank()) {
            showError(getString(R.string.register_error_empty))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = runCatching { performRegister(name, password) }
            setLoading(false)

            result.onSuccess { message ->
                binding.registerError.visibility = View.GONE
                val successMessage = message.ifBlank { getString(R.string.register_success) }
                Toast.makeText(this@RegisterActivity, successMessage, Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure { error ->
                showError(error.message ?: getString(R.string.register_error_generic))
            }
        }
    }

    private fun showError(message: String) {
        binding.registerError.text = message
        binding.registerError.visibility = View.VISIBLE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.registerProgress.isVisible = isLoading
        binding.registerButton.isEnabled = !isLoading
        binding.nameInputLayout.isEnabled = !isLoading
        binding.registerPasswordInputLayout.isEnabled = !isLoading
    }

    private suspend fun performRegister(name: String, password: String): String = withContext(Dispatchers.IO) {
        val url = URL(REGISTER_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val payload = JSONObject().apply {
            put("name", name)
            put("password", password)
        }

        try {
            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.let { readStream(it) }.orEmpty()

            if (responseCode !in 200..299) {
                throw IOException(response.ifBlank { getString(R.string.register_error_generic) })
            }

            response
        } finally {
            connection.disconnect()
        }
    }

    private fun readStream(stream: java.io.InputStream): String {
        val builder = StringBuilder()
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
        }
        return builder.toString()
    }

    companion object {
        private const val REGISTER_URL = "http://10.0.2.2:3000/Users/register"
    }
}
