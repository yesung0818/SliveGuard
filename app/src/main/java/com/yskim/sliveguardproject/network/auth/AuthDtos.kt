package com.yskim.sliveguardproject.network.auth

data class LoginRequest(
    val loginId: String,
    val password: String
)

data class SignupRequest(
    val loginId: String,
    val password: String,
    val name: String,
    val birth: String
)

data class RegisterResponse(
    val ok: Boolean,
    val message: String,
    val data: Map<String, Any>? = null
)

data class KakaoLoginRequest(
    val kakaoAccessToken: String
)

data class UserDto(
    val id: Long,
    val name: String
)

data class LoginResponse(
    val accessToken: String,
    val user: UserDto
)

data class CheckDuplicateIdRequest(
    val loginId: String
)

data class SimpleOkResponse(
    val ok: Boolean,
    val message: String,
    val userId: String? = null
)

data class ApiResponse(
    val ok: Boolean,
    val message: String,
    val data: Map<String, Any>? = null
)

data class DeleteUserRequest(
    val loginId: String
)

//data class VideoScoreItem(
//    val ts: Long,
//    val s_video: Double
//)
//
//data class VideoScoreResponse(
//    val items: List<VideoScoreItem>
//)

data class VideoScoreRequest(
    val userId: String,
    val ts: Long
)

data class StartStopMeasurementRequest(
    val userId: String
)

//data class VideoScoreResponse(
//    val ok: Boolean,
//    val s_video: Double?,
//    val ts: Long?
//)

data class VideoScoreResponse(
    val ok: Boolean,
    val message: String? = null,
    val s_video: Double? = null,
    val userId: String? = null,
    val ts: Long? = null,
    val updatedAt: String? = null
)

data class VideoScorePostRequest(
    val userId: String,
    val ts: Long
)
