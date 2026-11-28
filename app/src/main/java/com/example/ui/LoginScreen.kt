package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.viewmodel.AuthState
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.SignUpDraft
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AuthFlowScreen {
    ENTRY,
    LOGIN,
    FORGOT_PASSWORD,
    CREATE_ACCOUNT,
    SUCCESS,
    SECURITY_CHALLENGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(AuthFlowScreen.ENTRY) }
    var createAccountStep by remember { mutableIntStateOf(1) }
    var signUpDraft by remember { mutableStateOf(SignUpDraft()) }
    var createdUsername by remember { mutableStateOf("") }
    var pendingRiskEvaluation by remember { mutableStateOf<com.example.security.RiskEvaluation?>(null) }

    // Auto-advance if authenticated successfully outside create account success
    LaunchedEffect(authState) {
        if (authState is AuthState.Success && currentScreen != AuthFlowScreen.CREATE_ACCOUNT) {
            val uid = com.example.model.RepositoryManager.getCurrentUserId()
            if (uid.isNotEmpty()) {
                val eval = com.example.security.LoginRiskEngine.evaluateLoginRisk(context, uid, true)
                if (eval.requiresChallenge) {
                    pendingRiskEvaluation = eval
                    currentScreen = AuthFlowScreen.SECURITY_CHALLENGE
                    return@LaunchedEffect
                }
            }
            onLoginSuccess()
        }
    }

    val googleAccountChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val chosenEmail = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
        if (!chosenEmail.isNullOrBlank()) {
            authViewModel.signInWithSelectedGoogleEmail(context, chosenEmail)
        }
    }

    val launchNativeGoogleAccountPicker = {
        try {
            val chooseIntent = android.accounts.AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            googleAccountChooserLauncher.launch(chooseIntent)
        } catch (e: Exception) {
            authViewModel.setError("Could not open Google account picker: ${e.localizedMessage ?: "Try Email Login"}")
        }
    }

    val launchGoogleSignIn = {
        coroutineScope.launch {
            try {
                val buildConfigClientId = try {
                    BuildConfig::class.java.getField("WEB_CLIENT_ID").get(null) as? String ?: ""
                } catch (_: Exception) {
                    ""
                }
                val webClientId = if (buildConfigClientId.isNotBlank() && buildConfigClientId != "YOUR_WEB_CLIENT_ID_HERE") {
                    buildConfigClientId
                } else {
                    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                    if (resId != 0) context.getString(resId) else ""
                }

                if (webClientId.isBlank() || webClientId == "YOUR_WEB_CLIENT_ID_HERE") {
                    launchNativeGoogleAccountPicker()
                    return@launch
                }

                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                val result = credentialManager.getCredential(request = request, context = context)
                val credential = result.credential
                if (credential is GoogleIdTokenCredential) {
                    authViewModel.signInWithGoogle(credential.idToken)
                } else {
                    launchNativeGoogleAccountPicker()
                }
            } catch (_: GetCredentialException) {
                launchNativeGoogleAccountPicker()
            } catch (_: Exception) {
                launchNativeGoogleAccountPicker()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
            },
            label = "auth_screen_transition"
        ) { target ->
            when (target) {
                AuthFlowScreen.ENTRY -> {
                    AuthEntryView(
                        onNavigateToLogin = {
                            authViewModel.clearError()
                            currentScreen = AuthFlowScreen.LOGIN
                        },
                        onNavigateToSignUp = {
                            authViewModel.clearError()
                            signUpDraft = SignUpDraft()
                            createAccountStep = 1
                            currentScreen = AuthFlowScreen.CREATE_ACCOUNT
                        },
                        onGoogleSignIn = { launchGoogleSignIn() },
                        onGuestSignIn = { authViewModel.signInAsGuest() },
                        isLoading = authState is AuthState.Loading,
                        errorMessage = (authState as? AuthState.Error)?.message,
                        onCancel = onBack
                    )
                }
                AuthFlowScreen.LOGIN -> {
                    LoginView(
                        authViewModel = authViewModel,
                        authState = authState,
                        onBack = {
                            authViewModel.clearError()
                            currentScreen = AuthFlowScreen.ENTRY
                        },
                        onForgotPassword = {
                            authViewModel.clearError()
                            currentScreen = AuthFlowScreen.FORGOT_PASSWORD
                        },
                        onCreateAccount = {
                            authViewModel.clearError()
                            signUpDraft = SignUpDraft()
                            createAccountStep = 1
                            currentScreen = AuthFlowScreen.CREATE_ACCOUNT
                        },
                        onGoogleSignIn = { launchGoogleSignIn() },
                        onGuestSignIn = { authViewModel.signInAsGuest() }
                    )
                }
                AuthFlowScreen.FORGOT_PASSWORD -> {
                    ForgotPasswordView(
                        authViewModel = authViewModel,
                        onBack = {
                            authViewModel.clearError()
                            currentScreen = AuthFlowScreen.LOGIN
                        }
                    )
                }
                AuthFlowScreen.CREATE_ACCOUNT -> {
                    CreateAccountFlowView(
                        currentStep = createAccountStep,
                        draft = signUpDraft,
                        onUpdateDraft = { updated -> signUpDraft = updated },
                        onStepChange = { nextStep -> createAccountStep = nextStep },
                        onCancelFlow = {
                            authViewModel.clearError()
                            currentScreen = AuthFlowScreen.ENTRY
                        },
                        authViewModel = authViewModel,
                        authState = authState,
                        onAccountCreated = { username ->
                            createdUsername = username
                            currentScreen = AuthFlowScreen.SUCCESS
                        },
                        onNavigateToLogin = {
                            authViewModel.clearError()
                            currentScreen = AuthFlowScreen.LOGIN
                        }
                    )
                }
                AuthFlowScreen.SUCCESS -> {
                    AccountCreatedSuccessView(
                        username = createdUsername,
                        onContinue = onLoginSuccess
                    )
                }
                AuthFlowScreen.SECURITY_CHALLENGE -> {
                    com.example.ui.security.LoginSecurityChallengeView(
                        riskEvaluation = pendingRiskEvaluation ?: com.example.security.RiskEvaluation(),
                        onChallengeSuccess = {
                            onLoginSuccess()
                        },
                        onCancel = {
                            com.example.model.RepositoryManager.auth.signOut()
                            authViewModel.clearError()
                            currentScreen = AuthFlowScreen.LOGIN
                        }
                    )
                }
            }
        }
    }
}

/* =====================================================================
   1. AUTHENTICATION ENTRY SCREEN
   ===================================================================== */
@Composable
fun AuthEntryView(
    onNavigateToLogin: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onGuestSignIn: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Section: App Branding
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            // Social India Application Icon with glowing ambient pulse
            com.example.ui.components.SocialIndiaAnimatedLogo(
                size = 96.dp,
                showGlow = true,
                isAnimated = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Social India",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Connect • Share • Celebrate",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Middle Section: Actions with smooth staggered animations
        var actionsVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { actionsVisible = true }
        val pulseTransition = rememberInfiniteTransition(label = "auth_cta_pulse")
        val createPulseScale by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.022f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "create_account_pulse"
        )
        val googlePulseScale by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.018f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "google_login_pulse"
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
        ) {
            if (!errorMessage.isNullOrEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x22EF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Primary Log In Button
            AnimatedVisibility(
                visible = actionsVisible,
                enter = fadeIn(tween(320)) + slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    initialOffsetY = { it / 2 }
                )
            ) {
                Button(
                    onClick = onNavigateToLogin,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("entry_login_button")
                ) {
                    Text(
                        text = "Log In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Create New Account Button (Animated entrance + subtle glow/scale pulse)
            AnimatedVisibility(
                visible = actionsVisible,
                enter = fadeIn(tween(420, delayMillis = 90)) + slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    initialOffsetY = { it / 2 }
                )
            ) {
                OutlinedButton(
                    onClick = onNavigateToSignUp,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.8.dp,
                        Brush.horizontalGradient(listOf(Color(0xFFFF9933), Color(0xFFFF2A6D)))
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0x18FF9933),
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .scale(if (isLoading) 1f else createPulseScale)
                        .testTag("entry_create_account_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = Color(0xFFFF9933),
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create New Account",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Divider "or"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2E323E))
                Text(
                    text = "  or  ",
                    color = Color(0xFF717684),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2E323E))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Continue with Google (Animated entrance + Google accent border pulse)
            AnimatedVisibility(
                visible = actionsVisible,
                enter = fadeIn(tween(460, delayMillis = 180)) + slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    initialOffsetY = { it / 2 }
                )
            ) {
                Button(
                    onClick = onGoogleSignIn,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF4285F4).copy(alpha = 0.7f),
                                Color(0xFF34A853).copy(alpha = 0.6f),
                                Color(0xFFEA4335).copy(alpha = 0.6f)
                            )
                        )
                    ),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E222D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .scale(if (isLoading) 1f else googlePulseScale)
                        .testTag("entry_google_signin_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = Color(0xFF4285F4),
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Continue with Google",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Guest Login
            TextButton(
                onClick = onGuestSignIn,
                enabled = !isLoading,
                modifier = Modifier.height(44.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFFB0B3BC),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Instant Access as Guest",
                        color = Color(0xFFB0B3BC),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Bottom Footer: Terms & Dismiss
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                text = "By continuing, you agree to Social India's Terms and Privacy Policy.",
                color = Color(0xFF595D69),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Back to Feed",
                color = Color(0xFF8B8F9C),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onCancel() }
                    .padding(8.dp)
            )
        }
    }
}

/* =====================================================================
   2. LOGIN SCREEN
   ===================================================================== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginView(
    authViewModel: AuthViewModel,
    authState: AuthState,
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onGuestSignIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var clientError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    val isLoading = authState is AuthState.Loading
    val serverError = (authState as? AuthState.Error)?.message

    val handleLogin = {
        focusManager.clearFocus()
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            clientError = "Please enter your email address."
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            clientError = "Please enter a valid email address."
        } else if (password.isEmpty()) {
            clientError = "Please enter your password."
        } else {
            clientError = null
            authViewModel.logIn(trimmedEmail, password)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Welcome Back",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Log in to your Social India account",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Error display
            val displayedError = clientError ?: serverError
            if (!displayedError.isNullOrEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x22EF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = displayedError,
                            color = Color(0xFFFCA5A5),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    clientError = null
                    authViewModel.clearError()
                },
                label = { Text("Email", color = Color(0xFF9E9E9E)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = Color(0xFF757575)
                    )
                },
                trailingIcon = {
                    if (email.isNotEmpty()) {
                        IconButton(onClick = { email = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color(0xFF757575)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = Color(0xFFFF9933),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_email_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    clientError = null
                    authViewModel.clearError()
                },
                label = { Text("Password", color = Color(0xFF9E9E9E)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF757575)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = Color(0xFF9E9E9E)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = Color(0xFFFF9933),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { handleLogin() })
            )

            // Forgot Password?
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "Forgot Password?",
                    color = Color(0xFFFF9933),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onForgotPassword() }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Log In Action Button
            Button(
                onClick = { handleLogin() },
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("login_submit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = "Log In",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Divider "or"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2E323E))
                Text(
                    text = "  or  ",
                    color = Color(0xFF717684),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2E323E))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Google Sign-In (Animated)
            val loginPulse = rememberInfiniteTransition(label = "login_cta_pulse")
            val loginGoogleScale by loginPulse.animateFloat(
                initialValue = 1f,
                targetValue = 1.016f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "login_google_pulse"
            )
            Button(
                onClick = onGoogleSignIn,
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF4285F4).copy(alpha = 0.7f),
                            Color(0xFF34A853).copy(alpha = 0.6f),
                            Color(0xFFEA4335).copy(alpha = 0.6f)
                        )
                    )
                ),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E222D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .scale(if (isLoading) 1f else loginGoogleScale)
                    .testTag("login_google_signin_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Color(0xFF4285F4),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Continue with Google",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Guest Sign-In
            OutlinedButton(
                onClick = onGuestSignIn,
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF2E323E)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFFB0B3BC),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Continue as Guest",
                    color = Color(0xFFB0B3BC),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Switch to Sign Up (Animated highlight button)
            Surface(
                onClick = onCreateAccount,
                shape = RoundedCornerShape(14.dp),
                color = Color(0x18FF9933),
                border = BorderStroke(1.dp, Color(0xFFFF9933).copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .scale(if (isLoading) 1f else loginGoogleScale)
                    .testTag("login_create_account_cta")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Don't have an account? ",
                        color = Color(0xFF9E9E9E),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Create New Account",
                        color = Color(0xFFFF9933),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/* =====================================================================
   3. FORGOT PASSWORD SCREEN
   ===================================================================== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordView(
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Reset Password", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E222D)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFFF9933),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Trouble logging in?",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter your registered email and we'll send you a link to reset your password.",
                color = Color(0xFF9E9E9E),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            message?.takeIf { it.isNotEmpty() }?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess) Color(0x2210B981) else Color(0x22EF4444)
                    ),
                    border = BorderStroke(1.dp, if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = msg,
                        color = if (isSuccess) Color(0xFFA7F3D0) else Color(0xFFFCA5A5),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    message = null
                },
                label = { Text("Email", color = Color(0xFF9E9E9E)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = Color(0xFF757575)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFF9933),
                    unfocusedBorderColor = Color(0xFF2E323E),
                    focusedContainerColor = Color(0xFF14161E),
                    unfocusedContainerColor = Color(0xFF14161E)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    val trimmed = email.trim()
                    if (trimmed.isEmpty()) {
                        isSuccess = false
                        message = "Please enter your email address."
                        return@Button
                    }
                    isSending = true
                    authViewModel.sendPasswordReset(trimmed) { success, resultMessage ->
                        isSending = false
                        isSuccess = success
                        message = resultMessage
                    }
                },
                enabled = !isSending,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = "Send Reset Link",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onBack) {
                Text(
                    text = "Back to Log In",
                    color = Color(0xFFFF9933),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/* =====================================================================
   4. CREATE ACCOUNT MULTI-STEP FLOW (7 STEPS)
   ===================================================================== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountFlowView(
    currentStep: Int,
    draft: SignUpDraft,
    onUpdateDraft: (SignUpDraft) -> Unit,
    onStepChange: (Int) -> Unit,
    onCancelFlow: () -> Unit,
    authViewModel: AuthViewModel,
    authState: AuthState,
    onAccountCreated: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val onBackClick = {
        focusManager.clearFocus()
        if (currentStep > 1) {
            onStepChange(currentStep - 1)
        } else {
            onCancelFlow()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Step $currentStep of 7",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = onCancelFlow) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )

                // Progress Bar
                LinearProgressIndicator(
                    progress = { currentStep / 7f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFFFF9933),
                    trackColor = Color(0xFF1E222D)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            when (currentStep) {
                1 -> Step1Email(
                    draft = draft,
                    onUpdateDraft = onUpdateDraft,
                    onNext = { onStepChange(2) },
                    authViewModel = authViewModel,
                    onNavigateToLogin = onNavigateToLogin
                )
                2 -> Step2Password(
                    draft = draft,
                    onUpdateDraft = onUpdateDraft,
                    onNext = { onStepChange(3) }
                )
                3 -> Step3Name(
                    draft = draft,
                    onUpdateDraft = onUpdateDraft,
                    onNext = { onStepChange(4) }
                )
                4 -> Step4Username(
                    draft = draft,
                    onUpdateDraft = onUpdateDraft,
                    onNext = { onStepChange(5) },
                    authViewModel = authViewModel
                )
                5 -> Step5Photo(
                    draft = draft,
                    onUpdateDraft = onUpdateDraft,
                    onNext = { onStepChange(6) },
                    onSkip = { onStepChange(6) }
                )
                6 -> Step6About(
                    draft = draft,
                    onUpdateDraft = onUpdateDraft,
                    onNext = { onStepChange(7) }
                )
                7 -> Step7Review(
                    draft = draft,
                    onEditStep = { step -> onStepChange(step) },
                    onCreateAccount = {
                        authViewModel.signUpWithFullProfile(
                            draft = draft,
                            context = context,
                            onSuccess = { username -> onAccountCreated(username) },
                            onError = { /* Error state captured by authState */ }
                        )
                    },
                    isLoading = authState is AuthState.Loading,
                    errorMessage = (authState as? AuthState.Error)?.message,
                    onBack = { onStepChange(6) }
                )
            }
        }
    }
}

/* ---------------- STEP 1: EMAIL ---------------- */
@Composable
fun Step1Email(
    draft: SignUpDraft,
    onUpdateDraft: (SignUpDraft) -> Unit,
    onNext: () -> Unit,
    authViewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf(draft.email) }
    var isChecking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isAlreadyRegistered by remember { mutableStateOf(false) }

    val handleNext = {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) {
            errorMessage = "Email is required."
            isAlreadyRegistered = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            errorMessage = "Please enter a valid email address."
            isAlreadyRegistered = false
        } else {
            isChecking = true
            errorMessage = null
            authViewModel.checkEmailAvailability(trimmed) { available, error ->
                isChecking = false
                if (available) {
                    onUpdateDraft(draft.copy(email = trimmed))
                    onNext()
                } else {
                    errorMessage = error ?: "This email is already registered."
                    isAlreadyRegistered = error?.contains("already registered", ignoreCase = true) == true
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp)
    ) {
        Text(
            text = "Create your Social India account",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Enter the email you want to use to register your account.",
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        errorMessage?.takeIf { it.isNotEmpty() }?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x22EF4444)),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = err,
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp
                    )
                    if (isAlreadyRegistered) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap here to Log In instead →",
                            color = Color(0xFFFF9933),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onNavigateToLogin() }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
                isAlreadyRegistered = false
            },
            label = { Text("Email address", color = Color(0xFF9E9E9E)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = Color(0xFF757575)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFFF9933),
                unfocusedBorderColor = Color(0xFF2E323E),
                focusedContainerColor = Color(0xFF14161E),
                unfocusedContainerColor = Color(0xFF14161E)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("signup_step1_email"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { handleNext() })
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { handleNext() },
            enabled = !isChecking,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("signup_step1_next")
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "Next",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/* ---------------- STEP 2: PASSWORD ---------------- */
@Composable
fun Step2Password(
    draft: SignUpDraft,
    onUpdateDraft: (SignUpDraft) -> Unit,
    onNext: () -> Unit
) {
    var password by remember { mutableStateOf(draft.password) }
    var confirmPassword by remember { mutableStateOf(draft.confirmPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Password strength calculation
    val lengthOk = password.length >= 6
    val hasLetters = password.any { it.isLetter() }
    val hasDigits = password.any { it.isDigit() }
    val strength = when {
        password.isEmpty() -> 0
        password.length < 6 -> 1 // Weak
        lengthOk && (hasLetters xor hasDigits) -> 2 // Medium
        lengthOk && hasLetters && hasDigits -> 3 // Strong
        else -> 1
    }

    val handleNext = {
        if (password.isEmpty()) {
            errorMessage = "Password is required."
        } else if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters."
        } else if (confirmPassword.isEmpty()) {
            errorMessage = "Please confirm your password."
        } else if (password != confirmPassword) {
            errorMessage = "Passwords do not match."
        } else {
            errorMessage = null
            onUpdateDraft(draft.copy(password = password, confirmPassword = confirmPassword))
            onNext()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp)
    ) {
        Text(
            text = "Create a password",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Choose a secure password with at least 6 characters.",
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        errorMessage?.takeIf { it.isNotEmpty() }?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x22EF4444)),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = err,
                    color = Color(0xFFFCA5A5),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Password", color = Color(0xFF9E9E9E)) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFF757575))
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password",
                        tint = Color(0xFF9E9E9E)
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFFF9933),
                unfocusedBorderColor = Color(0xFF2E323E),
                focusedContainerColor = Color(0xFF14161E),
                unfocusedContainerColor = Color(0xFF14161E)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("signup_step2_password"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
        )

        // Password strength meter
        if (password.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val color1 = if (strength >= 1) Color(0xFFEF4444) else Color(0xFF2E323E)
                val color2 = if (strength >= 2) Color(0xFFF59E0B) else Color(0xFF2E323E)
                val color3 = if (strength >= 3) Color(0xFF10B981) else Color(0xFF2E323E)

                Box(modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color1))
                Box(modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color2))
                Box(modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color3))
            }

            val strengthLabel = when (strength) {
                1 -> "Weak (min 6 characters required)"
                2 -> "Medium (add numbers to make it stronger)"
                3 -> "Strong password"
                else -> ""
            }
            Text(
                text = strengthLabel,
                color = when (strength) {
                    1 -> Color(0xFFEF4444)
                    2 -> Color(0xFFF59E0B)
                    3 -> Color(0xFF10B981)
                    else -> Color.Gray
                },
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Confirm Password field
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                errorMessage = null
            },
            label = { Text("Confirm Password", color = Color(0xFF9E9E9E)) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFF757575))
            },
            trailingIcon = {
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(
                        imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password",
                        tint = Color(0xFF9E9E9E)
                    )
                }
            },
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFFF9933),
                unfocusedBorderColor = Color(0xFF2E323E),
                focusedContainerColor = Color(0xFF14161E),
                unfocusedContainerColor = Color(0xFF14161E)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("signup_step2_confirm_password"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { handleNext() })
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { handleNext() },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("signup_step2_next")
        ) {
            Text(
                text = "Next",
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/* ---------------- STEP 3: NAME ---------------- */
@Composable
fun Step3Name(
    draft: SignUpDraft,
    onUpdateDraft: (SignUpDraft) -> Unit,
    onNext: () -> Unit
) {
    var fullName by remember { mutableStateOf(draft.fullName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val handleNext = {
        val trimmed = fullName.trim()
        if (trimmed.isEmpty()) {
            errorMessage = "Please enter your name."
        } else if (trimmed.length < 2) {
            errorMessage = "Name must be at least 2 characters."
        } else {
            errorMessage = null
            onUpdateDraft(draft.copy(fullName = trimmed))
            onNext()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp)
    ) {
        Text(
            text = "What's your full name?",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "This name will appear on your profile so friends can easily find and recognize you.",
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        errorMessage?.takeIf { it.isNotEmpty() }?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x22EF4444)),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = err,
                    color = Color(0xFFFCA5A5),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        OutlinedTextField(
            value = fullName,
            onValueChange = {
                fullName = it
                errorMessage = null
            },
            label = { Text("Full Name", color = Color(0xFF9E9E9E)) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color(0xFF757575))
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFFF9933),
                unfocusedBorderColor = Color(0xFF2E323E),
                focusedContainerColor = Color(0xFF14161E),
                unfocusedContainerColor = Color(0xFF14161E)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("signup_step3_name"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { handleNext() })
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { handleNext() },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("signup_step3_next")
        ) {
            Text(
                text = "Next",
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/* ---------------- STEP 4: USERNAME ---------------- */
@Composable
fun Step4Username(
    draft: SignUpDraft,
    onUpdateDraft: (SignUpDraft) -> Unit,
    onNext: () -> Unit,
    authViewModel: AuthViewModel
) {
    var username by remember { mutableStateOf(draft.username) }
    var isChecking by remember { mutableStateOf(false) }
    var availabilityStatus by remember { mutableStateOf<String?>(null) }
    var isAvailable by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // Live availability check on typing
    LaunchedEffect(username) {
        val trimmed = username.trim().lowercase()
        debounceJob?.cancel()
        if (trimmed.length < 3) {
            availabilityStatus = null
            isAvailable = false
            return@LaunchedEffect
        }
        debounceJob = coroutineScope.launch {
            delay(500)
            isChecking = true
            authViewModel.checkUsernameAvailability(trimmed) { available, errorMsg ->
                isChecking = false
                isAvailable = available
                availabilityStatus = if (available) {
                    "@$trimmed is available!"
                } else {
                    errorMsg ?: "Username is not available."
                }
            }
        }
    }

    val handleNext = {
        val trimmed = username.trim().lowercase()
        if (trimmed.isEmpty()) {
            errorMessage = "Username is required."
        } else if (trimmed.length < 3) {
            errorMessage = "Username must be at least 3 characters."
        } else if (trimmed.length > 30) {
            errorMessage = "Username must be 30 characters or less."
        } else {
            isChecking = true
            errorMessage = null
            authViewModel.checkUsernameAvailability(trimmed) { available, errorMsg ->
                isChecking = false
                isAvailable = available
                if (available) {
                    onUpdateDraft(draft.copy(username = trimmed))
                    onNext()
                } else {
                    errorMessage = errorMsg ?: "That username is already taken. Please try another."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp)
    ) {
        Text(
            text = "Choose a username",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Pick a unique @username. You can change this anytime later.",
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        errorMessage?.takeIf { it.isNotEmpty() }?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x22EF4444)),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = err,
                    color = Color(0xFFFCA5A5),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it.replace(" ", "").lowercase()
                errorMessage = null
            },
            label = { Text("Username", color = Color(0xFF9E9E9E)) },
            leadingIcon = {
                Text(
                    text = "@",
                    color = Color(0xFFFF9933),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                )
            },
            trailingIcon = {
                if (isChecking) {
                    CircularProgressIndicator(
                        color = Color(0xFFFF9933),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else if (isAvailable && username.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Available",
                        tint = Color(0xFF10B981)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = if (isAvailable) Color(0xFF10B981) else Color(0xFFFF9933),
                unfocusedBorderColor = Color(0xFF2E323E),
                focusedContainerColor = Color(0xFF14161E),
                unfocusedContainerColor = Color(0xFF14161E)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("signup_step4_username"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { handleNext() })
        )

        // Availability feedback label
        val statusText = availabilityStatus
        if (!statusText.isNullOrEmpty() && !isChecking) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusText,
                color = if (isAvailable) Color(0xFF10B981) else Color(0xFFEF4444),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { handleNext() },
            enabled = !isChecking && (isAvailable || username.trim().length >= 3),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("signup_step4_next")
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "Next",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/* ---------------- STEP 5: PROFILE PHOTO ---------------- */
@Composable
fun Step5Photo(
    draft: SignUpDraft,
    onUpdateDraft: (SignUpDraft) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    var photoUri by remember { mutableStateOf<Uri?>(draft.photoUri) }
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUri = uri
            onUpdateDraft(draft.copy(photoUri = uri))
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val tempFile = java.io.File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
                tempFile.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                val uri = Uri.fromFile(tempFile)
                photoUri = uri
                onUpdateDraft(draft.copy(photoUri = uri))
            } catch (e: Exception) {
                Log.e("Step5Photo", "Failed to process camera picture", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Add a profile photo",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Add a photo so your friends can recognize you.",
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Large circular preview
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .border(2.dp, Color(0xFFFF9933), CircleShape)
                .background(Color(0xFF1A1D26))
                .clickable {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Selected Profile Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF757575),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Add Photo", color = Color(0xFFFF9933), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Options: Camera & Gallery
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Camera option
            OutlinedButton(
                onClick = { cameraLauncher.launch(null) },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Color(0xFFFF9933)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFFFF9933), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Camera", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            // Gallery option
            OutlinedButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Color(0xFFFF9933)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFFFF9933), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Gallery", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (photoUri != null) {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = {
                    photoUri = null
                    onUpdateDraft(draft.copy(photoUri = null))
                }
            ) {
                Text("Remove Photo", color = Color(0xFFEF4444), fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Next button
        Button(
            onClick = {
                onUpdateDraft(draft.copy(photoUri = photoUri))
                onNext()
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("signup_step5_next")
        ) {
            Text(
                text = if (photoUri != null) "Next" else "Skip for Now",
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/* ---------------- STEP 6: ABOUT YOU (ADDITIONAL INFO) ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step6About(
    draft: SignUpDraft,
    onUpdateDraft: (SignUpDraft) -> Unit,
    onNext: () -> Unit
) {
    var bio by remember { mutableStateOf(draft.bio) }
    var selectedGender by remember { mutableStateOf(draft.gender) }
    val genderOptions = listOf("Female", "Male", "Non-binary", "Prefer not to say")

    val handleNext = {
        onUpdateDraft(draft.copy(bio = bio.trim(), gender = selectedGender))
        onNext()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp)
    ) {
        Text(
            text = "Tell us a bit about you",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Add a short bio and select your gender to personalize your profile.",
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Bio Field
        Text("Bio", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = bio,
            onValueChange = { if (it.length <= 150) bio = it },
            placeholder = { Text("What makes you tick? Share your passions or interests...", color = Color(0xFF6B7280)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFFF9933),
                unfocusedBorderColor = Color(0xFF2E323E),
                focusedContainerColor = Color(0xFF14161E),
                unfocusedContainerColor = Color(0xFF14161E)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .testTag("signup_step6_bio"),
            maxLines = 4
        )

        Text(
            text = "${bio.length}/150",
            color = Color(0xFF717684),
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, end = 4.dp),
            textAlign = TextAlign.End
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Gender Selection
        Text("Gender", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genderOptions.take(2).forEach { option ->
                val isSelected = selectedGender == option
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedGender = option },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF9933),
                        selectedLabelColor = Color.Black,
                        containerColor = Color(0xFF1A1D26),
                        labelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genderOptions.drop(2).forEach { option ->
                val isSelected = selectedGender == option
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedGender = option },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF9933),
                        selectedLabelColor = Color.Black,
                        containerColor = Color(0xFF1A1D26),
                        labelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = { handleNext() },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("signup_step6_next")
        ) {
            Text(
                text = "Next",
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/* ---------------- STEP 7: REVIEW ACCOUNT ---------------- */
@Composable
fun Step7Review(
    draft: SignUpDraft,
    onEditStep: (Int) -> Unit,
    onCreateAccount: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp)
    ) {
        Text(
            text = "Review your account",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Make sure your details look good before joining Social India.",
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!errorMessage.isNullOrEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x22EF4444)),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFCA5A5),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Summary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14161E)),
            border = BorderStroke(1.dp, Color(0xFF2E323E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header: Avatar + Name + Username
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF252936)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (draft.photoUri != null) {
                            AsyncImage(
                                model = draft.photoUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFFB0B3BC),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = draft.fullName.ifEmpty { "User" },
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${draft.username}",
                            color = Color(0xFFFF9933),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    TextButton(onClick = { onEditStep(3) }) {
                        Text("Edit", color = Color(0xFFFF9933), fontSize = 13.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF222632))

                // Detail Rows
                ReviewDetailRow(
                    label = "Email",
                    value = draft.email,
                    onEdit = { onEditStep(1) }
                )

                ReviewDetailRow(
                    label = "Gender",
                    value = draft.gender,
                    onEdit = { onEditStep(6) }
                )

                if (draft.bio.isNotEmpty()) {
                    ReviewDetailRow(
                        label = "Bio",
                        value = draft.bio,
                        onEdit = { onEditStep(6) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "By tapping Create Account, you confirm that your information is accurate and agree to Social India community guidelines.",
            color = Color(0xFF717684),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Create Account Action Button
        Button(
            onClick = onCreateAccount,
            enabled = !isLoading,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9933)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("signup_step7_submit")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = "Create Account",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            enabled = !isLoading,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF2E323E)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Back to Details", color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
fun ReviewDetailRow(label: String, value: String, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = Color(0xFF8B8F9C), fontSize = 12.sp)
            Text(
                text = value,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
        }
        Text(
            text = "Edit",
            color = Color(0xFFFF9933),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { onEdit() }
                .padding(4.dp)
        )
    }
}

/* =====================================================================
   5. ACCOUNT CREATED SUCCESS SCREEN
   ===================================================================== */
@Composable
fun AccountCreatedSuccessView(
    username: String,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing celebration badge
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color(0x2210B981))
                .border(2.dp, Color(0xFF10B981), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Success",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Welcome to Social India!",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "@$username",
            color = Color(0xFFFF9933),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Your account is ready. Discover reels, share stories, connect with friends, and celebrate India's vibrant community.",
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onContinue,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("success_continue_button")
        ) {
            Text(
                text = "Continue to Social India",
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
