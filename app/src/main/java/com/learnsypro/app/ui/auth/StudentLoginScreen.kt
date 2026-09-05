package com.learnsypro.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.theme.rememberShakeOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LoginResult(val ok: Boolean, val studentId: String? = null, val message: String? = null)

/**
 * ── StudentLoginScreen ──
 * Tương đương window.StudentLoginScreen trong student-login.jsx (bản UI
 * nâng cấp: glassmorphism, theme Indigo/Violet có dark/light, logo "Learnsy",
 * dấu chấm bay lượn trang trí nền).
 */
@Composable
fun StudentLoginScreen(
    dark: Boolean,
    onLoginSuccess: (username: String, studentId: String?) -> Unit,
    onCheckLogin: suspend (username: String, password: String) -> LoginResult
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var shakeError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val shakeOffset = rememberShakeOffset(trigger = shakeError)
    val usernameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        usernameFocusRequester.requestFocus()
    }

    val canSubmit = !loading && username.trim().isNotEmpty() && password.isNotEmpty()

    fun doLogin() {
        if (loading) return
        if (username.trim().isEmpty() || password.isEmpty()) {
            errorMsg = "Nhập đầy đủ username và mật khẩu nhé!"
            shakeError = true
            scope.launch { delay(400); shakeError = false }
            return
        }
        loading = true
        errorMsg = null
        scope.launch {
            val result = onCheckLogin(username.trim(), password)
            loading = false
            if (result.ok) {
                onLoginSuccess(username.trim(), result.studentId)
            } else {
                errorMsg = result.message ?: "Đăng nhập thất bại!"
                shakeError = true
                password = ""
                delay(400)
                shakeError = false
            }
        }
    }

    val primary = Color(0xFF6366F1)
    val bgBrush = if (dark) {
        Brush.radialGradient(
            colors = listOf(Color(0xFF1A1F3A), Color(0xFF0F172A), Color(0xFF0F172A)),
            center = Offset(0.2f, 0.2f)
        )
    } else {
        Brush.radialGradient(
            colors = listOf(Color(0xFFEDEBFF), Color(0xFFF8FAFC), Color(0xFFF8FAFC)),
            center = Offset(0.2f, 0.2f)
        )
    }
    val cardBg = if (dark) Color(0xB31E293B) else Color(0xBFFFFFFF)
    val cardBorder = if (dark) Color(0x14FFFFFF) else Color(0xE6FFFFFF)
    val tMain = if (dark) Color(0xFFF1F5F9) else Color(0xFF1E293B)
    val tSub = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val inBg = if (dark) Color(0x990F172A) else Color(0xCCF1F5F9)
    val inBorder = if (dark) Color(0x14FFFFFF) else Color(0x266366F1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        FloatingLoginDots(dark = dark)

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.98f)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val floatYState = rememberLoginFloat()
                    Box(modifier = Modifier.graphicsLayer { translationY = floatYState.value }) {
                        DashboardIcon(name = "graduationCap", size = 26.dp, color = primary)
                    }
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "Learnsy Pro",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = primary,
                            fontFamily = NunitoFontFamily
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (dark) Color(0x266366F1) else Color(0x146366F1))
                        .border(1.5.dp, if (dark) Color(0x4D6366F1) else Color(0x336366F1), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DashboardIcon(name = "graduationCapFilled", size = 12.dp, color = primary)
                    Text(text = "Khu vực học sinh", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = primary, fontFamily = NunitoFontFamily)
                }

                Spacer(modifier = Modifier.height(28.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(cardBg)
                        .border(1.5.dp, cardBorder, RoundedCornerShape(28.dp))
                        .padding(horizontal = 28.dp, vertical = 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(60.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (dark) SolidColor(Color(0x266366F1))
                                else Brush.linearGradient(listOf(Color(0xFFE0E7FF), Color(0xFFF3E8FF)))
                            )
                            .border(1.5.dp, if (dark) Color(0x336366F1) else Color(0x266366F1), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        DashboardIcon(name = "graduationCap", size = 26.dp, color = primary)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Chào mừng trở lại!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = tMain,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        fontFamily = NunitoFontFamily
                    )
                    Text(
                        text = "Đăng nhập để bắt đầu luyện tập",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tSub,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        fontFamily = NunitoFontFamily
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, if (dark) Color(0x1AFFFFFF) else Color(0x336366F1), Color.Transparent)
                                )
                            )
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "TÊN ĐĂNG NHẬP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = tSub,
                        letterSpacing = 0.5.sp,
                        fontFamily = NunitoFontFamily,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.replace(" ", ""); errorMsg = null },
                        placeholder = { Text("Nhập username của bạn", fontFamily = NunitoFontFamily, fontSize = 14.sp) },
                        leadingIcon = { DashboardIcon(name = "userCircle", size = 16.dp, color = tSub) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(usernameFocusRequester),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (errorMsg != null) Color(0xFFEF4444) else primary,
                            unfocusedBorderColor = if (errorMsg != null) Color(0xFFEF4444) else inBorder,
                            focusedContainerColor = inBg,
                            unfocusedContainerColor = inBg,
                            focusedTextColor = tMain,
                            unfocusedTextColor = tMain
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "MẬT KHẨU",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = tSub,
                        letterSpacing = 0.5.sp,
                        fontFamily = NunitoFontFamily,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMsg = null },
                        placeholder = { Text("••••••••", fontFamily = NunitoFontFamily, fontSize = 14.sp) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Box(modifier = Modifier.clickable { passwordVisible = !passwordVisible }.padding(8.dp)) {
                                DashboardIcon(name = if (passwordVisible) "eyeOff" else "eye", size = 18.dp, color = tSub)
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { doLogin() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(x = shakeOffset.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (errorMsg != null) Color(0xFFEF4444) else primary,
                            unfocusedBorderColor = if (errorMsg != null) Color(0xFFEF4444) else inBorder,
                            focusedContainerColor = inBg,
                            unfocusedContainerColor = inBg,
                            focusedTextColor = tMain,
                            unfocusedTextColor = tMain
                        )
                    )

                    AnimatedVisibility(visible = errorMsg != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x14EF4444))
                                .border(1.dp, Color(0x33EF4444), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DashboardIcon(name = "alertCircle", size = 14.dp, color = Color(0xFFEF4444))
                            Text(
                                text = errorMsg ?: "",
                                color = Color(0xFFEF4444),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NunitoFontFamily
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val loginInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val loginPressed by loginInteraction.collectIsPressedAsState()
                    val loginScale by androidx.compose.animation.core.animateFloatAsState(
                        if (loginPressed && canSubmit) 0.96f else 1f, com.learnsypro.app.ui.theme.OneUiSpring.bouncy, label = "loginBtnScale"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .graphicsLayer { scaleX = loginScale; scaleY = loginScale }
                            .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.pill))
                            .background(
                                if (canSubmit) Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
                                else Brush.linearGradient(listOf(Color(0x336366F1), Color(0x336366F1)))
                            )
                            .clickable(enabled = canSubmit, interactionSource = loginInteraction, indication = null) { doLogin() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (loading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Text(text = "Đang xử lý...", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Đăng nhập",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (canSubmit) Color.White else Color(0x80FFFFFF),
                                    fontFamily = NunitoFontFamily
                                )
                                DashboardIcon(name = "chevronRight", size = 15.dp, color = if (canSubmit) Color.White else Color(0x80FFFFFF))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DashboardIcon(name = "shieldLock", size = 12.dp, color = tSub)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tài khoản do giáo viên cấp phát",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tSub,
                            fontFamily = NunitoFontFamily
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Learnsy © 2026 · Nền tảng học tập trực tuyến",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = tSub.copy(alpha = 0.6f),
                    fontFamily = NunitoFontFamily
                )
            }
        }
    }
}

@Composable
private fun rememberLoginFloat(): androidx.compose.runtime.State<Float> {
    if (com.learnsypro.app.ui.theme.LocalLiteMode.current) {
        return remember { mutableStateOf(0f) }
    }
    val transition = rememberInfiniteTransition(label = "loginFloat")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(4000, easing = EaseInOut), repeatMode = RepeatMode.Reverse),
        label = "loginFloatY"
    )
}

@Composable
private fun FloatingLoginDots(dark: Boolean) {
    // Lite Mode: 6 chấm bay lượn là thuần trang trí nền, bỏ animate hoàn
    // toàn tránh 6 InfiniteTransition chạy song song trên máy yếu.
    if (com.learnsypro.app.ui.theme.LocalLiteMode.current) return

    val dotColor = if (dark) Color(0x26A855F7) else Color(0x266366F1)
    Box(modifier = Modifier.fillMaxSize()) {
        repeat(6) { i ->
            val transition = rememberInfiniteTransition(label = "dot$i")
            val y by transition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    tween(3000 + i * 1000, easing = EaseInOut, delayMillis = i * 500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotY$i"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (30 + i * 50).dp, y = (100 + i * 90).dp)
                    .graphicsLayer { translationY = y }
                    .size((10 + i * 4).dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
