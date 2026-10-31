package com.cohenjikan.xhslite

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Hidden settings page (long-press the top-right corner of the page). Plain Views, Claude-Remote's palette and type. */
class SettingsActivity : ComponentActivity() {
    companion object {
        const val RESULT_RELOAD = 10
        const val RESULT_HOME = 11
    }

    private val pal by lazy { palette() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // top bar: ← and title, like Claude-Remote's screens
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), dp(16), dp(6))
        }
        bar.addView(text("←", 20f).apply {
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { finish() }
        })
        bar.addView(text("设置", Type.TITLE))
        col.addView(bar)

        col.addView(section("页面"))
        col.addView(switchRow("只留搜索", "隐藏首页频道和推荐流", prefs.hideFeed) { prefs.hideFeed = it })
        col.addView(divider())
        col.addView(switchRow("只看图文", "推荐流不出现视频笔记，隐藏 RED 和视频频道", prefs.imageOnly) { prefs.imageOnly = it })
        col.addView(divider())
        col.addView(switchRow("隐藏猜你想搜", "笔记正文下面的搜索推荐", prefs.hideRelated) { prefs.hideRelated = it })

        col.addView(section("操作"))
        col.addView(actionRow("重新加载页面") { setResult(RESULT_RELOAD); finish() })
        col.addView(divider())
        col.addView(actionRow("回到首页") { setResult(RESULT_HOME); finish() })
        col.addView(divider())
        col.addView(actionRow("检查更新") { Updater.check(this, quiet = false) })

        col.addView(section("调试"))
        col.addView(switchRow("页面内调试工具", "注入 eruda，右下角出现齿轮按钮；在 Console 里运行 __xl.probe() 查看选择器命中数", prefs.debug) { prefs.debug = it })

        col.addView(text("版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）", Type.TINY, pal.muted).apply {
            setPadding(dp(16), dp(24), dp(16), dp(16))
        })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            addView(col)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        Updater.resume(this)
    }

    private fun section(title: String) = text(title, Type.SMALL, pal.muted).apply {
        setPadding(dp(16), dp(20), dp(16), dp(6))
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(pal.line)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { marginStart = dp(16) }
    }

    private fun switchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = Switch(this).apply {
            isChecked = on
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(pal.accent, if (pal.dark) pal.muted else 0xFFFFFFFF.toInt()),
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(pal.accent, pal.line),
            )
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title))
            addView(text(sub, Type.SMALL, pal.muted).apply { setPadding(0, dp(2), 0, 0) })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(sw)
            setOnClickListener { sw.toggle() }
        }
    }

    private fun actionRow(title: String, onClick: () -> Unit) = text(title).apply {
        setPadding(dp(16), dp(14), dp(16), dp(14))
        setOnClickListener { onClick() }
    }
}
