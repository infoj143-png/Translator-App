package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private lateinit var networkMonitor: NetworkMonitor
    private var mInterstitialAd: InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize AdMob Ads SDK (Safe for Google Play Store compliance)
        try {
            MobileAds.initialize(this) { status ->
                Log.d("MainActivity", "AdMob initialized successfully: $status")
                loadInterstitialAd()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing AdMob", e)
        }

        // 2. Initialize Network Monitor
        networkMonitor = NetworkMonitor(applicationContext)

        setContent {
            MyApplicationTheme {
                var showSplash by remember { mutableStateOf(true) }
                val isOnline by networkMonitor.isConnected.collectAsState()

                // Display splash screen, then fade out standardly after brief period
                LaunchedEffect(Unit) {
                    delay(2500) // Beautiful 2.5 seconds showcase of branding & loading
                    showSplash = false
                    // Show loaded interstitial ad with modern transition sequence
                    showInterstitialAd()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Pre-render the core web application underneath the splash
                    MainScreenContent(
                        isOnline = isOnline,
                        onBackPress = { finish() }
                    )

                    // Splash Screen Animated overlay
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = fadeIn(animationSpec = twenSpec()),
                        exit = fadeOut(animationSpec = twenSpec())
                    ) {
                        SplashScreenLayout()
                    }
                }
            }
        }
    }

    private fun twenSpec() = tween<Float>(durationMillis = 600)

    // Load AdMob Interstitial Ad
    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        // AdMob standardized test interstitial ad unit ID
        InterstitialAd.load(
            this,
            "ca-app-pub-3940256099942544/1033173712",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    Log.d("MainActivity", "Interstitial loaded.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e("MainActivity", "Interstitial ad failed representation: ${loadAdError.message}")
                    mInterstitialAd = null
                }
            }
        )
    }

    // Show AdMob Interstitial Ad
    private fun showInterstitialAd() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
            mInterstitialAd = null // Ensure shown only once
            loadInterstitialAd() // Pre-load next
        } else {
            Log.d("MainActivity", "Interstitial ad not ready yet.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.unregister()
    }
}

// Network state manager flow
class NetworkMonitor(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isConnected.value = true
        }

        override fun onLost(network: Network) {
            _isConnected.value = false
        }
    }

    init {
        // Initial state assessment
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        _isConnected.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Error unregistering connectivity callback", e)
        }
    }
}

// Beautiful Premium Splash Interface
@Composable
fun SplashScreenLayout() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A) // Sleek slate space dark background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated logo presentation container
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF3B82F6), Color(0xFFEC4899))
                        )
                    )
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_icon_1781203847150),
                        contentDescription = "AI Translator Icon",
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("splash_logo")
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "AI Translator",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("splash_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Instant. Adaptive. High-Fidelity.",
                color = Color(0xFF94A3B8),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = Color(0xFF3B82F6),
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

// Core webview & interactive overlay wrapper
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainScreenContent(
    isOnline: Boolean,
    onBackPress: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageLoadingProgress by remember { mutableFloatStateOf(0f) }
    var isCurrentlyLoading by remember { mutableStateOf(true) }
    var hasConnectionError by remember { mutableStateOf(false) }

    val homeUrl = "https://translator-lovat-six.vercel.app/"

    // Back navigation management inside Compose
    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            // AdMob test Banner integration pinned securely at the bottom
            AdMobBannerContainer()
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F172A))
        ) {
            if (!isOnline || hasConnectionError) {
                // Graceful Offline Error state card
                OfflineStateLayout(
                    onRetry = {
                        hasConnectionError = false
                        webViewRef?.reload()
                    }
                )
            } else {
                // Native high-performance Android WebView
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_webview"),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            // Setup web settings optimized for performance and DOM storage features
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                textZoom = 100
                            }

                            // Inject custom agent to let the web platform identify wrapper native integrations
                            settings.userAgentString = settings.userAgentString + " AI-Translator-Native-Android"

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    isCurrentlyLoading = true
                                    hasConnectionError = false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isCurrentlyLoading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    // Set error only if it's the main url page failing
                                    if (request?.isForMainFrame == true) {
                                        hasConnectionError = true
                                        isCurrentlyLoading = false
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    // Keep same domain inside WebView, open out-of-domain connections in user's browser
                                    return if (url.contains("translator-lovat-six.vercel.app") || url.startsWith("file:///")) {
                                        false
                                    } else {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Log.e("WebViewClient", "Error launching external URL intent", e)
                                        }
                                        true
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageLoadingProgress = newProgress / 100f
                                    if (newProgress == 100) {
                                        isCurrentlyLoading = false
                                    }
                                }
                            }

                            // Start initial page request execution
                            loadUrl(homeUrl)
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        // Dynamically update view state references
                        webViewRef = webView
                    },
                    onRelease = { webView ->
                        webView.destroy()
                    }
                )

                // Slick Progress banner on top of web loading progress changes
                if (isCurrentlyLoading) {
                    LinearProgressIndicator(
                        progress = pageLoadingProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Color(0xFF3B82F6),
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    }
}

// Gorgeous styling of Offline representation cards
@Composable
fun OfflineStateLayout(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("offline_layout"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "No Internet Icon",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No Connection Available",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Please check your network settings and tap retry to reload the translation workspace.",
            color = Color(0xFF94A3B8),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3B82F6),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(48.dp)
                .testTag("retry_connection_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Retry Button Icon"
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Retry",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Google Play Store compliant AdMob Banner View container
@Composable
fun AdMobBannerContainer() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        val context = LocalContext.current
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            factory = { ctx ->
                AdView(ctx).apply {
                    adUnitId = "ca-app-pub-3940256099942544/6300978111" // AdMob sample test unit banner ID
                    setAdSize(AdSize.BANNER)
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            super.onAdFailedToLoad(loadAdError)
                            Log.e("AdMobBanner", "Failed to load banner ad: ${loadAdError.message}")
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}

// Deprecated fallback Greeting function to prevent breaking static Template JUnit Tests
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        color = Color.White,
        modifier = modifier
            .background(Color(0xFF0F172A))
            .padding(16.dp)
            .testTag("greeting_text")
    )
}
