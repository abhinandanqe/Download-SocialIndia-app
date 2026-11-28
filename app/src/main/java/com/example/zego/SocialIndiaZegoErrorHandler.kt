package com.example.zego

import android.content.Context
import android.widget.Toast

enum class ZegoErrorCode {
    INVALID_CREDENTIALS,
    TOKEN_EXPIRED,
    ROOM_UNAVAILABLE,
    NETWORK_ERROR,
    PERMISSION_DENIED,
    USER_NOT_FOUND,
    ALREADY_IN_ROOM,
    DUPLICATE_LOGIN,
    CAMERA_UNAVAILABLE,
    MICROPHONE_UNAVAILABLE,
    SDK_INIT_FAILURE,
    SERVER_ERROR,
    RATE_LIMIT,
    UNSUPPORTED_FEATURE,
    UNKNOWN_ERROR
}

object SocialIndiaZegoErrorHandler {

    fun getErrorMessage(code: ZegoErrorCode): String {
        return when (code) {
            ZegoErrorCode.INVALID_CREDENTIALS -> "Communication service credentials could not be verified."
            ZegoErrorCode.TOKEN_EXPIRED -> "Your session has expired. Reconnecting..."
            ZegoErrorCode.ROOM_UNAVAILABLE -> "The requested live stream or room has ended or is unavailable."
            ZegoErrorCode.NETWORK_ERROR -> "Poor network connection. Checking connection..."
            ZegoErrorCode.PERMISSION_DENIED -> "Camera and microphone permissions are required for calling."
            ZegoErrorCode.USER_NOT_FOUND -> "User is currently offline or unavailable."
            ZegoErrorCode.ALREADY_IN_ROOM -> "You are already active in another session."
            ZegoErrorCode.DUPLICATE_LOGIN -> "Your account was connected from another device."
            ZegoErrorCode.CAMERA_UNAVAILABLE -> "Camera is currently in use by another application."
            ZegoErrorCode.MICROPHONE_UNAVAILABLE -> "Microphone is currently unavailable."
            ZegoErrorCode.SDK_INIT_FAILURE -> "Unable to initialize communication service. Please retry."
            ZegoErrorCode.SERVER_ERROR -> "Service temporarily busy. Please try again shortly."
            ZegoErrorCode.RATE_LIMIT -> "Too many attempts. Please wait a moment."
            ZegoErrorCode.UNSUPPORTED_FEATURE -> "This feature is not supported on this device version."
            ZegoErrorCode.UNKNOWN_ERROR -> "Something went wrong. Please try again."
        }
    }

    fun showToast(context: Context, code: ZegoErrorCode) {
        Toast.makeText(context, getErrorMessage(code), Toast.LENGTH_SHORT).show()
    }
}
