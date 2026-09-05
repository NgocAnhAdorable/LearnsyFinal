package com.learnsypro.app.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.admin.data.SupabaseConfig
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val shake: Boolean = false
)

// Tương đương LoginScreen trong learnsy-login.jsx — dùng Supabase Auth
// thay vì mật khẩu tĩnh của bản admin.html cũ.
class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Tương đương message map trong doLogin()
    private val errorMessages = mapOf(
        "Invalid login credentials" to "Email hoặc mật khẩu không đúng 🔐",
        "Email not confirmed" to "Email chưa được xác nhận!",
        "Too many requests" to "Thử quá nhiều lần, đợi chút nhé ⏳"
    )

    fun setEmail(v: String) = _uiState.update { it.copy(email = v, error = null) }
    fun setPassword(v: String) = _uiState.update { it.copy(password = v, error = null) }
    fun toggleShowPassword() = _uiState.update { it.copy(showPassword = !it.showPassword) }

    fun canSubmit(): Boolean {
        val s = _uiState.value
        return !s.loading && s.email.trim().isNotEmpty() && s.password.isNotEmpty()
    }

    fun login(onSuccess: () -> Unit) {
        val s = _uiState.value
        if (s.loading) return
        if (s.email.trim().isEmpty() || s.password.isEmpty()) {
            triggerShake("Vui lòng nhập đầy đủ email và mật khẩu!")
            return
        }

        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                SupabaseConfig.client.auth.signInWith(Email) {
                    email = s.email.trim().lowercase()
                    password = s.password
                }
                _uiState.update { it.copy(loading = false) }
                onSuccess()
            } catch (e: Exception) {
                val msg = errorMessages[e.message] ?: e.message ?: "Lỗi kết nối, thử lại nhé!"
                _uiState.update { it.copy(loading = false, password = "") }
                triggerShake(msg)
            }
        }
    }

    private fun triggerShake(message: String) {
        _uiState.update { it.copy(error = message, shake = true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            _uiState.update { it.copy(shake = false) }
        }
    }
}
