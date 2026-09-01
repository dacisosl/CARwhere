package com.eottadwotji.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases 기반 앱 자체 업데이트 (v3).
 *
 * main 푸시 → GitHub Actions가 서명 APK를 releases/latest에 배포 →
 * 대시보드가 tag(v{versionCode})를 현재 버전과 비교해 배너 표시 →
 * 탭하면 다운로드 후 설치 화면 호출 (안드로이드 정책상 설치 확인 탭은 필수).
 */
object UpdateChecker {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/dacisosl/CARwhere/releases/latest"
    private const val TIMEOUT_MS = 5_000

    data class Update(val versionCode: Int, val label: String, val apkUrl: String)

    /** 최신 릴리스 확인 — 실패(오프라인 등)하면 조용히 null */
    fun check(currentVersionCode: Int, callback: (Update?) -> Unit) {
        Thread {
            val update = runCatching { fetchLatest(currentVersionCode) }.getOrNull()
            Handler(Looper.getMainLooper()).post { callback(update) }
        }.start()
    }

    private fun fetchLatest(current: Int): Update? {
        val conn = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)

        val code = json.getString("tag_name").filter { it.isDigit() }.toIntOrNull() ?: return null
        if (code <= current) return null

        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk")) {
                return Update(
                    versionCode = code,
                    label = json.optString("name").ifEmpty { "v$code" },
                    apkUrl = asset.getString("browser_download_url")
                )
            }
        }
        return null
    }

    /** APK 다운로드 → 완료되면 시스템 설치 화면 호출 */
    fun downloadAndInstall(context: Context, update: Update) {
        val appContext = context.applicationContext
        val dm = appContext.getSystemService(DownloadManager::class.java)

        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("어따뒀지 ${update.label}")
            .setDestinationInExternalFilesDir(
                appContext, Environment.DIRECTORY_DOWNLOADS, "naechawichi-update.apk"
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                runCatching { appContext.unregisterReceiver(this) }
                val apkUri = dm.getUriForDownloadedFile(downloadId) ?: return
                appContext.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(apkUri, "application/vnd.android.package-archive")
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                )
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }
}
