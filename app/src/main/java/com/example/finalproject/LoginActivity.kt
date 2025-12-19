package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.finalproject.databinding.ActivityLoginBinding
import com.example.finalproject.data.UserSession
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

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener {
            attemptLogin()
        }

        binding.signupPrompt.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptLogin() {
        val username = binding.usernameEditText.text?.toString()?.trim().orEmpty()
        val password = binding.passwordEditText.text?.toString()?.trim().orEmpty()

        if (username.isBlank() || password.isBlank()) {
            showError(getString(R.string.login_error_empty))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = runCatching { performLogin(username, password) }
            setLoading(false)

            result.onSuccess { response ->
                binding.loginError.visibility = View.GONE
                val parsed = runCatching { JSONObject(response) }.getOrNull()
                val userId = parsed?.optString("id").orEmpty()
                val userNameFromResponse = parsed?.optString("name").orEmpty()

                if (userId.isNotBlank()) {
                    UserSession.saveUserId(this@LoginActivity, userId)
                }
                val nameToStore = userNameFromResponse.ifBlank { username }
                UserSession.saveUsername(this@LoginActivity, nameToStore)

                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.login_success),
                    Toast.LENGTH_SHORT
                ).show()
                startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
            }.onFailure { error ->
                showError(error.message ?: getString(R.string.login_error_generic))
            }
        }
    }

    private fun showError(message: String) {
        binding.loginError.text = message
        binding.loginError.visibility = View.VISIBLE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loginProgress.isVisible = isLoading
        binding.loginButton.isEnabled = !isLoading
        binding.usernameInputLayout.isEnabled = !isLoading
        binding.passwordInputLayout.isEnabled = !isLoading
    }

    private suspend fun performLogin(username: String, password: String): String = withContext(Dispatchers.IO) {
        val url = URL(LOGIN_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val payload = JSONObject().apply {
            put("name", username)
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
                throw IOException(response.ifBlank { getString(R.string.login_error_generic) })
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
        private const val LOGIN_URL = "http://10.0.2.2:3000/Users/login"
    }
}
