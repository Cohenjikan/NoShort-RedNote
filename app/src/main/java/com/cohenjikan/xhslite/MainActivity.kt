package com.cohenjikan.xhslite

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import kotlin.math.abs

/**
 * One WebView on xiaohongshu.com/explore with a desktop UA, plus the injection layer in
 * assets/inject.js + inject.css. Long-press the top-right corner of the page for settings.
 */
class MainActivity : ComponentActivity() {
    companion object {
        const val HOME = "https://www.xiaohongshu.com/explore"
        private val SCRIPT_ORIGINS = setOf("https://xiaohongshu.com", "https://*.xiaohongshu.com")
        /** hotspot for settings: this many dp from the top-right corner of the page, held this long */
        private const val HOTSPOT_DP = 64
        private const val HOLD_MS = 900L
        /** desktop sites read navigator.platform too; only settable before the page's own scripts run */
        private const val PLATFORM_SHIM =
            "try{Object.defineProperty(Navigator.prototype,'platform',{get:function(){return 'Win32'},configurable:true});}catch(e){}\n"
    }

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var progress: View
    private lateinit var prefs: Prefs

    private val injectJs by lazy { asset("inject.js") }
    private val injectCss by lazy { asset("inject.css") }
    private val erudaJs by lazy { asset("eruda.min.js") }

    private var startScript: ScriptHandler? = null
    /** prefs as last pushed into the page; onResume re-pushes when the settings page changed them */
    private var appliedCfg = ""

    private var fullscreen: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private val settingsPage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        when (r.resultCode) {
            SettingsActivity.RESULT_RELOAD -> web.reload()
            SettingsActivity.RESULT_HOME -> web.loadUrl(HOME)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pal = palette()

        root = FrameLayout(this).apply { setBackgroundColor(pal.bg) }
        web = WebView(this).apply { setBackgroundColor(pal.bg) }
        progress = View(this).apply {
            setBackgroundColor(pal.accent)
            pivotX = 0f
            scaleX = 0f
        }
        root.addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(progress, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(2)))
        setContentView(root)

        // edge-to-edge: the window draws behind the bars, the page is padded out of them
        // (status bar + camera cutout on top, gesture bar or keyboard at the bottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val i = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(i.left, i.top, i.right, i.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setupWebView()
        setupHotspot()

        onBackPressedDispatcher.addCallback(this) {
            when {
                fullscreen != null -> exitFullscreen()
                web.canGoBack() -> web.goBack()
                else -> finish()
            }
        }

        pushConfig()
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(HOME)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = web.settings
        val chrome = Regex("Chrome/(\\d+)").find(s.userAgentString)?.groupValues?.get(1) ?: "150"
        // desktop Chrome on Windows, same major version as this WebView so features match what the UA claims
        s.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome.0.0.0 Safari/537.36"
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            // Sec-CH-UA-* headers and navigator.userAgentData would otherwise still say "Android, mobile"
            fun brand(name: String, major: String) =
                UserAgentMetadata.BrandVersion.Builder().setBrand(name).setMajorVersion(major).setFullVersion("$major.0.0.0").build()
            WebSettingsCompat.setUserAgentMetadata(
                s,
                UserAgentMetadata.Builder()
                    .setBrandVersionList(listOf(brand("Google Chrome", chrome), brand("Chromium", chrome), brand("Not_A Brand", "24")))
                    .setFullVersion("$chrome.0.0.0")
                    .setPlatform("Windows")
                    .setPlatformVersion("15.0.0")
                    .setArchitecture("x86")
                    .setBitness(64)
                    .setModel("")
                    .setMobile(false)
                    .setWow64(false)
                    .build()
            )
        }
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = false
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        // CSS px follow the system font size like sp do
        s.textZoom = (resources.configuration.fontScale * 100).toInt()
        s.mediaPlaybackRequiresUserGesture = true
        // target=_blank opens in this same WebView, no popups
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return when (UrlPolicy.decide(url, request.isForMainFrame)) {
                    UrlPolicy.Action.LOAD -> false
                    UrlPolicy.Action.BLOCK -> true
                    UrlPolicy.Action.EXTERNAL -> {
                        // only when the user tapped a link; never let a script throw us into the browser
                        if (request.hasGesture()) openExternal(request.url)
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageCommitVisible(view: WebView, url: String?) = inject(view)

            override fun onPageFinished(view: WebView, url: String?) {
                inject(view)
                if (prefs.debug) injectEruda(view)
                CookieManager.getInstance().flush()
            }

            // fires for history.pushState / replaceState too; inject.js hooks those itself, this is a backstop
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (isXhs(url)) view.evaluateJavascript("window.__xl&&__xl.apply('native')", null)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                root.removeView(view)
                view.destroy()
                recreate()
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.scaleX = newProgress / 100f
                if (newProgress >= 100) progress.visibility = View.GONE
            }

            // fullscreen button on video notes
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreen != null) {
                    callback.onCustomViewHidden()
                    return
                }
                fullscreen = view
                fullscreenCallback = callback
                view.setBackgroundColor(Color.BLACK)
                (window.decorView as ViewGroup).addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }

            override fun onHideCustomView() = exitFullscreen()
        }
    }

    private fun exitFullscreen() {
        val v = fullscreen ?: return
        (window.decorView as ViewGroup).removeView(v)
        fullscreen = null
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }

    /** Long-press near the top-right corner opens settings. Only watches: the page still gets every touch. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupHotspot() {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val zone = dp(HOTSPOT_DP)
        var downX = 0f
        var downY = 0f
        val open = Runnable {
            web.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            settingsPage.launch(Intent(this, SettingsActivity::class.java))
        }
        web.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (e.x > web.width - zone && e.y < zone) {
                    downX = e.x
                    downY = e.y
                    web.postDelayed(open, HOLD_MS)
                }
                MotionEvent.ACTION_MOVE -> if (abs(e.x - downX) > slop || abs(e.y - downY) > slop) web.removeCallbacks(open)
                else -> web.removeCallbacks(open)
            }
            false
        }
    }

    // ------------------------------------------------------------------ injection

    private fun asset(name: String) = assets.open(name).bufferedReader().use { it.readText() }

    private fun isXhs(url: String?): Boolean {
        val host = url?.let { Uri.parse(it).host } ?: return false
        return host == "xiaohongshu.com" || host.endsWith(".xiaohongshu.com")
    }

    /** config + css + inject.js as one script; inject.js reads the two globals */
    private fun bundle() =
        "window.__XL_CFG=${prefs.toJson()};window.__XL_CSS=${JSONObject.quote(injectCss)};\n$injectJs"

    private fun inject(view: WebView) {
        if (isXhs(view.url)) view.evaluateJavascript(bundle(), null)
    }

    private fun injectEruda(view: WebView) {
        if (isXhs(view.url)) view.evaluateJavascript("if(!window.eruda){$erudaJs\n;eruda.init();}", null)
    }

    /** (Re)register the document-start script with the current prefs and update the page that is open. */
    private fun pushConfig() {
        val cfg = prefs.toJson()
        if (cfg == appliedCfg) return
        val debugWas = appliedCfg.contains("\"debug\":true")
        appliedCfg = cfg
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            startScript?.remove()
            startScript = WebViewCompat.addDocumentStartJavaScript(web, PLATFORM_SHIM + bundle(), SCRIPT_ORIGINS)
        }
        inject(web)
        if (prefs.debug) injectEruda(web)
        else if (debugWas && isXhs(web.url)) web.evaluateJavascript("window.eruda&&eruda.destroy()", null)
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onResume() {
        super.onResume()
        web.onResume()
        pushConfig()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        web.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
