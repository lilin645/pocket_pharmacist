package com.contest.pocketpharmacist

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://dashscope.aliyuncs.com/"
    const val API_KEY = "sk-c3ef746926034340a4861ede7a54c205"

    val api: DashScopeApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DashScopeApi::class.java)
    }
}