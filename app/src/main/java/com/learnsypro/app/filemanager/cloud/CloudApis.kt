package com.learnsypro.app.filemanager.cloud

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

// ==================== Dropbox API v2 ====================

data class DropboxListFolderRequest(val path: String)
data class DropboxEntry(
    val name: String,
    val path_lower: String?,
    val id: String?,
    val size: Long = 0,
    val server_modified: String? = null,
    val tag: String? = null // ".tag" field: "folder" hoặc "file" - map thủ công lúc parse
)
data class DropboxListFolderResponse(val entries: List<DropboxEntry>, val has_more: Boolean = false)
data class DropboxCreateFolderRequest(val path: String)
data class DropboxDeleteRequest(val path: String)
data class DropboxDownloadArg(val path: String)

interface DropboxApi {
    @retrofit2.http.POST("2/files/list_folder")
    suspend fun listFolder(@Body request: DropboxListFolderRequest): Response<DropboxListFolderResponse>

    @retrofit2.http.POST("2/files/create_folder_v2")
    suspend fun createFolder(@Body request: DropboxCreateFolderRequest): Response<ResponseBody>

    @retrofit2.http.POST("2/files/delete_v2")
    suspend fun delete(@Body request: DropboxDeleteRequest): Response<ResponseBody>

    @retrofit2.http.POST("2/users/get_space_usage")
    suspend fun getSpaceUsage(): Response<DropboxSpaceUsage>

    /** Đổi tên = "di chuyển" sang chính thư mục cha cũ nhưng với tên mới — Dropbox không có API rename riêng. */
    @retrofit2.http.POST("2/files/move_v2")
    suspend fun move(@Body request: DropboxMoveRequest): Response<ResponseBody>

    /**
     * Tạo link chia sẻ mới. Nếu file ĐÃ có link chia sẻ từ trước, Dropbox trả lỗi
     * "shared_link_already_exists" (HTTP 409) thay vì trả lại link cũ — phải bắt riêng lỗi này
     * và gọi listSharedLinks() để lấy lại link đã tồn tại, xem DropboxService.getShareLink().
     */
    @retrofit2.http.POST("2/sharing/create_shared_link_with_settings")
    suspend fun createSharedLink(@Body request: DropboxCreateSharedLinkRequest): Response<DropboxSharedLink>

    @retrofit2.http.POST("2/sharing/list_shared_links")
    suspend fun listSharedLinks(@Body request: DropboxListSharedLinksRequest): Response<DropboxListSharedLinksResponse>
}

data class DropboxSpaceAllocation(val allocated: Long = 0)
data class DropboxSpaceUsage(val used: Long = 0, val allocation: DropboxSpaceAllocation? = null)
data class DropboxMoveRequest(val from_path: String, val to_path: String)
data class DropboxCreateSharedLinkRequest(val path: String)
data class DropboxSharedLink(val url: String)
data class DropboxListSharedLinksRequest(val path: String, val direct_only: Boolean = true)
data class DropboxListSharedLinksResponse(val links: List<DropboxSharedLink> = emptyList())

/** Upload/download Dropbox dùng content.dropboxapi.com với header Dropbox-API-Arg riêng - xem DropboxService. */

// ==================== Box API ====================

data class BoxItem(
    val id: String,
    val name: String,
    val type: String, // "file" hoặc "folder"
    val size: Long = 0,
    val modified_at: String? = null,
    val shared_link: BoxSharedLink? = null
)

data class BoxFolderItems(val entries: List<BoxItem>)

interface BoxApi {
    @GET("2.0/folders/{folderId}/items")
    suspend fun listItems(
        @Path("folderId") folderId: String,
        @Query("fields") fields: String = "id,name,type,size,modified_at"
    ): Response<BoxFolderItems>

    @retrofit2.http.POST("2.0/folders")
    suspend fun createFolder(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<BoxItem>

    @DELETE("2.0/files/{fileId}")
    suspend fun deleteFile(@Path("fileId") fileId: String): Response<Unit>

    @DELETE("2.0/folders/{folderId}")
    suspend fun deleteFolder(@Path("folderId") folderId: String, @Query("recursive") recursive: Boolean = true): Response<Unit>

    @Streaming
    @GET("2.0/files/{fileId}/content")
    suspend fun downloadFile(@Path("fileId") fileId: String): Response<ResponseBody>

    @GET("2.0/users/me")
    suspend fun getCurrentUser(@Query("fields") fields: String = "space_amount,space_used"): Response<BoxUser>

    /** Box dùng CHUNG endpoint PUT /files/{id} cho cả đổi tên (body "name") và tạo link chia sẻ
     *  (body "shared_link") — 2 hàm riêng biệt bên dưới chỉ khác phần body gửi lên, xem BoxService. */
    @PUT("2.0/files/{fileId}")
    suspend fun updateFile(@Path("fileId") fileId: String, @Body body: Map<String, @JvmSuppressWildcards Any>): Response<BoxItem>

    @PUT("2.0/folders/{folderId}")
    suspend fun updateFolder(@Path("folderId") folderId: String, @Body body: Map<String, @JvmSuppressWildcards Any>): Response<BoxItem>
}

data class BoxUser(val space_amount: Long = 0, val space_used: Long = 0)
data class BoxSharedLink(val url: String? = null)
