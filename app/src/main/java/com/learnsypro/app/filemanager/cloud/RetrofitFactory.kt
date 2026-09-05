package com.learnsypro.app.filemanager.cloud

import com.learnsypro.app.filemanager.model.CloudProvider
import com.learnsypro.app.filemanager.util.SecurePrefs
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.content.Context

/** Tạo Retrofit client có tự động gắn header Authorization: Bearer <token> cho từng provider. */
object RetrofitFactory {

    private fun authInterceptor(context: Context, provider: CloudProvider): Interceptor = Interceptor { chain ->
        val token = SecurePrefs.getInstance(context).getCloudAccessToken(provider)
        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrEmpty()) {
                addHeader("Authorization", "Bearer $token")
            }
            // Dropbox API v2 bắt buộc Content-Type: application/json trên các endpoint RPC dạng
            // POST (list_folder, create_folder_v2, delete_v2, get_space_usage...) — Retrofit +
            // GsonConverterFactory không tự set header này khi @Body là data class thường, dẫn
            // tới lỗi "Lỗi Dropbox: 400" đang gặp. Box API không cần header này nên chỉ set khi
            // có body và endpoint là Dropbox.
            if (provider == CloudProvider.DROPBOX && chain.request().body != null) {
                addHeader("Content-Type", "application/json")
            }
        }.build()
        chain.proceed(request) as Response
    }

    /**
     * Tự động làm mới access token khi request bị Dropbox/Box từ chối với 401 (token hết hạn),
     * rồi TỰ ĐỘNG GỬI LẠI request đó với token mới — người dùng không hề hay biết, không phải
     * vào Cloud bấm hủy liên kết rồi liên kết lại. Đây là mảnh còn thiếu khiến trước đây
     * accessToken hết hạn sau vài tiếng là mọi thao tác Cloud báo lỗi (xem chú thích chi tiết ở
     * OAuthManager.refreshAccessTokenBlocking).
     *
     * Đồng bộ hoá (synchronized) theo TỪNG provider: nếu nhiều request cùng lúc đều dính 401 (ví
     * dụ đang tải song song vài file), CHỈ luồng đầu tiên thực sự gọi endpoint làm mới token —
     * các luồng sau chờ, rồi dùng luôn token mới vừa lưu thay vì mỗi luồng tự gọi làm mới riêng.
     * Quan trọng vì refresh token ở một số provider chỉ dùng được 1 lần (rotate) — gọi làm mới
     * song song nhiều lần có thể khiến các lần gọi sau nhận refresh token đã bị vô hiệu bởi lần
     * gọi trước, làm hỏng phiên đăng nhập thay vì sửa lỗi.
     */
    private class CloudTokenAuthenticator(
        private val context: Context,
        private val provider: CloudProvider
    ) : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            // Đã thử làm mới rồi mà vẫn 401 -> refresh token cũng không dùng được nữa (đã bị
            // OAuthManager.refreshAccessTokenBlocking xoá khỏi SecurePrefs), dừng lại để lỗi 401
            // nổi lên bình thường thay vì lặp vô hạn.
            if (responseCount(response) >= 2) return null

            val prefs = SecurePrefs.getInstance(context)
            val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

            return synchronized(lockFor(provider)) {
                val currentToken = prefs.getCloudAccessToken(provider)
                val tokenToUse = if (!currentToken.isNullOrEmpty() && currentToken != failedToken) {
                    // Luồng khác vừa làm mới xong trong lúc luồng này chờ lock -> dùng luôn.
                    currentToken
                } else {
                    OAuthManager.refreshAccessTokenBlocking(context, provider)
                }
                tokenToUse?.let {
                    response.request.newBuilder().header("Authorization", "Bearer $it").build()
                }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    // Khởi tạo sẵn lock cho cả 3 provider ngay từ đầu (thay vì tạo "lười" lúc cần) — vì
    // CloudProvider chỉ có 3 giá trị cố định nên không tốn kém gì, và tránh hẳn 1 race condition
    // hiếm gặp: nếu tạo lười bằng getOrPut, 2 luồng cùng lúc gọi lần ĐẦU TIÊN cho cùng 1 provider
    // có thể mỗi luồng tạo 1 Any() khác nhau trước khi kịp ghi vào map, khiến 1 trong 2 luồng
    // đồng bộ hoá nhầm trên lock "mồ côi" không ai khác dùng, làm mất tác dụng chống refresh
    // song song đúng lúc cần nhất (lần đầu token hết hạn).
    private val providerLocks: Map<CloudProvider, Any> = CloudProvider.values().associateWith { Any() }
    private fun lockFor(provider: CloudProvider): Any = providerLocks.getValue(provider)

    private fun client(context: Context, provider: CloudProvider): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor(context, provider))
            .addInterceptor(logging)
            // NGUYÊN NHÂN CHÍNH GÂY "Đã có lỗi xảy ra" + cảm giác giật/lag khi thao tác Cloud:
            // OkHttpClient mặc định (không chỉnh gì) dùng timeout 10 giây cho MỖI CHIỀU
            // (connect/read/write). Với mạng di động/WiFi không ổn định hoặc file lớn, riêng
            // bước tải NỘI DUNG file (downloadFile/uploadFile, không đi qua đây mà tự tạo
            // OkHttpClient() qua okHttpFor() — CŨNG dùng builder này) rất dễ vượt quá 10 giây,
            // khiến toàn bộ request bị hủy giữa chừng và ném SocketTimeoutException, rơi vào
            // catch(e: Exception) -> Result.failure ở từng Service, hiện Snackbar lỗi chung
            // chung dù bản chất chỉ là mạng chậm chứ không phải lỗi thật. Tăng lên 30s
            // connect/read/write đủ rộng cho cả API JSON (nhanh) lẫn tải file cỡ vừa mà không
            // treo vô hạn nếu mạng thực sự mất kết nối.
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            // Tự động thử lại 1 lần khi lỗi kết nối cấp thấp (mất sóng thoáng qua, đổi mạng
            // WiFi<->4G giữa chừng) thay vì thất bại ngay lần đầu — mặc định OkHttp có bật sẵn
            // nhưng khai báo tường minh ở đây để chắc chắn không bị tắt nhầm nếu ai đó sửa builder sau này.
            .retryOnConnectionFailure(true)
        // Google Drive không đi qua client này trong thực tế (GoogleDriveService tự dùng Drive
        // SDK riêng), nhưng vẫn chặn tường minh ở đây: chỉ gắn Authenticator cho Dropbox/Box.
        if (provider != CloudProvider.GOOGLE_DRIVE) {
            builder.authenticator(CloudTokenAuthenticator(context, provider))
        }
        return builder.build()
    }

    /**
     * Authenticator DÙNG CHUNG theo host — khác CloudTokenAuthenticator trong RetrofitFactory
     * (1 authenticator/1 provider cố định vì mỗi Retrofit instance chỉ gọi 1 provider), client
     * này phục vụ Coil load ẢNH TRỰC TIẾP TỪ URL (getThumbnailRequest trả url+header, Coil tự
     * gọi bằng OkHttpClient riêng của nó, KHÔNG đi qua RetrofitFactory) nên phải tự suy ra
     * provider từ host của chính request đang bị 401, rồi làm mới đúng token của provider đó.
     *
     * Đây là mảnh còn thiếu khiến trước đây thumbnail/ảnh/video xem trực tiếp trên Cloud báo
     * lỗi tải khi access token hết hạn dù OAuthManager.refreshAccessTokenBlocking() đã tồn tại
     * và hoạt động tốt cho các thao tác list/upload/download (vốn đi qua RetrofitFactory) — Coil
     * trước đây dùng OkHttpClient mặc định của chính nó, không hề biết tới cơ chế refresh này.
     */
    private class HostBasedCloudAuthenticator(private val context: Context) : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (responseCount(response) >= 2) return null
            val provider = providerForHost(response.request.url.host) ?: return null

            val prefs = SecurePrefs.getInstance(context)
            val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

            return synchronized(lockFor(provider)) {
                val currentToken = prefs.getCloudAccessToken(provider)
                val tokenToUse = if (!currentToken.isNullOrEmpty() && currentToken != failedToken) {
                    currentToken
                } else {
                    OAuthManager.refreshAccessTokenBlocking(context, provider)
                }
                tokenToUse?.let { newToken ->
                    val builder = response.request.newBuilder().header("Authorization", "Bearer $newToken")
                    // Dropbox: header Dropbox-API-Arg chứa path, KHÔNG chứa token -> không cần sửa,
                    // chỉ Authorization là cần thay khi refresh.
                    builder.build()
                }
            }
        }

        private fun providerForHost(host: String): CloudProvider? = when {
            host.endsWith("dropboxapi.com") -> CloudProvider.DROPBOX
            host.endsWith("box.com") -> CloudProvider.BOX
            else -> null // Google Drive (content.googleapis.com/drive.google.com) tự refresh qua Play Services, không xử lý ở đây
        }
    }

    /** OkHttpClient dùng riêng cho Coil (load thumbnail/ảnh/video trực tiếp từ URL cloud) — có sẵn khả năng tự làm mới token khi 401, khác client mặc định Coil tự tạo. */
    fun coilClient(context: Context): OkHttpClient = OkHttpClient.Builder()
        .authenticator(HostBasedCloudAuthenticator(context))
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun dropbox(context: Context): DropboxApi = Retrofit.Builder()
        .baseUrl("https://api.dropboxapi.com/")
        .client(client(context, CloudProvider.DROPBOX))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DropboxApi::class.java)

    fun box(context: Context): BoxApi = Retrofit.Builder()
        .baseUrl("https://api.box.com/")
        .client(client(context, CloudProvider.BOX))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BoxApi::class.java)

    fun okHttpFor(context: Context, provider: CloudProvider): OkHttpClient = client(context, provider)
}
