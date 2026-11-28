package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.AdminPanelScreen
import com.example.ui.BroadcastChannelsScreen
import com.example.ui.DailyRewardsAndLuckyBoxScreen
import com.example.ui.LoginScreen
import com.example.ui.MonetizationScreen
import com.example.ui.PrivacySettingsScreen
import com.example.ui.SavedCollectionsScreen
import com.example.ui.SettingsScreen
import com.example.ui.WalletScreen
import com.example.ui.security.AccountSecurityScreen
import com.example.ui.templates.TemplateBrowserScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AuthViewModel
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppCrashAuditTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainActivity_launches_and_renders_without_crash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
        assert(activity != null && !activity.isFinishing)
    }

    @Test
    fun authViewModel_instantiates_safely() {
        val vm = AuthViewModel()
        assertNotNull(vm.authState.value)
    }

    @Test
    fun loginScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                LoginScreen(onLoginSuccess = {}, onBack = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun templateBrowserScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                TemplateBrowserScreen(
                    initialTemplateId = null,
                    onClose = {},
                    onTemplateReadyForEditor = { _, _, _ -> }
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun privacySettingsScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                PrivacySettingsScreen(onBack = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun dailyRewardsScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                DailyRewardsAndLuckyBoxScreen(onBack = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun settingsScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(onBack = {}, onLogout = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun monetizationScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MonetizationScreen(onBack = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun walletScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                WalletScreen(onBack = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun accountSecurityScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AccountSecurityScreen(onBack = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun adminPanelScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AdminPanelScreen(onBack = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun broadcastChannelsScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BroadcastChannelsScreen(
                    currentUserId = "test_user",
                    currentUsername = "tester",
                    currentUserAvatar = "",
                    onNavigateBack = {},
                    onOpenChannel = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun savedCollectionsScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SavedCollectionsScreen(
                    currentUserId = "test_user",
                    onNavigateBack = {},
                    onOpenPost = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun homeFeedScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.HomeFeedScreen(
                    viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                    onNavigate = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        composeTestRule.waitForIdle()
    }

    @Test
    fun profileScreen_ownProfile_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.ProfileScreen(
                    profileUserId = null,
                    onNavigateToEditProfile = {},
                    onNavigateToSettings = {},
                    onNavigateToReel = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        composeTestRule.waitForIdle()
    }

    @Test
    fun profileScreen_otherUser_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.ProfileScreen(
                    profileUserId = "creator_test_uid_123",
                    onNavigateToEditProfile = {},
                    onNavigateToSettings = {},
                    onNavigateToReel = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        composeTestRule.waitForIdle()
    }

    @Test
    fun discoverScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.DiscoverScreen(
                    onNavigateToInbox = {},
                    onNavigateToHome = {},
                    onNavigateToProfile = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        composeTestRule.waitForIdle()
    }

    @Test
    fun videoFeedScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.VideoFeedScreen()
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        composeTestRule.waitForIdle()
    }

    @Test
    fun inboxScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.InboxScreen()
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        composeTestRule.waitForIdle()
    }

    @Test
    fun uploadScreen_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.UploadScreen()
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        composeTestRule.waitForIdle()
    }

    @Test
    fun shortsApp_homeToProfileNavigation_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        com.example.ui.HomeFeedScreen(
                            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                            onNavigate = { route -> navController.navigate(route) }
                        )
                    }
                    composable("profile") {
                        com.example.ui.ProfileScreen(
                            profileUserId = null,
                            onNavigateToEditProfile = {},
                            onNavigateToSettings = {},
                            onNavigateToReel = {},
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("profile/{userId}") { entry ->
                        com.example.ui.ProfileScreen(
                            profileUserId = entry.arguments?.getString("userId"),
                            onNavigateToEditProfile = {},
                            onNavigateToSettings = {},
                            onNavigateToReel = {},
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(300)
                    navController.navigate("profile")
                    kotlinx.coroutines.delay(300)
                    navController.navigate("profile/creator_abc")
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        composeTestRule.waitForIdle()
    }

    @Test
    fun shortsApp_fullRoot_renders_without_crash() {
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.ShortsApp()
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        composeTestRule.waitForIdle()
    }

    @Test
    fun bottomNavigationBar_tappingAllTabsAndProfile_without_crash() {
        composeTestRule.mainClock.autoAdvance = false
        var lastNavigatedRoute = "home"
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.BottomNavigationBar(
                    currentRoute = lastNavigatedRoute,
                    userAvatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde",
                    onNavigate = { route -> lastNavigatedRoute = route },
                    onOpenAccountSwitcher = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Discover")).performClick()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Reels")).performClick()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Profile Picture")).performClick()
        composeTestRule.mainClock.advanceTimeBy(300)
        org.junit.Assert.assertEquals("profile", lastNavigatedRoute)
    }

    @Test
    fun homeVideoPostItem_rendersAndClicksProfile_without_crash() {
        composeTestRule.mainClock.autoAdvance = false
        var profileClicked = false
        val sampleVideo = com.example.model.Video(
            id = "post_real_101",
            userId = "creator_uid_999",
            user = com.example.model.User(
                id = "creator_uid_999",
                username = "ananya_sharma",
                avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde",
                isVerified = true
            ),
            videoUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg",
            videoUrls = listOf("https://res.cloudinary.com/demo/image/upload/sample.jpg"),
            mediaType = "image",
            description = "Exploring Jaipur today! #travel @rahul",
            caption = "Exploring Jaipur today! #travel @rahul",
            musicTrack = "Original Audio",
            likesCount = 128,
            commentsCount = 14,
            sharesCount = 5
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.HomeVideoPostItem(
                    video = sampleVideo,
                    isPlaying = false,
                    onLike = {},
                    onCommentClick = {},
                    onShareClick = {},
                    onRepostClick = {},
                    onOptionsClick = {},
                    onProfileClick = { profileClicked = true },
                    onReelsClick = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Avatar")).performClick()
        composeTestRule.mainClock.advanceTimeBy(300)
        org.junit.Assert.assertTrue(profileClicked)
    }

    @Test
    fun profileScreen_withCachedUserAndPosts_switchesTabs_without_crash() {
        composeTestRule.mainClock.autoAdvance = false
        val targetUid = "creator_populated_uid"
        com.example.ui.GlobalState.cacheProfileUser(
            targetUid,
            com.example.model.User(
                id = targetUid,
                username = "rohit_verma",
                displayName = "Rohit Verma",
                bio = "Filmmaker & Creator | Mumbai",
                avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde",
                isVerified = true,
                followerCount = 4200,
                followingCount = 180,
                coins = 500L
            )
        )
        com.example.ui.GlobalState.cacheProfilePosts(
            targetUid,
            listOf(
                com.example.model.FirebasePost(
                    id = "p_img_1",
                    userId = targetUid,
                    username = "rohit_verma",
                    postUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg",
                    mediaType = "image",
                    caption = "First photo post",
                    likesCount = 95,
                    viewsCount = 410
                ),
                com.example.model.FirebasePost(
                    id = "p_vid_2",
                    userId = targetUid,
                    username = "rohit_verma",
                    postUrl = "https://res.cloudinary.com/demo/video/upload/dog.mp4",
                    thumbnailUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg",
                    mediaType = "video",
                    caption = "First reel video",
                    likesCount = 240,
                    viewsCount = 1920
                )
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.ProfileScreen(
                    profileUserId = targetUid,
                    onNavigateToEditProfile = {},
                    onNavigateToSettings = {},
                    onNavigateToReel = {},
                    onNavigateToPostDetail = { _, _ -> },
                    onBack = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)

        // Switch across profile tabs (Reels, Mentions, Posts)
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Reels")).performClick()
        composeTestRule.mainClock.advanceTimeBy(500)

        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Mentions")).performClick()
        composeTestRule.mainClock.advanceTimeBy(500)

        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Posts")).performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
    }
}

