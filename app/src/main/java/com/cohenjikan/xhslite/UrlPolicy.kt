package com.cohenjikan.xhslite

import android.net.Uri

/** What shouldOverrideUrlLoading does with a navigation. */
object UrlPolicy {
    enum class Action {
        /** let the WebView load it */
        LOAD,
        /** drop it silently (app-launch schemes, app stores) */
        BLOCK,
        /** hand it to the system browser */
        EXTERNAL,
    }

    /** Xiaohongshu itself, its CDNs, short links and login/captcha pages */
    private val ALLOWED = listOf(
        "xiaohongshu.com",
        "xhscdn.com",
        "xhscdn.net",
        "xhslink.com",
        "xiaohongshu.net",
    )

    private val STORES = listOf(
        "app.mi.com",
        "play.google.com",
        "apps.apple.com",
        "itunes.apple.com",
        "appgallery.huawei.com",
        "appgallery.cloud.huawei.com",
        "sj.qq.com",
        "a.app.qq.com",
        "app.qq.com",
        "app.flyme.cn",
        "store.oppomobile.com",
        "h5coml.vivo.com.cn",
    )

    /** decided from the URL alone; [mainFrame] = false for iframes (captcha frames etc.) */
    fun decide(url: String, mainFrame: Boolean): Action {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase() ?: return Action.BLOCK
        // about:blank, data: and blob: are the page's own business
        if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") return Action.LOAD
        // xhsdiscover://, intent://, market://, weixin:// ... anything that would launch another app
        if (scheme != "http" && scheme != "https") return Action.BLOCK
        val host = uri.host?.lowercase() ?: return Action.BLOCK
        if (matches(host, STORES) || isStorePath(uri)) return Action.BLOCK
        if (matches(host, ALLOWED)) return Action.LOAD
        // third-party pages inside an iframe (captcha, login helpers) stay in the page
        if (!mainFrame) return Action.LOAD
        return Action.EXTERNAL
    }

    private fun matches(host: String, list: List<String>) = list.any { host == it || host.endsWith(".$it") }

    /** download-the-app landing pages served from Xiaohongshu's own domain */
    private fun isStorePath(uri: Uri): Boolean {
        val p = uri.path?.lowercase() ?: return false
        return p.endsWith(".apk") || p.contains("/download") || p.contains("/app-download") || p.contains("/launchapp")
    }
}
