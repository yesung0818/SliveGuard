package com.yskim.sliveguardproject.network.auth

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthApiClient {

    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logger)
        .build()

    val api: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://dev.sleepydetect.kro.kr/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}