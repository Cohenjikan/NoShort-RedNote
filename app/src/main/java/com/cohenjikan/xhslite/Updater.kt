package com.cohenjikan.xhslite

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Updates from GitHub Releases (published by .github/workflows/release.yml).
 * The release notes carry `versionCode=N`; a release newer than this build is offered, the APK is
 * downloaded into cacheDir/updates and handed to the system installer. Android itself refuses an
 * APK signed with a different key, so nothing else needs checking here.
 */
object Updater {
    private const val API = "https://api.github.com/repos/Cohenjikan/NoShort-RedNote/releases/latest"
    private const val ASSET = "xhs-lite.apk"
    /** automatic checks (on app start) at most this often; the settings button always checks */
    private const val AUTO_EVERY_MS = 6 * 60 * 60 * 1000L

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** downloaded, waiting for the user to allow "install unknown apps" */
    private var pending: File? = null

    private data class Release(val code: Int, val tag: String, val notes: String, val apkUrl: String)

    fun autoCheck(a: Activity) {
        val sp = a.getSharedPreferences("xhslite", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("updateCheckedAt", 0) < AUTO_EVERY_MS) return
        sp.edit().putLong("updateCheckedAt", now).apply()
        check(a, quiet = true)
    }

    /** [quiet]: say nothing unless there is an update (automatic check) */
    fun check(a: Activity, quiet: Boolean) {
        if (!quiet) toast(a, "正在检查更新…")
        io.execute {
            val r = runCatching { fetch() }
            main.post {
                if (a.isFinishing || a.isDestroyed) return@post
                r.onFailure { if (!quiet) toast(a, "检查更新失败：${it.message}") }
                r.onSuccess { rel ->
                    when {
                        rel == null -> if (!quiet) toast(a, "还没有发布的版本")
                        rel.code <= BuildConfig.VERSION_CODE -> if (!quiet) toast(a, "已是最新版本")
                        else -> offer(a, rel)
                    }
                }
            }
        }
    }

    /** call from onResume: finishes an install that was waiting for the unknown-apps permission */
    fun resume(a: Activity) {
        val f = pending ?: return
        if (a.packageManager.canRequestPackageInstalls() && f.exists()) {
            pending = null
            install(a, f)
        }
    }

    private fun fetch(): Release? {
        val c = URL(API).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val body = j.optString("body")
            val code = Regex("versionCode=(\\d+)").find(body)?.groupValues?.get(1)?.toInt() ?: return null
            val assets = j.getJSONArray("assets")
            val url = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == ASSET }?.optString("browser_download_url") ?: return null
            val notes = body.lines().filterNot { it.startsWith("versionCode=") }.joinToString("\n").trim()
            return Release(code, j.optString("tag_name"), notes, url)
        } finally {
            c.disconnect()
        }
    }

    private fun offer(a: Activity, r: Release) {
        AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("有新版本 ${r.tag}")
            .setMessage(r.notes.ifBlank { "新版本已发布。" })
            .setPositiveButton("下载并安装") { _, _ -> download(a, r) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun download(a: Activity, r: Release) {
        val cancelled = AtomicBoolean(false)
        val dialog = AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("正在下载 ${r.tag}")
            .setMessage("0%")
            .setCancelable(false)
            .setNegativeButton("取消") { _, _ -> cancelled.set(true) }
            .show()
        io.execute {
            val res = runCatching {
                val dir = File(a.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "xhs-lite-${r.code}.apk")
                val c = URL(r.apkUrl).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                try {
                    if (c.responseCode != 200) error("HTTP ${c.responseCode}")
                    val total = c.contentLengthLong
                    c.inputStream.use { input ->
                        out.outputStream().use { o ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var shown = -1
                            while (true) {
                                if (cancelled.get()) error("已取消")
                                val n = input.read(buf)
                                if (n < 0) break
                                o.write(buf, 0, n)
                                done += n
                                val pct = if (total > 0) (done * 100 / total).toInt() else -1
                                if (pct != shown && pct >= 0) {
                                    shown = pct
                                    main.post { dialog.setMessage("$pct%") }
                                }
                            }
                        }
                    }
                } finally {
                    c.disconnect()
                }
                out
            }
            main.post {
                if (dialog.isShowing) dialog.dismiss()
                if (a.isFinishing || a.isDestroyed) return@post
                res.onSuccess { install(a, it) }
                res.onFailure { if (!cancelled.get()) toast(a, "下载失败：${it.message}") }
            }
        }
    }

    private fun install(a: Activity, f: File) {
        if (!a.packageManager.canRequestPackageInstalls()) {
            // one-time system switch; resume() installs once the user comes back with it on
            pending = f
            toast(a, "请允许「小红书·轻」安装应用，返回后自动继续")
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.files", f)
        a.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
}
