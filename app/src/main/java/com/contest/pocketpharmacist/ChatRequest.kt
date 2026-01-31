package com.contest.pocketpharmacist

// ================== 请求部分 ==================
data class ChatRequest(
    val model: String = "qwen-vl-plus",
    val messages: List<Message>
)

data class Message(
    val role: String,  // "system", "user", "assistant"
    val content: Any   // String 或 List<ContentItem>
)

data class ContentItem(
    val type: String,           // "text" 或 "image_url"
    val text: String? = null,
    val image_url: ImageUrl? = null
)

data class ImageUrl(
    val url: String  // base64格式: "data:image/jpeg;base64,..."
)

// ================== 响应部分（全部独立，不嵌套） ==================
data class ChatResponse(
    val choices: List<Choice>? = null,
    val usage: Usage? = null,
    val created: Long? = null,
    val id: String? = null
)

data class Choice(
    val index: Int = 0,
    val message: MessageContent? = null,
    val finish_reason: String? = null
)

data class MessageContent(
    val role: String? = null,
    val content: String? = null
)

data class Usage(
    val input_tokens: Int? = null,
    val output_tokens: Int? = null,
    val total_tokens: Int? = null
)