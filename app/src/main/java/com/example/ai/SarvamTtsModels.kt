package com.example.ai

import kotlinx.serialization.Serializable

@Serializable
data class SarvamVoice(
    val id: String,
    val displayName: String,
    val gender: String, // "Female", "Male"
    val languageCode: String = "hi-IN",
    val languageName: String = "Hindi",
    val model: String = "bulbul:v3",
    val tag: String = "Conversational",
    val isDefault: Boolean = false
)

data class SarvamTtsSettings(
    val voiceId: String = "ritu",
    val model: String = "bulbul:v3",
    val languageCode: String = "hi-IN",
    val pace: Float = 1.0f,
    val pitch: Float = 0.0f,
    val loudness: Float = 1.5f
)

object SarvamVoiceRegistry {
    val ALL_VOICES = listOf(
        // Default flagship voices for Mahi
        SarvamVoice(
            id = "ritu",
            displayName = "Ritu (Mahi Default)",
            gender = "Female",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Empathetic & Friendly",
            isDefault = true
        ),
        SarvamVoice(
            id = "priya",
            displayName = "Priya",
            gender = "Female",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Expressive & Warm"
        ),
        SarvamVoice(
            id = "kavya",
            displayName = "Kavya",
            gender = "Female",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Cheerful & Modern"
        ),
        SarvamVoice(
            id = "simran",
            displayName = "Simran",
            gender = "Female",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Sweet & Gentle"
        ),
        SarvamVoice(
            id = "ishita",
            displayName = "Ishita",
            gender = "Female",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Conversational Indian"
        ),
        SarvamVoice(
            id = "neha",
            displayName = "Neha",
            gender = "Female",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Polished & Clear"
        ),
        SarvamVoice(
            id = "pooja",
            displayName = "Pooja",
            gender = "Female",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Soft & Caring"
        ),
        SarvamVoice(
            id = "shreya",
            displayName = "Shreya",
            gender = "Female",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Energetic & Lively"
        ),
        SarvamVoice(
            id = "suhani",
            displayName = "Suhani",
            gender = "Female",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Social & Fun"
        ),
        SarvamVoice(
            id = "roopa",
            displayName = "Roopa",
            gender = "Female",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Companion & Storyteller"
        ),
        SarvamVoice(
            id = "tanya",
            displayName = "Tanya",
            gender = "Female",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Youthful & Vibrant"
        ),

        // Male Voices
        SarvamVoice(
            id = "aditya",
            displayName = "Aditya",
            gender = "Male",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Smart & Professional"
        ),
        SarvamVoice(
            id = "shubh",
            displayName = "Shubh",
            gender = "Male",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Deep & Charismatic"
        ),
        SarvamVoice(
            id = "ashutosh",
            displayName = "Ashutosh",
            gender = "Male",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Confident & Calm"
        ),
        SarvamVoice(
            id = "rahul",
            displayName = "Rahul",
            gender = "Male",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Friendly & Dynamic"
        ),
        SarvamVoice(
            id = "kabir",
            displayName = "Kabir",
            gender = "Male",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Warm & Reassuring"
        ),
        SarvamVoice(
            id = "aayan",
            displayName = "Aayan",
            gender = "Male",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Modern Conversational"
        ),
        SarvamVoice(
            id = "rohan",
            displayName = "Rohan",
            gender = "Male",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Natural & Casual"
        ),
        SarvamVoice(
            id = "dev",
            displayName = "Dev",
            gender = "Male",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Story & Creative"
        ),
        SarvamVoice(
            id = "amit",
            displayName = "Amit",
            gender = "Male",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v3",
            tag = "Authoritative & Clear"
        ),

        // Multi-Language Regional Voices
        SarvamVoice(
            id = "ishita_enhi_companion",
            displayName = "Ishita (Hinglish)",
            gender = "Female",
            languageCode = "en-IN",
            languageName = "Hinglish / Indian English",
            model = "bulbul:v4-flash",
            tag = "Companion Pro"
        ),
        SarvamVoice(
            id = "sanchita_hi_assistant",
            displayName = "Sanchita (Assistant)",
            gender = "Female",
            languageCode = "hi-IN",
            languageName = "Hindi",
            model = "bulbul:v4-flash",
            tag = "AI Smart Assistant"
        ),
        SarvamVoice(
            id = "roopa_bn_conversational",
            displayName = "Roopa (Bengali)",
            gender = "Female",
            languageCode = "bn-IN",
            languageName = "Bengali",
            model = "bulbul:v4-flash",
            tag = "Sweet Bengali"
        ),
        SarvamVoice(
            id = "ishita_mr_conversational",
            displayName = "Ishita (Marathi)",
            gender = "Female",
            languageCode = "mr-IN",
            languageName = "Marathi",
            model = "bulbul:v4-flash",
            tag = "Marathi Conversational"
        ),
        SarvamVoice(
            id = "kavitha_te_conversation",
            displayName = "Kavitha (Telugu)",
            gender = "Female",
            languageCode = "te-IN",
            languageName = "Telugu",
            model = "bulbul:v4-flash",
            tag = "Telugu Conversational"
        ),
        SarvamVoice(
            id = "gokul_ta_narration",
            displayName = "Gokul (Tamil)",
            gender = "Male",
            languageCode = "ta-IN",
            languageName = "Tamil",
            model = "bulbul:v4-flash",
            tag = "Tamil Voice"
        ),
        SarvamVoice(
            id = "pooja_gu_conversational",
            displayName = "Pooja (Gujarati)",
            gender = "Female",
            languageCode = "gu-IN",
            languageName = "Gujarati",
            model = "bulbul:v4-flash",
            tag = "Gujarati Conversational"
        ),
        SarvamVoice(
            id = "anand_pa_conversation",
            displayName = "Anand (Punjabi)",
            gender = "Male",
            languageCode = "pa-IN",
            languageName = "Punjabi",
            model = "bulbul:v4-flash",
            tag = "Punjabi Conversational"
        )
    )

    fun getVoiceById(id: String): SarvamVoice {
        return ALL_VOICES.find { it.id.equals(id, ignoreCase = true) } ?: ALL_VOICES.first()
    }
}
