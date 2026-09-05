package com.learnsypro.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.branding.LearnsyLogo
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.theme.rememberShakeOffset
import kotlinx.coroutines.launch

/**
 * Màn hình "cổng mật khẩu" hiện trước khi vào app chính.
 * Tương đương #pw-overlay trong index.html gốc (background blur gradient
 * hồng/tím, đổi màu theo dark mode).
 *
 * @param isDarkTheme dùng để chọn gradient nền tương ứng body.dark #pw-overlay
 * @param onPasswordCorrect callback khi mật khẩu đúng, dùng để chuyển màn hình chính
 * @param checkPassword hàm kiểm tra mật khẩu (nên gọi Supabase Auth hoặc API riêng)
 */
@Composable
fun PasswordGateScreen(
    isDarkTheme: Boolean,
    checkPassword: suspend (String) -> Boolean,
    onPasswordCorrect: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val shakeOffset = rememberShakeOffset(trigger = isError)
    val scope = rememberCoroutineScope()

    // Gradient nền: tương đương linear-gradient trong CSS #pw-overlay / body.dark #pw-overlay
    val backgroundBrush = if (isDarkTheme) {
        Brush.linearGradient(
            colors = listOf(Color(0xF7180A10), Color(0xF71E0D21))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xF5FFF5F9), Color(0xF5F0E6FF))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .background(
                    color = if (isDarkTheme) Color(0xFF1E0845) else Color.White,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LearnsyLogo(size = 84.dp, fontSize = 26.sp)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Nhập mật khẩu để tiếp tục",
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                color = if (isDarkTheme) Color(0xFFF5E9FF) else Color(0xFF2A1233)
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    isError = false
                },
                label = { Text("Mật khẩu", fontFamily = NunitoFontFamily) },
                singleLine = true,
                isError = isError,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = shakeOffset.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF472B6),
                    cursorColor = Color(0xFFF472B6)
                )
            )

            AnimatedVisibility(visible = isError) {
                Text(
                    text = "Mật khẩu không đúng, thử lại nhé!",
                    color = Color(0xFFEF4444),
                    fontFamily = NunitoFontFamily,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (password.isBlank()) return@Button
                    isLoading = true
                    scope.launch {
                        val correct = checkPassword(password)
                        isLoading = false
                        if (correct) {
                            onPasswordCorrect()
                        } else {
                            isError = true
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF472B6))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Vào Learnsy",
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
