package com.eottadwotji.ui.dashboard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.eottadwotji.R
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import com.eottadwotji.ui.theme.DarkPalette
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 대시보드 지도 카드 (v4.2 — 사용자 스케치).
 *
 * 위: 저장된 위치 칩(집·학교…, 가로 스크롤, + 추가) — 탭하면 수정 모달.
 * 아래: GPS 기반 지도 — 주차 확정 때 1회 저장한 차 좌표에 차 마커,
 *       내 위치(대시보드 열 때 1회 조회)에서 차까지 점선 + 거리·방향 화살표.
 *
 * 절대 규칙 6(상시 추적 금지) 준수: 내 위치는 화면 복귀 시 1회만 조회하고 저장하지 않는다.
 * 지도는 프리뷰 전용(스크롤 충돌 방지) — 탭하면 지도 앱으로 넘겨 길찾기.
 * 타일은 OSM(API 키 불필요), 다크 테마에선 색 반전 필터로 계기판 톤에 맞춘다.
 */
@Composable
fun CarMapCard(
    lots: List<ParkingLotProfile>,
    currentLotId: String?,
    carCoords: Pair<Double, Double>?,
    myCoords: Pair<Double, Double>?,
    parked: Boolean,
    onLotTap: (ParkingLotProfile) -> Unit,
    onAddLot: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
            .padding(top = 14.dp, bottom = 14.dp)
    ) {
        // ── 저장된 위치 칩 ──
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp)
        ) {
            item {
                Text("위치", style = AppType.SectionLabel, color = Concrete.TextDim)
            }
            items(lots, key = { it.id }) { lot ->
                LotChip(
                    label = lot.name,
                    active = lot.id == currentLotId,
                    onClick = { onLotTap(lot) }
                )
            }
            item {
                LotChip(label = "+", active = false, onClick = onAddLot)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 지도 ──
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Concrete.BgScreen)
        ) {
            if (carCoords != null) {
                OsmCarMap(
                    car = carCoords,
                    me = myCoords,
                    dark = Concrete.palette == DarkPalette,
                    modifier = Modifier.fillMaxSize()
                )
                // 터치는 전부 이 투명 레이어가 받는다 — 세로 스크롤과 지도 드래그 충돌 방지
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { openInMapsApp(context, carCoords) }
                )
                // 거리·방향 필 (내 위치를 알 때만)
                if (myCoords != null) {
                    val distanceM = ParkingLotProfile.distanceMeters(
                        myCoords.first, myCoords.second, carCoords.first, carCoords.second
                    )
                    val bearing = bearingDegrees(
                        myCoords.first, myCoords.second, carCoords.first, carCoords.second
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .background(Concrete.BgDeep.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BearingArrow(bearing, Modifier.size(14.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "차까지 ${formatDistance(distanceM)} · ${compassLabel(bearing)}",
                            style = AppType.BodySmall,
                            color = Concrete.TextMain
                        )
                    }
                }
                Text(
                    "지도 앱으로 열기 →",
                    style = AppType.Hint,
                    color = Concrete.TextSub,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Concrete.BgDeep.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (parked) "저장된 차 좌표가 없어요"
                        else "주차하면 차 위치가 여기에 표시돼요",
                        style = AppType.Body,
                        color = Concrete.TextSub
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (parked) "위치 권한이 꺼져 있거나 지하라 GPS를 못 잡았어요"
                        else "GPS 좌표는 주차 확정 순간 1회만 저장해요",
                        style = AppType.Hint,
                        color = Concrete.TextDim
                    )
                }
            }
        }
    }
}

@Composable
private fun LotChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .background(Concrete.BgPanel, RoundedCornerShape(16.dp))
            .then(
                if (active) Modifier.border(1.5.dp, Concrete.Neon, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = AppType.BodySmall,
            color = if (active) Concrete.NeonLight else Concrete.TextBody
        )
    }
}

/** 북쪽 기준 방위각(도)만큼 회전한 화살표 — 내 위치에서 차 방향 */
@Composable
private fun BearingArrow(bearingDeg: Double, modifier: Modifier = Modifier) {
    val color = Concrete.Neon
    androidx.compose.foundation.Canvas(modifier = modifier.rotate(bearingDeg.toFloat())) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(w, h)
            lineTo(w / 2f, h * 0.72f)
            lineTo(0f, h)
            close()
        }
        drawPath(path, color)
        drawPath(
            path, color,
            style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * osmdroid MapView — 차 마커 + (내 위치 있으면) 내 위치 점과 점선.
 * 프리뷰 전용: 줌 버튼·멀티터치 없음, 위 Compose 레이어가 탭을 처리한다.
 */
@Composable
private fun OsmCarMap(
    car: Pair<Double, Double>,
    me: Pair<Double, Double>?,
    dark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName // OSM 타일 정책: 식별 가능한 UA 필수
            // 스코프 저장소 대응: 외부 저장소 대신 앱 캐시에 타일 보관
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
        }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    val neon = Concrete.Neon.toArgb()
    val faceColor = Concrete.BgDeep.toArgb()
    val meColor = Concrete.TextMain.toArgb()

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlayManager.tilesOverlay.setColorFilter(
                if (dark) TilesOverlay.INVERT_COLORS else null
            )
            map.overlays.clear()

            val carPoint = GeoPoint(car.first, car.second)
            val mePoint = me?.let { GeoPoint(it.first, it.second) }
            val density = map.resources.displayMetrics.density

            if (mePoint != null) {
                map.overlays.add(
                    Polyline(map).apply {
                        setPoints(listOf(mePoint, carPoint))
                        outlinePaint.color = neon
                        outlinePaint.strokeWidth = 3f * density
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.pathEffect =
                            DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
                        isEnabled = true
                    }
                )
                map.overlays.add(
                    Marker(map).apply {
                        position = mePoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = dotDrawable(map.context, 14, meColor, faceColor)
                        setInfoWindow(null)
                    }
                )
            }
            map.overlays.add(
                Marker(map).apply {
                    position = carPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = carMarkerDrawable(map.context, 40, faceColor, neon)
                    setInfoWindow(null)
                }
            )

            val frame = {
                if (mePoint != null) {
                    val box = BoundingBox.fromGeoPointsSafe(listOf(mePoint, carPoint))
                    map.zoomToBoundingBox(box, false, (40 * density).roundToInt())
                    // 아주 가까우면 bounding box 줌이 과하게 커진다 — 상한
                    if (map.zoomLevelDouble > 18.0) map.controller.setZoom(18.0)
                } else {
                    map.controller.setZoom(17.0)
                    map.controller.setCenter(carPoint)
                }
                map.invalidate()
            }
            if (map.width == 0) {
                map.addOnFirstLayoutListener { _, _, _, _, _ -> frame() }
            } else {
                frame()
            }
        }
    )
}

/** 차 마커: 어두운 원판 + 네온 링 + 차 실루엣 (계기판 톤 유지) */
private fun carMarkerDrawable(context: Context, sizeDp: Int, fill: Int, ring: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val px = (sizeDp * density).roundToInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val c = px / 2f
    paint.color = fill
    canvas.drawCircle(c, c, c - 2f * density, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f * density
    paint.color = ring
    canvas.drawCircle(c, c, c - 2f * density, paint)
    ContextCompat.getDrawable(context, R.drawable.ic_car)?.let { raw ->
        val icon = DrawableCompat.wrap(raw).mutate()
        DrawableCompat.setTint(icon, ring)
        val inset = (px * 0.26f).roundToInt()
        icon.setBounds(inset, inset, px - inset, px - inset)
        icon.draw(canvas)
    }
    return BitmapDrawable(context.resources, bitmap)
}

/** 내 위치 점 */
private fun dotDrawable(context: Context, sizeDp: Int, fill: Int, ring: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val px = (sizeDp * density).roundToInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val c = px / 2f
    paint.color = ring
    canvas.drawCircle(c, c, c, paint)
    paint.color = fill
    canvas.drawCircle(c, c, c - 2f * density, paint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun openInMapsApp(context: Context, coords: Pair<Double, Double>) {
    val (lat, lon) = coords
    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode("내 차")})")
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** 시작점→끝점 방위각 (0=북, 시계 방향, 도) */
internal fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dLon = Math.toRadians(lon2 - lon1)
    val y = sin(dLon) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

internal fun compassLabel(bearingDeg: Double): String {
    val labels = listOf("북", "북동", "동", "남동", "남", "남서", "서", "북서")
    val index = ((bearingDeg + 22.5) / 45.0).toInt() % 8
    return labels[index] + "쪽"
}

internal fun formatDistance(meters: Double): String = when {
    meters < 1_000 -> "${meters.roundToInt()}m"
    else -> String.format(java.util.Locale.KOREAN, "%.1fkm", meters / 1_000)
}
