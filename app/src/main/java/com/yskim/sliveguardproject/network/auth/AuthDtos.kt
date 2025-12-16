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
    val message: String
)

data class ApiResponse(
    val ok: Boolean,
    val message: String,
    val data: Map<String, Any>? = null
)

data class DeleteUserRequest(
    val loginId: String
)
