package com.yskim.sliveguardproject.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface TestApi {
    @GET("api/smart-app/test")
    suspend fun testGet(): Map<String, Any>

    @POST("api/smart-app/test")
//    suspend fun testPost(@Body body: Map<String, Any>): Map<String, Any>
    suspend fun testPost(): Map<String, Any>


}