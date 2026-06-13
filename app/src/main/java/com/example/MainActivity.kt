package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

        // Request Microphone/Audio Permissions dynamically for Web Speech API and recording functions
        requestMicrophonePermission()

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

    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )
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

// Native TextToSpeech bridge class to handle Web Speech speechSynthesis in Android OS
class AndroidTTSBridge(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    var onSpeechStateChanged: ((id: String, state: String) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale.getDefault()
            
            // Register standard native Utterance Listener to track speaking progress and trigger callbacks for web synthesis icons
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceId?.let { id ->
                        onSpeechStateChanged?.invoke(id, "start")
                    }
                }

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { id ->
                        onSpeechStateChanged?.invoke(id, "end")
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { id ->
                        onSpeechStateChanged?.invoke(id, "error")
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    utteranceId?.let { id ->
                        onSpeechStateChanged?.invoke(id, "error")
                    }
                }
            })
            Log.d("AndroidTTSBridge", "TTS initialized successfully and UtteranceProgressListener registered with standard locale: ${Locale.getDefault()}")
        } else {
            Log.e("AndroidTTSBridge", "TTS initialization failed status: $status")
        }
    }

    @JavascriptInterface
    fun speak(text: String) {
        speak(text, null, 1.0f, null)
    }

    @JavascriptInterface
    fun speak(text: String, lang: String?) {
        speak(text, lang, 1.0f, null)
    }

    @JavascriptInterface
    fun speak(text: String, lang: String?, rate: Float) {
        speak(text, lang, rate, null)
    }

    @JavascriptInterface
    fun speak(text: String, lang: String?, rate: Float, utteranceId: String?) {
        Log.d("AndroidTTSBridge", "Native speaking requested for: '$text' with lang tag: '$lang', rate: $rate, id: '$utteranceId'")
        if (isInitialized && tts != null) {
            val locale = if (!lang.isNullOrBlank()) {
                try {
                    Locale.forLanguageTag(lang)
                } catch (e: Exception) {
                    Locale.getDefault()
                }
            } else {
                Locale.getDefault()
            }
            tts?.language = locale
            
            // Speed up or set speech rate according to web parameter
            val finalRate = if (rate <= 0f) 1.0f else rate
            tts?.setSpeechRate(finalRate)
            
            val finalId = utteranceId ?: ("UtteranceId_" + System.currentTimeMillis())
            
            // Use Bundle parameter key to ensure compatibility across Xiaomi, Samsung, and other device types
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, finalId)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, finalId)
        } else {
            Log.e("AndroidTTSBridge", "TTS speak called but not fully initialized or tts is null. isInitialized=$isInitialized")
        }
    }

    @JavascriptInterface
    fun stop() {
        Log.d("AndroidTTSBridge", "Native stopping speech output")
        if (isInitialized && tts != null) {
            tts?.stop()
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            Log.d("AndroidTTSBridge", "TTS clean shutdown done")
        } catch (e: Exception) {
            Log.e("AndroidTTSBridge", "Error in TTS shutdown", e)
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
    val context = LocalContext.current
    
    val ttsBridge = remember {
        AndroidTTSBridge(context).apply {
            onSpeechStateChanged = { id, state ->
                // Ensure evaluating scripts runs safely on the UI Main Thread
                webViewRef?.post {
                    val script = when (state) {
                        "start" -> "if (window.AndroidTTSCallbacks) window.AndroidTTSCallbacks.onStart('$id');"
                        "end" -> "if (window.AndroidTTSCallbacks) window.AndroidTTSCallbacks.onEnd('$id');"
                        else -> "if (window.AndroidTTSCallbacks) window.AndroidTTSCallbacks.onError('$id', '$state');"
                    }
                    webViewRef?.evaluateJavascript(script, null)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsBridge.shutdown()
        }
    }

    // Polyfill script to inject native Android TextToSpeech in WebView seamlessly overriding window.speechSynthesis
    val injectTtsScript = """
        (function() {
            if (!window.AndroidTTS) {
                console.warn("AndroidTTS is not bound to window yet.");
                return;
            }

            var activeUtterances = {};

            var customSpeak = function(utterance) {
                if (!utterance) return;
                var text = utterance.text || "";
                var lang = utterance.lang || "en-US";
                var rate = utterance.rate || 1.0;
                var id = utterance.id || "ut_" + Math.random().toString(36).substr(2, 9);
                utterance.id = id;
                activeUtterances[id] = utterance;
                
                // Call multi-arg native bridge safely with speaking rate support for speed configuration
                if (typeof window.AndroidTTS.speak === "function") {
                    try {
                        window.AndroidTTS.speak(text, lang, rate, id);
                    } catch (e) {
                        try {
                            window.AndroidTTS.speak(text, lang, id);
                        } catch (err) {
                            window.AndroidTTS.speak(text);
                        }
                    }
                }
            };

            var customCancel = function() {
                window.AndroidTTS.stop();
            };

            // Comprehensive Voice Database to pass web translation voice match filters
            var voices = [
                { name: "English (US)", lang: "en-US", default: true, localService: true, voiceURI: "en-US" },
                { name: "English (UK)", lang: "en-GB", default: false, localService: true, voiceURI: "en-GB" },
                { name: "Hindi (India)", lang: "hi-IN", default: false, localService: true, voiceURI: "hi-IN" },
                { name: "Urdu (Pakistan)", lang: "ur-PK", default: false, localService: true, voiceURI: "ur-PK" },
                { name: "Spanish (Spain)", lang: "es-ES", default: false, localService: true, voiceURI: "es-ES" },
                { name: "French (France)", lang: "fr-FR", default: false, localService: true, voiceURI: "fr-FR" },
                { name: "German (Germany)", lang: "de-DE", default: false, localService: true, voiceURI: "de-DE" },
                { name: "Arabic (Saudi Arabia)", lang: "ar-SA", default: false, localService: true, voiceURI: "ar-SA" },
                { name: "Bengali (India)", lang: "bn-IN", default: false, localService: true, voiceURI: "bn-IN" },
                { name: "Bengali (Bangladesh)", lang: "bn-BD", default: false, localService: true, voiceURI: "bn-BD" },
                { name: "Italian (Italy)", lang: "it-IT", default: false, localService: true, voiceURI: "it-IT" },
                { name: "Portuguese (Brazil)", lang: "pt-BR", default: false, localService: true, voiceURI: "pt-BR" },
                { name: "Russian (Russia)", lang: "ru-RU", default: false, localService: true, voiceURI: "ru-RU" },
                { name: "Japanese (Japan)", lang: "ja-JP", default: false, localService: true, voiceURI: "ja-JP" },
                { name: "Korean (South Korea)", lang: "ko-KR", default: false, localService: true, voiceURI: "ko-KR" },
                { name: "Chinese (China)", lang: "zh-CN", default: false, localService: true, voiceURI: "zh-CN" },
                { name: "Turkish (Turkey)", lang: "tr-TR", default: false, localService: true, voiceURI: "tr-TR" }
            ];

            var customSpeechSynthesis = {
                speak: customSpeak,
                cancel: customCancel,
                getVoices: function() { return voices; },
                paused: false,
                pending: false,
                speaking: false,
                onvoiceschanged: null
            };

            // Override Pattern 1: Override standard SpeechSynthesis prototype methods
            if (window.SpeechSynthesis) {
                try {
                    Object.defineProperty(SpeechSynthesis.prototype, 'speak', {
                        value: customSpeak,
                        writable: true,
                        configurable: true
                    });
                    Object.defineProperty(SpeechSynthesis.prototype, 'cancel', {
                        value: customCancel,
                        writable: true,
                        configurable: true
                    });
                    Object.defineProperty(SpeechSynthesis.prototype, 'getVoices', {
                        value: function() { return voices; },
                        writable: true,
                        configurable: true
                    });
                } catch(e) {
                    console.error("AndroidTTS: Failed to patch SpeechSynthesis prototype", e);
                }
            }

            // Override Pattern 2: Override window.speechSynthesis object property directly
            try {
                delete window.speechSynthesis;
                Object.defineProperty(window, 'speechSynthesis', {
                    get: function() { return customSpeechSynthesis; },
                    configurable: true,
                    enumerable: true
                });
            } catch (e) {
                try {
                    window.speechSynthesis = customSpeechSynthesis;
                } catch (err) {
                    console.error("AndroidTTS: Hard assignment fail for window.speechSynthesis", err);
                }
            }

            // Ensure window.SpeechSynthesisUtterance exists
            if (!window.SpeechSynthesisUtterance) {
                window.SpeechSynthesisUtterance = function(text) {
                    this.text = text || "";
                    this.lang = "en-US";
                    this.volume = 1.0;
                    this.rate = 1.0;
                    this.pitch = 1.0;
                    this.onstart = null;
                    this.onend = null;
                    this.onerror = null;
                    this.id = "ut_" + Math.random().toString(36).substr(2, 9);
                };
            }

            // Global callback receiver from Android Native TextToSpeech
            window.AndroidTTSCallbacks = {
                onStart: function(id) {
                    var utt = activeUtterances[id];
                    if (utt && typeof utt.onstart === 'function') {
                        try { utt.onstart({ target: utt }); } catch(err) { console.error(err); }
                    }
                },
                onEnd: function(id) {
                    var utt = activeUtterances[id];
                    if (utt) {
                        if (typeof utt.onend === 'function') {
                            try { utt.onend({ target: utt }); } catch(err) { console.error(err); }
                        }
                        delete activeUtterances[id];
                    }
                },
                onError: function(id, msg) {
                    var utt = activeUtterances[id];
                    if (utt) {
                        if (typeof utt.onerror === 'function') {
                            try { utt.onerror({ target: utt, error: msg }); } catch(err) { console.error(err); }
                        }
                        delete activeUtterances[id];
                    }
                }
            };

            // Periodically fire 'voiceschanged' to notify translator pages of loaded voices
            var triggerVoicesChanged = function() {
                if (typeof customSpeechSynthesis.onvoiceschanged === 'function') {
                    try { customSpeechSynthesis.onvoiceschanged(); } catch(e) {}
                }
                window.dispatchEvent(new Event('voiceschanged'));
            };
            triggerVoicesChanged();
            setTimeout(triggerVoicesChanged, 100);
            setTimeout(triggerVoicesChanged, 500);
            setTimeout(triggerVoicesChanged, 1500);

            console.log("AndroidTTS: Enhanced Web SpeechSynthesis polyfilled/overridden successfully!");
        })();
    """.trimIndent()

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
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            // Setup web settings optimized for extreme performance and DOM storage features
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                textZoom = 100
                                mediaPlaybackRequiresUserGesture = false
                                allowFileAccess = true
                                allowContentAccess = true
                                setSupportMultipleWindows(false)
                            }

                            // Force active GPU hardware rendering layers for high performance rendering
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                            // Enable cookie synchronization to speed up network header calculations and API handshakes
                            try {
                                val cookieManager = android.webkit.CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                            } catch (e: Exception) {
                                Log.e("WebView", "Error setting active CookieManager parameters", e)
                            }

                            // Inject custom agent to let the web platform identify wrapper native integrations
                            settings.userAgentString = settings.userAgentString + " AI-Translator-Native-Android"

                            // Register native speech synthesis proxy bridge
                            addJavascriptInterface(ttsBridge, "AndroidTTS")

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    isCurrentlyLoading = true
                                    hasConnectionError = false
                                    // Inject script early to intercept before page starts fully consuming speech API
                                    view?.evaluateJavascript(injectTtsScript, null)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isCurrentlyLoading = false
                                    // Ensure fallback is injected even when DOM is fully settled
                                    view?.evaluateJavascript(injectTtsScript, null)
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
                                            ctx.startActivity(intent)
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

                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    Log.d("MainActivity", "onPermissionRequest requested for: ${request?.resources?.joinToString()}")
                                    try {
                                        request?.grant(request.resources)
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Error granting WebView permission request", e)
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
