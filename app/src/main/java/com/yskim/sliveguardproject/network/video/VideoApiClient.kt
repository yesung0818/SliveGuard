//package com.yskim.sliveguardproject.network.video
//
//import okhttp3.OkHttpClient
//import okhttp3.logging.HttpLoggingInterceptor
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//
//object VideoApiClient {
//
//    private val logger = HttpLoggingInterceptor().apply {
//        level = HttpLoggingInterceptor.Level.BODY
//    }
//
//    private val client = OkHttpClient.Builder()
//        .addInterceptor(logger)
//        .build()
//
//    val api: VideoApi by lazy {
//        Retrofit.Builder()
//            .baseUrl("https://dev.sleepydetect.kro.kr/")
//            .client(client)
//            .addConverterFactory(GsonConverterFactory.create())
//            .build()
//            .create(VideoApi::class.java)
//    }
//}
