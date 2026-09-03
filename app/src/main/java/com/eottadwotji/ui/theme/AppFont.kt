package com.eottadwotji.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.eottadwotji.R

/**
 * 네이티브 Canvas·RemoteViews용 Pretendard 로더 (v5.3).
 *
 * Compose는 [Pretendard] FontFamily를 쓰지만, 상태바 표지판·지도 마커·스플래시처럼
 * android.graphics.Paint로 직접 그리는 곳은 Typeface가 필요하다. 로드가 가볍지 않아
 * 한 번 읽고 캐시한다 (Typeface는 Context를 잡지 않아 static 보관이 안전하다).
 * 폰트를 못 읽으면 기존 동작(sans-serif-black)으로 떨어진다.
 */
object AppFont {
    @Volatile private var blackCache: Typeface? = null
    @Volatile private var bodyCache: Typeface? = null

    /** 층수·워드마크처럼 "사인"으로 읽혀야 하는 곳 */
    fun black(context: Context): Typeface =
        blackCache ?: load(context, R.font.pretendard_black, "sans-serif-black")
            .also { blackCache = it }

    /** 태그라인 등 보조 문구 */
    fun body(context: Context): Typeface =
        bodyCache ?: load(context, R.font.pretendard_regular, "sans-serif")
            .also { bodyCache = it }

    private fun load(context: Context, resId: Int, fallback: String): Typeface =
        runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull()
            ?: Typeface.create(fallback, Typeface.NORMAL)
}
