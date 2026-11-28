package com.example.model

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "follows", primaryKeys = ["followerId", "followingId"])
data class FollowEntity(
    val followerId: String,
    val followingId: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "likes", primaryKeys = ["userId", "videoId"])
data class LikeEntity(
    val userId: String,
    val videoId: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val receiverId: String,
    val message: String,
    val sharedVideoId: String? = null,
    val mediaType: String? = null, // "image", "video"
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val videoDurationSeconds: Int = 0,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val reaction: String? = null,
    val status: String = "sent", // "sending", "sent", "delivered", "read", "failed"
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val userId: String,
    val username: String,
    val avatarUrl: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "comment_likes", primaryKeys = ["userId", "commentId"])
data class CommentLikeEntity(
    val userId: String,
    val commentId: Long
)

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String,
    val textOverlay: String,
    val mediaUrl: String,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0L,
    val viewCount: Int = 0,
    val mentionedUserId: String? = null,
    val mentionedUsername: String? = null,
    val isReback: Boolean = false,
    val musicTrackName: String? = null,
    val musicTrackArtist: String? = null,
    val musicTrackUrl: String? = null,
    val repostedPostId: String? = null,
    val isLive: Boolean = false
)

@Entity(tableName = "multi_image_posts")
data class MultiImagePostEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String,
    val description: String,
    val imagesCsv: String,
    val timestamp: Long = System.currentTimeMillis(),
    val likesCount: Int = 0,
    val viewsCount: Int = 0,
    val commentsCount: Int = 0
)

@Dao
interface SocialDao {
    // Follows
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollow(follow: FollowEntity)

    @Delete
    suspend fun deleteFollow(follow: FollowEntity)

    @Query("SELECT * FROM follows WHERE followerId = :followerId")
    fun getFollowing(followerId: String): Flow<List<FollowEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM follows WHERE followerId = :followerId AND followingId = :followingId)")
    fun isFollowing(followerId: String, followingId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM follows WHERE followerId = :followerId AND followingId = :followingId)")
    suspend fun isFollowingDirect(followerId: String, followingId: String): Boolean

    @Query("SELECT COUNT(*) FROM follows WHERE followingId = :userId")
    fun getFollowerCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM follows WHERE followerId = :userId")
    fun getFollowingCount(userId: String): Flow<Int>

    // Likes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(like: LikeEntity)

    @Delete
    suspend fun deleteLike(like: LikeEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM likes WHERE userId = :userId AND videoId = :videoId)")
    fun isLiked(userId: String, videoId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM likes WHERE userId = :userId AND videoId = :videoId)")
    suspend fun isLikedDirect(userId: String, videoId: String): Boolean

    // Messages
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE (senderId = :u1 AND receiverId = :u2) OR (senderId = :u2 AND receiverId = :u1) ORDER BY timestamp ASC")
    fun getChatMessages(u1: String, u2: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    // Comments
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Query("SELECT * FROM comments WHERE videoId = :videoId ORDER BY timestamp DESC")
    fun getCommentsForVideo(videoId: String): Flow<List<CommentEntity>>

    // Comment Likes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommentLike(commentLike: CommentLikeEntity)

    @Delete
    suspend fun deleteCommentLike(commentLike: CommentLikeEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM comment_likes WHERE userId = :userId AND commentId = :commentId)")
    fun isCommentLiked(userId: String, commentId: Long): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM comment_likes WHERE commentId = :commentId")
    fun getCommentLikesCount(commentId: Long): Flow<Int>

    // Stories
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StoryEntity)

    @Query("SELECT * FROM stories ORDER BY timestamp DESC")
    fun getAllStories(): Flow<List<StoryEntity>>

    // Multi-Image Posts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMultiImagePost(post: MultiImagePostEntity)

    @Query("SELECT * FROM multi_image_posts ORDER BY timestamp DESC")
    fun getAllMultiImagePosts(): Flow<List<MultiImagePostEntity>>
}

@Database(
    entities = [
        FollowEntity::class,
        LikeEntity::class,
        MessageEntity::class,
        CommentEntity::class,
        CommentLikeEntity::class,
        StoryEntity::class,
        MultiImagePostEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun socialDao(): SocialDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shorts_social_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
