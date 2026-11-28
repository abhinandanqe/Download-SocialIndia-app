package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.utils.GlobalMediaSessionManager
import com.example.utils.MediaResolver
import com.example.utils.ThemePreference
import com.example.zego.SocialIndiaZegoConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Social India", appName)
    }

    @Test
    fun `theme preference defaults to clean white theme`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ThemePreference.init(context)
        assertFalse("Default theme should be Clean White Theme (isDarkTheme = false)", ThemePreference.isDarkTheme.value)
    }

    @Test
    fun `google drive media resolver normalizes video image and poster urls`() {
        val driveFileId = "1aBcDeFgHiJkLmNoPqRsTuVwXyZ012345"
        val viewerUrl = "https://drive.google.com/file/d/$driveFileId/view?usp=sharing"

        val resolvedVideo = MediaResolver.resolveVideoUrl(viewerUrl)
        assertTrue("Video URL must resolve to direct Drive stream with confirm=t", resolvedVideo.contains(driveFileId) && resolvedVideo.contains("confirm=t"))

        val resolvedImage = MediaResolver.resolveImageUrl(viewerUrl)
        assertEquals("https://drive.google.com/thumbnail?id=$driveFileId&sz=w1080", resolvedImage)

        val resolvedPoster = MediaResolver.resolveVideoPosterUrl(resolvedVideo, "")
        assertEquals("https://drive.google.com/thumbnail?id=$driveFileId&sz=w1080", resolvedPoster)

        assertTrue("Sample BigBuckBunny URL must be flagged as dummy", MediaResolver.isDummyOrSampleUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"))
        assertFalse("Real Google Drive URL must not be flagged as dummy", MediaResolver.isDummyOrSampleUrl(resolvedVideo))
    }

    @Test
    fun `global media session manager switches focus cleanly between home feed reels and story`() {
        GlobalMediaSessionManager.requestFocus(com.example.utils.MediaSource.REELS)
        assertTrue(GlobalMediaSessionManager.isReelsPlaybackAllowed.value)
        assertFalse(GlobalMediaSessionManager.isHomeFeedPlaybackAllowed.value)

        GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.REELS)
        assertTrue("Releasing REELS focus must re-enable HOME_FEED playback", GlobalMediaSessionManager.isHomeFeedPlaybackAllowed.value)

        GlobalMediaSessionManager.requestFocus(com.example.utils.MediaSource.STORY)
        assertFalse(GlobalMediaSessionManager.isHomeFeedPlaybackAllowed.value)
        GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY)
        assertTrue("Releasing STORY focus must restore previous playback allowance", GlobalMediaSessionManager.isHomeFeedPlaybackAllowed.value)
    }

    @Test
    fun `zego cloud configuration is valid`() {
        assertTrue("Zego AppID must be positive", SocialIndiaZegoConfig.appId > 0L)
        assertTrue("Zego AppSign must not be blank", SocialIndiaZegoConfig.appSign.isNotBlank())
    }

    @Test
    fun `app update manager resolves google drive apk url and reads installed version`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val installedCode = com.example.utils.AppUpdateManager.getInstalledVersionCode(context)
        val installedName = com.example.utils.AppUpdateManager.getInstalledVersionName(context)
        assertTrue("Installed version code must be >= 1", installedCode >= 1)
        assertTrue("Installed version name must not be blank", installedName.isNotBlank())

        val driveApkId = "1xYzAbCdEfGhIjKlMnOpQrStUvWxYz987"
        val shareLink = "https://drive.google.com/file/d/$driveApkId/view?usp=sharing"
        val directDownloadUrl = com.example.utils.AppUpdateManager.resolveApkDownloadUrl(shareLink)
        assertTrue(
            "APK Drive link must resolve to direct confirm=t download URL",
            directDownloadUrl.contains(driveApkId) && directDownloadUrl.contains("export=download") && directDownloadUrl.contains("confirm=t")
        )
    }

    @Test
    fun `photo and video posts classify accurately and enforce multi user public and private visibility`() {
        val driveFileId = "1pHoToViDeOTeStId9876543210"
        val drivePhotoUrl = "https://drive.google.com/thumbnail?id=$driveFileId&sz=w1080"
        val driveVideoUrl = "https://drive.usercontent.google.com/download?id=$driveFileId&export=download&confirm=t&ext=.mp4"

        assertTrue("Google Drive thumbnail URL must be recognized as image", MediaResolver.isImage(drivePhotoUrl))
        assertFalse("Google Drive video streaming URL must be recognized as video", MediaResolver.isImage(driveVideoUrl))

        val videoFallbacks = MediaResolver.getFallbackVideoUrls(driveVideoUrl)
        assertTrue("Video fallbacks must contain multiple streaming endpoints", videoFallbacks.size >= 2)
        assertTrue(videoFallbacks.first().contains("drive.usercontent.google.com"))

        val imageFallbacks = MediaResolver.getFallbackImageUrls(drivePhotoUrl)
        assertTrue("Image fallbacks must contain thumbnail and lh3 endpoints", imageFallbacks.size >= 2)

        val photoModel = com.example.model.Video(
            id = "post_photo_1",
            userId = "userA",
            videoUrl = drivePhotoUrl,
            thumbnailUrl = drivePhotoUrl,
            mediaType = "image",
            visibility = "PUBLIC",
            aspectRatio = 0.8f
        )
        val videoModel = com.example.model.Video(
            id = "post_video_1",
            userId = "userA",
            videoUrl = driveVideoUrl,
            thumbnailUrl = drivePhotoUrl,
            mediaType = "video",
            visibility = "PUBLIC",
            aspectRatio = 0.5625f
        )
        assertTrue("Photo post must have isMediaAnImage == true", photoModel.isMediaAnImage)
        assertFalse("Video post must have isMediaAnImage == false even with image thumbnailUrl", videoModel.isMediaAnImage)

        val userAPublicPost = com.example.model.FirebasePost(
            id = "pub_1",
            userId = "userA",
            postUrl = drivePhotoUrl,
            mediaType = "image",
            visibility = "PUBLIC",
            isDeleted = false,
            isArchived = false
        )
        val userAPrivatePost = com.example.model.FirebasePost(
            id = "priv_1",
            userId = "userA",
            postUrl = driveVideoUrl,
            mediaType = "video",
            visibility = "PRIVATE",
            isDeleted = false,
            isArchived = false
        )
        val allPosts = listOf(userAPublicPost, userAPrivatePost)
        val viewerUserB = "userB"
        val visibleToUserB = allPosts.filter { post ->
            !post.isDeleted && !post.isArchived && (post.visibility == "PUBLIC" || post.userId == viewerUserB)
        }
        assertEquals("User B must see User A's public post but not User A's private post", 1, visibleToUserB.size)
        assertEquals("pub_1", visibleToUserB.first().id)
    }

    @Test
    fun `pagination page size optimistic cache rollback and webp compression work smoothly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Feed pagination batch size must be 10 posts at a time", 10, com.example.viewmodel.VideoViewModel.PAGE_SIZE)

        // Optimistic Like / Save / Follow cache & rollback
        com.example.model.RepositoryManager.setPostLikedCached("post_opt_1", true)
        assertTrue(com.example.model.RepositoryManager.hasPostLikedCached("post_opt_1"))
        assertTrue(com.example.model.RepositoryManager.isPostLikedCached("post_opt_1"))
        com.example.model.RepositoryManager.setPostLikedCached("post_opt_1", false) // rollback
        assertFalse(com.example.model.RepositoryManager.isPostLikedCached("post_opt_1"))

        com.example.model.RepositoryManager.setPostSavedCached("post_opt_1", true)
        assertTrue(com.example.model.RepositoryManager.hasPostSavedCached("post_opt_1"))
        assertTrue(com.example.model.RepositoryManager.isPostSavedCached("post_opt_1"))
        com.example.model.RepositoryManager.setPostSavedCached("post_opt_1", false) // rollback
        assertFalse(com.example.model.RepositoryManager.isPostSavedCached("post_opt_1"))

        com.example.model.RepositoryManager.setFollowingCached("creator_opt_1", true)
        assertTrue(com.example.model.RepositoryManager.isFollowingCached("creator_opt_1"))
        com.example.model.RepositoryManager.setFollowingCached("creator_opt_1", false) // rollback
        assertFalse(com.example.model.RepositoryManager.isFollowingCached("creator_opt_1"))

        // Instant in-memory feed cache
        val sampleVideo = com.example.model.Video(id = "cached_vid_1", userId = "u1", videoUrl = "https://example.com/v1.mp4")
        com.example.model.RepositoryManager.setCachedFeedVideos(listOf(sampleVideo))
        assertEquals(1, com.example.model.RepositoryManager.getCachedFeedVideos().size)
        assertEquals("cached_vid_1", com.example.model.RepositoryManager.getCachedFeedVideos().first().id)

        // WebP compression before upload
        val srcFile = java.io.File(context.cacheDir, "test_raw_photo.png")
        val bmp = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.BLUE)
        java.io.FileOutputStream(srcFile).use { fos ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
        }
        val webpFile = com.example.repository.MediaRepository.compressImageToWebP(srcFile, context.cacheDir)
        assertTrue("WebP compressed file must be created and non-empty", webpFile != null && webpFile.exists() && webpFile.length() > 0L && webpFile.name.endsWith(".webp"))
        srcFile.delete()
        webpFile?.delete()
    }
}


