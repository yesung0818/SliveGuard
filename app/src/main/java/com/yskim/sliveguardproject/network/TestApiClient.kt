package com.yskim.sliveguardproject.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TestApiClient {

    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logger)
        .build()

    val api: TestApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://dev.sleepydetect.kro.kr/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TestApi::class.java)
    }
}