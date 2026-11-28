package com.example

import com.example.model.FirebasePost
import com.example.model.StoryUiModel
import com.example.repository.EXCLUSIVE_APPROVAL_WINDOW_MS
import com.example.repository.EntitlementRepository
import com.example.repository.ExclusivePurchaseRecord
import com.example.repository.canBuyerPurchaseExclusive
import com.example.repository.computeApprovalBalanceTransition
import com.example.repository.computeExclusiveSplit
import com.example.repository.isExclusiveRequestExpired
import com.example.repository.isExclusiveStatusPending
import com.example.repository.isExclusiveStatusUnlocked
import com.example.ui.formatCountdownRemaining
import com.example.utils.VideoTranscoder
import com.example.viewmodel.StoryViewModel
import com.example.viewmodel.VideoViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SocialIndiaFlowsTest {

    @Test
    fun `video upload url validation and 3x exponential retry backoff behave as expected`() {
        assertFalse(VideoTranscoder.isValidRemoteMediaUrl(null))
        assertFalse(VideoTranscoder.isValidRemoteMediaUrl(""))
        assertFalse(VideoTranscoder.isValidRemoteMediaUrl("content://media/external/video/123"))
        assertTrue(VideoTranscoder.isValidRemoteMediaUrl("https://firebasestorage.googleapis.com/v0/b/socialindia/o/reel.mp4?alt=media"))

        assertEquals(800L, VideoTranscoder.computeRetryBackoffMs(1))
        assertEquals(1800L, VideoTranscoder.computeRetryBackoffMs(2))
        assertEquals(3500L, VideoTranscoder.computeRetryBackoffMs(3))
    }

    @Test
    fun `story optimistic merge keeps instant story until remote snapshot arrives and expires after 24h`() {
        val now = 1_750_000_000_000L
        val optStory = StoryUiModel(
            id = "story_opt_1",
            username = "creator1",
            avatarUrl = "",
            imageUrl = "file:///cache/story_local.jpg",
            textOverlay = "",
            userId = "u_creator",
            timestamp = now - 5_000L
        )
        val expiredStory = StoryUiModel(
            id = "story_old_1",
            username = "creator2",
            avatarUrl = "",
            imageUrl = "https://cdn.example.com/old.jpg",
            textOverlay = "",
            userId = "u_other",
            timestamp = now - StoryViewModel.STORY_TTL_MS - 1_000L
        )

        assertTrue(StoryViewModel.isStoryExpired(expiredStory.timestamp, now))
        assertFalse(StoryViewModel.isStoryExpired(optStory.timestamp, now))

        val mergedBeforeRemote = StoryViewModel.mergeStoriesWithOptimistic(
            remoteStories = emptyList(),
            optimisticMap = mapOf(
                optStory.id to optStory,
                expiredStory.id to expiredStory
            ),
            nowMs = now
        )
        assertEquals(1, mergedBeforeRemote.size)
        assertEquals("story_opt_1", mergedBeforeRemote.first().id)

        // When Firestore snapshot includes the published story ID, avoid duplicate
        val remotePublished = optStory.copy(imageUrl = "https://firebasestorage.googleapis.com/story.jpg")
        val mergedAfterRemote = StoryViewModel.mergeStoriesWithOptimistic(
            remoteStories = listOf(remotePublished),
            optimisticMap = mapOf(optStory.id to optStory),
            nowMs = now
        )
        assertEquals(1, mergedAfterRemote.size)
        assertEquals("https://firebasestorage.googleapis.com/story.jpg", mergedAfterRemote.first().imageUrl)
    }

    @Test
    fun `exclusive content stays locked during pending_approval and unlocks only after creator approval`() {
        val buyerId = "buyer_101"
        val creatorId = "creator_202"
        val contentId = "exclusive_post_99"

        // Owner always has access
        assertTrue(
            EntitlementRepository.hasAccessFast(
                userId = creatorId,
                creatorId = creatorId,
                contentId = contentId,
                isExclusive = true,
                isPaidPost = true
            )
        )

        // Buyer is blocked initially
        assertFalse(
            EntitlementRepository.hasAccessFast(
                userId = buyerId,
                creatorId = creatorId,
                contentId = contentId,
                isExclusive = true,
                isPaidPost = true
            )
        )

        // Paid -> pending_approval (still locked, duplicate purchase prevented)
        val pendingRecord = ExclusivePurchaseRecord(
            id = "${buyerId}_${contentId}",
            buyerId = buyerId,
            contentId = contentId,
            creatorId = creatorId,
            amount = 99.0,
            status = "pending_approval",
            paymentVerified = true,
            expiresAt = System.currentTimeMillis() + EXCLUSIVE_APPROVAL_WINDOW_MS
        )
        EntitlementRepository.markPurchasePendingLocally(pendingRecord)
        assertTrue(isExclusiveStatusPending(pendingRecord.status))
        assertFalse(isExclusiveStatusUnlocked(pendingRecord.status))
        assertFalse("Duplicate purchase must be blocked while pending_approval", canBuyerPurchaseExclusive(pendingRecord.status))
        assertFalse(
            "Viewer must NOT gain playback access while pending_approval",
            EntitlementRepository.hasAccessFast(
                userId = buyerId,
                creatorId = creatorId,
                contentId = contentId,
                isExclusive = true,
                isPaidPost = true
            )
        )

        // Creator approves -> approved + unlocked
        EntitlementRepository.markUnlockedLocally(buyerId, contentId)
        assertTrue(isExclusiveStatusUnlocked("approved"))
        assertFalse("Already approved post cannot be purchased twice", canBuyerPurchaseExclusive("approved"))
        assertTrue(
            "Viewer must unlock immediately once approved",
            EntitlementRepository.hasAccessFast(
                userId = buyerId,
                creatorId = creatorId,
                contentId = contentId,
                isExclusive = true,
                isPaidPost = true
            )
        )
    }

    @Test
    fun `exclusive payment escrow approval reject and 48h auto refund math are exact`() {
        val split = computeExclusiveSplit(priceInr = 200.0, commissionPct = 15.0)
        assertEquals(200.0, split.grossAmount, 0.001)
        assertEquals(30.0, split.commissionAmount, 0.001)
        assertEquals(170.0, split.creatorNetAmount, 0.001)

        // Payment credited to creator Pending balance, then Approve moves Pending -> Available
        val creatorPendingAfterPay = 170.0
        val creatorAvailableBefore = 50.0
        val transition = computeApprovalBalanceTransition(
            currentPendingBalance = creatorPendingAfterPay,
            currentAvailableBalance = creatorAvailableBefore,
            netAmount = split.creatorNetAmount
        )
        assertEquals(0.0, transition.newPendingBalance, 0.001)
        assertEquals(220.0, transition.newAvailableBalance, 0.001)

        // Reject or Refunded allows clean state & buyer can re-request later if unblocked
        assertTrue(canBuyerPurchaseExclusive("rejected"))
        assertTrue(canBuyerPurchaseExclusive("refunded"))

        // 48h expiry check triggers auto-refund strictly at/after expiresAt
        val createdAt = 1_750_000_000_000L
        val expiresAt = createdAt + EXCLUSIVE_APPROVAL_WINDOW_MS
        assertFalse(isExclusiveRequestExpired("pending_approval", expiresAt, expiresAt - 60_000L))
        assertTrue(isExclusiveRequestExpired("pending_approval", expiresAt, expiresAt))
        assertFalse("Already approved must never auto-refund", isExclusiveRequestExpired("approved", expiresAt, expiresAt + 10_000L))

        val countdownText = formatCountdownRemaining(expiresAt, createdAt + (2 * 60 * 60 * 1000L))
        assertTrue(countdownText.contains("46h"))
    }

    @Test
    fun `feed uses 10 item cursor pagination and merges deduplicated pages newest first`() {
        assertEquals(10, VideoViewModel.INITIAL_FEED_LIMIT)
        assertEquals(10, VideoViewModel.PAGE_SIZE)

        val page0 = (1..10).map { idx ->
            FirebasePost(
                id = "post_$idx",
                userId = "u_$idx",
                timestamp = Date(1_000_000L + (21 - idx) * 1000L)
            )
        }
        val page1 = (10..19).map { idx -> // intentional boundary overlap post_10
            FirebasePost(
                id = "post_$idx",
                userId = "u_$idx",
                timestamp = Date(1_000_000L + (21 - idx) * 1000L)
            )
        }

        val merged = VideoViewModel.mergePaginatedPosts(mapOf(0 to page0, 1 to page1))
        assertEquals(19, merged.size)
        assertEquals("post_1", merged.first().id)
        assertEquals("post_19", merged.last().id)
    }

    @Test
    fun `github release parser extracts apk asset versionCode and resolves newer public popup`() {
        assertEquals(
            "abhinandan/social-india",
            com.example.utils.AppUpdateManager.normalizeGitHubRepo("https://github.com/abhinandan/social-india/releases")
        )
        assertEquals(
            "abhinandan/social-india",
            com.example.utils.AppUpdateManager.normalizeGitHubRepo("abhinandan/social-india")
        )
        assertEquals("", com.example.utils.AppUpdateManager.normalizeGitHubRepo("invalid"))

        val releaseJson = """
            {
              "tag_name": "v10.1.0-c101-b8",
              "name": "Social India v10.1.0 (Build 101)",
              "html_url": "https://github.com/abhinandan/social-india/releases/tag/v10.1.0-c101-b8",
              "published_at": "2026-11-28T08:00:00Z",
              "body": "Faster reels & instant GitHub updates",
              "assets": [
                {
                  "name": "SocialIndia-v10.1.0-code101.apk",
                  "size": 41943040,
                  "browser_download_url": "https://github.com/abhinandan/social-india/releases/download/v10.1.0-c101-b8/SocialIndia-v10.1.0-code101.apk"
                }
              ]
            }
        """.trimIndent()
        val manifestJson = """
            {
              "versionCode": 101,
              "versionName": "10.1.0",
              "releaseDate": "2026-11-28",
              "mandatory": false,
              "popupEnabled": true,
              "fileSizeMb": 40.0,
              "updateMessage": "New update ready!",
              "notes": "• Faster video loading"
            }
        """.trimIndent()

        val parsed = com.example.utils.AppUpdateManager.parseGitHubReleaseJson(
            releaseJson = releaseJson,
            repoSlug = "abhinandan/social-india",
            manifestJson = manifestJson
        )
        assertNotNull(parsed)
        assertEquals(101, parsed?.versionCode)
        assertEquals("10.1.0", parsed?.versionName)
        assertEquals("GITHUB_RELEASE", parsed?.source)
        assertEquals(40.0, parsed?.fileSizeMb ?: 0.0, 0.01)
        assertTrue(parsed?.popupEnabled == true)
        assertTrue(parsed?.url?.endsWith(".apk") == true)

        val olderFirebase = com.example.model.UpdateInfo(
            versionCode = 100,
            versionName = "10.0.0",
            url = "https://drive.google.com/file/d/abc/view",
            githubRepo = "abhinandan/social-india",
            githubAutoSyncEnabled = true
        )
        val effective = com.example.utils.AppUpdateManager.resolveEffectiveUpdate(
            firebaseConfig = olderFirebase,
            githubConfig = parsed,
            installedVersionCode = 100
        )
        assertEquals(101, effective?.versionCode)
        assertEquals("GITHUB_RELEASE", effective?.source)

        val disabledAutoSync = olderFirebase.copy(githubAutoSyncEnabled = false)
        val effectiveWhenDisabled = com.example.utils.AppUpdateManager.resolveEffectiveUpdate(
            firebaseConfig = disabledAutoSync,
            githubConfig = parsed,
            installedVersionCode = 100
        )
        assertEquals(100, effectiveWhenDisabled?.versionCode)
    }
}
