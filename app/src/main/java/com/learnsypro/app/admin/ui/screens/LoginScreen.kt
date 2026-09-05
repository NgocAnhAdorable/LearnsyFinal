package com.learnsypro.app.admin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.learnsypro.app.admin.ui.LoginViewModel

@Composable
fun LoginScreen(
    dark: Boolean,
    onAuth: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val emailFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        emailFocusRequester.requestFocus()
    }

    val tMain = if (dark) Color(0xFFF1F5F9) else Color(0xFF1E293B)
    val tSub = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val cardBg = if (dark) Color(0xFF1E293B).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.75f)
    val borderColor = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.9f)
    val lockBgDark = Brush.linearGradient(listOf(Color(0xFF6366F1).copy(alpha = 0.12f), Color(0xFF6366F1).copy(alpha = 0.12f)))
    val inBg = if (dark) Color(0xFF0F172A).copy(alpha = 0.6f) else Color(0xFFF1F5F9).copy(alpha = 0.8f)
    val inBgFocus = if (dark) Color(0xFF0F172A).copy(alpha = 0.8f) else Color.White

    val bgColor = if (dark) Color(0xFF0A0F1E) else Color(0xFFF8FAFC)

    val infiniteTransition = rememberInfiniteTransition(label = "login-dots")

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(state.shake) {
        if (state.shake) {
            scope.launch {
                listOf(-10f, 10f, -8f, 8f, -4f, 4f, 0f).forEach {
                    shakeOffset.animateTo(it, tween(50))
                }
            }
        }
    }

    val fieldColors = @Composable { hasError: Boolean ->
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (hasError) Color(0xFFEF4444) else Color(0xFF6366F1),
            unfocusedBorderColor = if (hasError) Color(0xFFEF4444) else borderColor,
            focusedContainerColor = inBgFocus,
            unfocusedContainerColor = inBg,
            cursorColor = Color(0xFF6366F1)
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // FIX: trước đây .background(Brush.radialGradient(..., bgColor)) tô
            // ĐẶC hoàn toàn màn hình (điểm cuối gradient là màu nền phẳng, không
            // alpha) — khi AppRoot đặt BackgroundLayer (nền tuỳ chỉnh dùng chung
            // 3 module) làm lớp dưới cùng, LoginScreen luôn vẽ đè kín lên trên,
            // nền custom không bao giờ thấy được ở màn Login. Đổi điểm cuối
            // gradient sang bgColor.copy(alpha=0) để chỉ còn vệt sáng glow ở
            // giữa màn, phần còn lại trong suốt cho BackgroundLayer xuyên qua.
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = if (dark) listOf(Color(0xFF312E81).copy(alpha = 0.22f), bgColor.copy(alpha = 0f))
                    else listOf(Color(0xFFC7D2FE).copy(alpha = 0.6f), bgColor.copy(alpha = 0f)),
                    radius = 1400f
                )
            )
            .statusBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        val screenH = maxHeight
        val screenW = maxWidth
        val density = androidx.compose.ui.platform.LocalDensity.current
        repeat(6) { i ->
            // FIX: "offset by ..." rồi dùng trong Modifier.offset(x = Dp, y = Dp)
            // (bản nhận Dp trực tiếp, KHÔNG phải bản lambda) — đọc giá trị Dp đó
            // xảy ra ở composition/layout phase, ép cả BoxWithConstraints cha
            // (chứa luôn form đăng nhập bên dưới) recompose 60 lần/giây suốt thời
            // gian màn Login hiển thị. Tách phần tĩnh (vị trí cơ bản theo % màn
            // hình, không đổi) khỏi phần động (nhấp nhô nhẹ) — phần động đọc
            // .value trong graphicsLayer{} (draw phase) để không ép recompose.
            val offsetState = infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000 + i * 1000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot-$i"
            )
            val wobblePx = with(density) { 8.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = screenW * (0.10f + i * 0.15f),
                        y = screenH * (0.15f + i * 0.12f)
                    )
                    .graphicsLayer { translationY = -(offsetState.value * wobblePx) }
                    .size((10 + i * 4).dp)
                    .clip(CircleShape)
                    .background(
                        if (dark) Color(0xFF8B5CF6).copy(alpha = 0.15f) else Color(0xFF6366F1).copy(alpha = 0.15f)
                    )
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.MenuBook, null,
                    tint = Color(0xFF8B5CF6), // gradient indigo→violet giản lược thành 1 màu trung gian
                    modifier = Modifier.size(22.dp)
                )
                Text("Learnsy", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF6366F1))
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF6366F1).copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (dark) Color(0xFF6366F1).copy(alpha = 0.12f) else Color(0xFFE0E7FF))
                    .border(
                        1.5.dp,
                        if (dark) Color(0xFF6366F1).copy(alpha = 0.20f) else Color(0xFFC7D2FE),
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFF6366F1), modifier = Modifier.size(10.dp))
                    Text("Khu vực Admin", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF6366F1))
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = shakeOffset.value.dp)
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = if (dark) Color.Black.copy(alpha = 0.6f) else Color(0xFF6366F1).copy(alpha = 0.25f),
                        spotColor = if (dark) Color.Black.copy(alpha = 0.6f) else Color(0xFF6366F1).copy(alpha = 0.25f)
                    ),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .shadow(
                            elevation = 10.dp, shape = RoundedCornerShape(17.dp),
                            ambientColor = Color(0xFF6366F1).copy(alpha = 0.3f),
                            spotColor = Color(0xFF6366F1).copy(alpha = 0.3f)
                        )
                        .size(54.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            if (dark) lockBgDark else Brush.linearGradient(listOf(Color(0xFFE0E7FF), Color(0xFFEDE9FE)))
                        )
                        .border(1.5.dp, borderColor, RoundedCornerShape(17.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFF6366F1))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Xin chào, giáo viên! 👋",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = tMain
                )
                Text(
                    "Nhập thông tin để vào trang quản trị",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    fontSize = 12.sp,
                    color = tSub
                )

                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, borderColor, Color.Transparent)
                            )
                        )
                )
                Spacer(Modifier.height(20.dp))

                Text("EMAIL", fontSize = 10.sp, fontWeight = FontWeight.Black, color = tSub, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(5.dp))
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::setEmail,
                    placeholder = { Text("admin@truong.edu.vn") },
                    isError = state.error != null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(state.error != null),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tMain),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(emailFocusRequester)
                )

                Spacer(Modifier.height(12.dp))

                Text("MẬT KHẨU", fontSize = 10.sp, fontWeight = FontWeight.Black, color = tSub, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(5.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::setPassword,
                    placeholder = { Text("••••••••") },
                    isError = state.error != null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(state.error != null),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tMain),
                    visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.login(onAuth) }),
                    trailingIcon = {
                        IconButton(onClick = viewModel::toggleShowPassword) {
                            Icon(
                                if (state.showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (state.showPassword) "Ẩn mật khẩu" else "Hiện mật khẩu",
                                tint = tSub
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                AnimatedVisibility(visible = state.error != null) {
                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.07f))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                        Text(state.error ?: "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    }
                }

                Spacer(Modifier.height(16.dp))

                val canSubmit = viewModel.canSubmit()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .then(
                            if (canSubmit) Modifier.shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(999.dp),
                                ambientColor = Color(0xFF8B5CF6).copy(alpha = 0.45f),
                                spotColor = Color(0xFF6366F1).copy(alpha = 0.5f)
                            ) else Modifier
                        )
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (canSubmit) Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
                            else Brush.linearGradient(listOf(Color(0xFF6366F1).copy(alpha = 0.22f), Color(0xFF6366F1).copy(alpha = 0.22f)))
                        )
                        .clickable(enabled = canSubmit) { viewModel.login(onAuth) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(7.dp))
                            Text("Đang đăng nhập...", fontWeight = FontWeight.Black, color = Color.White)
                        } else {
                            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(Modifier.width(7.dp))
                            Text("Đăng nhập", fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Lock, null, tint = tSub, modifier = Modifier.size(9.dp))
                    Text("Xác thực qua Supabase Auth", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = tSub.copy(alpha = 0.8f))
                }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Learnsy Admin · Chỉ dành cho giáo viên",
                fontSize = 11.sp,
                color = tSub.copy(alpha = 0.6f)
            )
        }
    }
}
