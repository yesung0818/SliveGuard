//package com.yskim.sliveguardproject.network.video
//
//import retrofit2.http.Body
//import retrofit2.http.GET
//import retrofit2.http.POST
//import retrofit2.http.Query
//
//data class VideoScoreItem(
//    val ts: Long,
//    val s_video: Double
//)
//
//data class VideoScoreResponse(
//    val items: List<VideoScoreItem>
//)
//
//data class VideoScoreRequest(
//    val userId: String,
//    val ts: Long
//)
//
//interface VideoApi {
//    @POST("api/smart-app/video/score")
//    suspend fun getScores(
//        @Body body: VideoScoreRequest
//    ): VideoScoreResponse
//
//    @GET("api/smart-app/video/score")
//    suspend fun getScores(
//        @Query("afterTs") afterTs: Long
//    ): VideoScoreResponse
//}