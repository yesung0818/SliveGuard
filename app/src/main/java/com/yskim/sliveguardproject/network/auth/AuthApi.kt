package com.yskim.sliveguardproject.network.auth

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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

//    @POST("api/smart-app/video/score")
//    suspend fun getScores(
//        @Body body: VideoScoreRequest
//    ): VideoScoreResponse
//
//    @GET("api/smart-app/video/score")
//    suspend fun getScores(
//        @Query("afterTs") afterTs: Long
//    ): VideoScoreResponse

    //졸음 측정 =================================
    @POST("api/smart-app/start-measurement")
    suspend fun startMeasurement(
        @Body body: StartStopMeasurementRequest
    ) : SimpleOkResponse

    @POST("api/smart-app/stop-measurement")
    suspend fun stopMeasurement(
        @Body body: StartStopMeasurementRequest
    ) : SimpleOkResponse

    @GET("api/smart-app/video-score")
    suspend fun getVideoScore(
        @Query("userId") userId: String,
        @Query("ts") ts: Long
    ) : VideoScoreResponse

    @POST("api/smart-app/video-score")
    suspend fun postVideoScore(
        @Body body: VideoScorePostRequest
    ) : VideoScoreResponse




}