package com.yskim.sliveguardproject.network.auth

import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

//    @POST("api/smart-app/auth/login")
//    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("api/smart-app/auth/signup")
    suspend fun signup(@Body body: SignupRequest)

    @POST("api/smart-app/auth/kakao")
    suspend fun kakaoLogin(@Body body: KakaoLoginRequest): LoginResponse

    @POST("register")
    suspend fun register(@Body body: SignupRequest): RegisterResponse

    @POST("check-duplicate-id")
    suspend fun checkDuplicateId(@Body body: CheckDuplicateIdRequest): SimpleOkResponse

    @POST("api/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse

    @POST("api/deleteUser")
    suspend fun deleteUser(@Body body: DeleteUserRequest): ApiResponse

}