package com.example.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.MediaUploadRepository
import com.example.repository.PostRepository
import kotlinx.coroutines.launch

class PostViewModel(
    private val mediaUploadRepository: MediaUploadRepository,
    private val postRepository: PostRepository
) : ViewModel() {

    fun createPost(userId: String, caption: String, uri: Uri) {
        viewModelScope.launch {
            mediaUploadRepository.uploadAndCreatePost(
                userId = userId,
                caption = caption,
                mediaUri = uri,
                onSuccess = { post ->
                    // Post is already saved by MediaUploadRepository
                },
                onError = { error ->
                    // Handle error
                }
            )
        }
    }

    fun likePost(postId: String, userId: String) {
        viewModelScope.launch {
            postRepository.addLike(postId, userId)
        }
    }
}
