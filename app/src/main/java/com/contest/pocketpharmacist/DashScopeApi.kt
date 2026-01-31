package com.contest.pocketpharmacist

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface DashScopeApi {
    @POST("compatible-mode/v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): ChatResponse
}