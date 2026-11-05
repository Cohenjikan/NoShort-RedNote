package com.cohenjikan.xhslite

import android.net.Uri

/**
 * The two websites the app shows. They run the same front end:
 * - www.xiaohongshu.com: mainland accounts (+86)
 * - www.rednote.com: the international site (RedNote). Accounts registered with an overseas number
 *   log in here, and their cookies live on .rednote.com. Since 2026-11 xiaohongshu.com also answers
 *   overseas IPs that aren't logged in with a 302 from /explore, /explore/<id> and /user/profile/<id>
 *   to the same path on www.rednote.com. /login and /search_result aren't redirected.
 */
object Sites {
    const val XHS = "www.xiaohongshu.com"
    const val RED = "www.rednote.com"

    /** registrable domains: the site, creator.<domain> (发布), and the rest of their pages */
    val DOMAINS = listOf("xiaohongshu.com", "rednote.com")

    /** origins for the injected script and the settings message channel */
    val ORIGINS = DOMAINS.flatMap { listOf("https://$it", "https://*.$it") }.toSet()

    fun home(host: String) = "https://$host/explore"

    fun isOurs(url: String?): Boolean = url?.let { Uri.parse(it).host }?.lowercase()?.let(::isOursHost) ?: false

    fun isOursHost(host: String) = DOMAINS.any { host == it || host.endsWith(".$it") }

    fun isRednote(host: String) = host == "rednote.com" || host.endsWith(".rednote.com")

    /** the main site (not creator. etc.) a page is on, or null */
    fun mainSiteOf(url: String?): String? = when (url?.let { Uri.parse(it).host }?.lowercase()) {
        XHS, "xiaohongshu.com" -> XHS
        RED, "rednote.com" -> RED
        else -> null
    }
}
