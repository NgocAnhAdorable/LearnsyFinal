package com.learnsypro.app.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.theme.rememberShakeOffset
import com.learnsypro.app.ui.theme.rememberPopState
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.dashboard.DashboardIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ── PwGateModal ──
 * Tương đương window.PwGate trong pw-gate.jsx — modal nhập mật khẩu để mở
 * MỘT BÀI HỌC bị khóa, khác với PasswordGateScreen (khóa toàn app).
 */
@Composable
fun PwGateModal(
    lessonTitle: String,
    lessonPassword: String,
    dark: Boolean,
    onUnlock: () -> Unit,
    onCancel: () -> Unit
) {
    var pw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    var pwVisible by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shakeOffset = rememberShakeOffset(trigger = err)
    val (popScale, popAlpha) = rememberPopState()

    fun tryUnlock() {
        if (unlocking) return
        if (pw == lessonPassword) {
            unlocking = true
            onUnlock()
        } else {
            err = true
            scope.launch {
                delay(600)
                err = false
            }
        }
    }

    val titleColor = if (dark) Color(0xFFF0DCE8) else Color(0xFF3D1830)
    val subColor = if (dark) Color(0xFF8A6080) else Color(0xFFA07090)
    val cardBorder = if (dark) Color(0x33FF96C8) else Color(0xFFF5D5E8)
    val cardBg = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF1E0845), Color(0xFF120330)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFFFF5F9), Color(0xFFF0E6FF)))
    }
    val fieldContainer = if (dark) Color(0x14FFFFFF) else Color(0xFFFAF5FF)
    val fieldBorder = if (dark) Color(0x33FFFFFF) else Color(0xFFE8DCFF)
    val fieldTextColor = if (dark) Color(0xFFF0DCE8) else Color(0xFF3D1830)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60A0219))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 300.dp)
                .graphicsLayer { scaleX = popScale; scaleY = popScale; alpha = popAlpha }
                .background(cardBg, RoundedCornerShape(28.dp))
                .border(1.5.dp, cardBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DashboardIcon(name = "shieldLock", size = 32.dp, color = titleColor)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = lessonTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = titleColor,
                textAlign = TextAlign.Center,
                fontFamily = NunitoFontFamily
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nhập mật khẩu để mở bài này",
                fontSize = 12.sp,
                color = subColor,
                fontFamily = NunitoFontFamily
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pw,
                onValueChange = { pw = it },
                placeholder = { Text("Mật khẩu...", textAlign = TextAlign.Center, fontFamily = NunitoFontFamily, color = subColor) },
                singleLine = true,
                visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { pwVisible = !pwVisible }) {
                        Icon(
                            imageVector = if (pwVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = subColor
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { tryUnlock() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = shakeOffset.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (err) Color(0xFFEF4444) else fieldBorder,
                    unfocusedBorderColor = if (err) Color(0xFFEF4444) else fieldBorder,
                    focusedContainerColor = fieldContainer,
                    unfocusedContainerColor = fieldContainer,
                    focusedTextColor = fieldTextColor,
                    unfocusedTextColor = fieldTextColor,
                    cursorColor = Color(0xFFF472B6)
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7))), RoundedCornerShape(50))
                    .clickable { tryUnlock() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Mở bài", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
                    DashboardIcon(name = "sparkle", size = 14.dp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, cardBorder, RoundedCornerShape(50))
                    .clickable(onClick = onCancel)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Quay lại", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = subColor, fontFamily = NunitoFontFamily)
            }
        }
    }
}
