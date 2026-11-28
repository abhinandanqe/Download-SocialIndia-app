package com.example.model

import com.google.firebase.firestore.PropertyName

data class AiTool(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var iconUrl: String = "",
    var category: String = "Other", // Chat, Image, Video, Audio, Editing, Writing, Translation, Other
    var status: String = "Draft", // Draft, Active, Disabled, Maintenance
    @get:PropertyName("api_endpoint") @set:PropertyName("api_endpoint")
    var apiEndpoint: String = "",
    @get:PropertyName("http_method") @set:PropertyName("http_method")
    var httpMethod: String = "POST",
    @get:PropertyName("model_identifier") @set:PropertyName("model_identifier")
    var modelIdentifier: String = "gemini-1.5-flash",
    var accessControl: String = "Everyone", // Everyone, Verified, Creators
    @get:PropertyName("daily_limit") @set:PropertyName("daily_limit")
    var dailyLimit: Int = 0,
    var order: Int = 0,
    @get:PropertyName("supported_input") @set:PropertyName("supported_input")
    var supportedInput: String = "text", // text, image, video, audio
    @get:PropertyName("supported_output") @set:PropertyName("supported_output")
    var supportedOutput: String = "text",
    @get:PropertyName("requires_key") @set:PropertyName("requires_key")
    var requiresKey: Boolean = true // Whether it routes through Gemini api key
)

data class AiRequestHistory(
    var id: String = "",
    var aiId: String = "",
    var aiName: String = "",
    var userId: String = "",
    var prompt: String = "",
    var resultText: String = "",
    var resultMediaUrl: String = "",
    var timestamp: Long = System.currentTimeMillis()
)
