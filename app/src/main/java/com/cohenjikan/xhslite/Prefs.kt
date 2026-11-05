package com.cohenjikan.xhslite

import android.content.Context
import org.json.JSONObject

/** The settings-page switches (all default off), plus which site to open. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("xhslite", Context.MODE_PRIVATE)

    var hideFeed: Boolean
        get() = sp.getBoolean("hideFeed", false)
        set(v) = sp.edit().putBoolean("hideFeed", v).apply()

    var hideRelated: Boolean
        get() = sp.getBoolean("hideRelated", false)
        set(v) = sp.edit().putBoolean("hideRelated", v).apply()

    /** homepage feed shows image notes only (the site's own 只看图文 filter, kept on) */
    var imageOnly: Boolean
        get() = sp.getBoolean("imageOnly", false)
        set(v) = sp.edit().putBoolean("imageOnly", v).apply()

    /** hide the 发布 entry in the bottom bar (shown by default) */
    var hidePublish: Boolean
        get() = sp.getBoolean("hidePublish", false)
        set(v) = sp.edit().putBoolean("hidePublish", v).apply()

    /** inject eruda (in-page devtools) */
    var debug: Boolean
        get() = sp.getBoolean("debug", false)
        set(v) = sp.edit().putBoolean("debug", v).apply()

    /** which site to open: [SITE_AUTO] follows wherever the site last sent us, or [Sites.XHS] / [Sites.RED] */
    var site: String
        get() = sp.getString("site", SITE_AUTO) ?: SITE_AUTO
        set(v) = sp.edit().putString("site", v).apply()

    /** main site the last page was on, for [SITE_AUTO]; empty before the first page */
    var lastSite: String
        get() = sp.getString("lastSite", "") ?: ""
        set(v) = sp.edit().putString("lastSite", v).apply()

    /** the site [MainActivity] opens on start and for 回到首页 */
    val homeSite: String
        get() = when (val s = site) {
            Sites.XHS, Sites.RED -> s
            else -> lastSite.ifEmpty { Sites.XHS }
        }

    companion object {
        const val SITE_AUTO = "auto"
    }

    /** what inject.js reads as window.__XL_CFG */
    fun toJson(): String = JSONObject()
        .put("hideFeed", hideFeed)
        .put("hideRelated", hideRelated)
        .put("imageOnly", imageOnly)
        .put("hidePublish", hidePublish)
        .put("debug", debug)
        .toString()
}
