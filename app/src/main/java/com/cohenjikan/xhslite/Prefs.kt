package com.cohenjikan.xhslite

import android.content.Context
import org.json.JSONObject

/** The settings-page switches. All default off. */
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

    /** what inject.js reads as window.__XL_CFG */
    fun toJson(): String = JSONObject()
        .put("hideFeed", hideFeed)
        .put("hideRelated", hideRelated)
        .put("imageOnly", imageOnly)
        .put("hidePublish", hidePublish)
        .put("debug", debug)
        .toString()
}
