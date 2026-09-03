package com.eottadwotji.ui.dashboard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.Geocoder
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.eottadwotji.R
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import com.eottadwotji.ui.theme.appCard
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 지도 위 마커 색 — 구글 지도 계열의 익숙한 톤 (v5.2) */
private val MAP_BLUE = 0xFF1A73E8.toInt()   // 내 위치
private val MAP_INK = 0xFF17191D.toInt()    // 차 마커·경로·핀

/**
 * 대시보드 지도 카드 (v5.2 — 사용자 스케치 + 반응형 높이).
 *
 * 위: 칩 줄 — [🔍 검색] [집] [학교] [직장] … 저장된 위치. 탭하면 지도가 그 위치로 이동.
 *     검색 칩을 누르면 입력창이 열리고, 이름이 맞는 저장 위치가 있으면 그곳으로,
 *     없으면 주소/장소명을 지오코딩해 핀을 찍는다. (편집·추가는 위치관리 탭)
 * 아래: 지도 — 주차 중이면 차 마커 + 내 위치 점선 + 거리·방향 필, 선택한 위치엔 P 핀.
 *
 * 절대 규칙 6(상시 추적 금지): 내 위치는 화면 복귀 시 1회만 조회하고 저장하지 않는다.
 * 지도는 프리뷰 전용(스크롤 충돌 방지) — 탭하면 지도 앱으로 넘겨 길찾기.
 *
 * v5.2: 타일 색 반전 필터를 없앴다 — 지도는 사람들이 가장 익숙한 기본 OSM 색 그대로 두고,
 * 마커도 시그니처 색이 아니라 지도에서 통용되는 색을 쓴다 (내 위치 파란 점, 차 잉크).
 * 카드는 남는 높이를 받도록 modifier를 밖에서 받는다 (기종별 화면 높이 대응).
 */
@Composable
fun CarMapCard(
    lots: List<ParkingLotProfile>,
    selectedLotId: String?,
    carCoords: Pair<Double, Double>?,
    myCoords: Pair<Double, Double>?,
    parked: Boolean,
    onLotSelect: (ParkingLotProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchPin by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var searchHint by remember { mutableStateOf<String?>(null) }

    val selectedLot = lots.firstOrNull { it.id == selectedLotId }
    val lotPin = selectedLot?.let { lot ->
        lot.latitude?.let { lat -> lot.longitude?.let { lon -> lat to lon } }
    }
    // 지도가 보여줄 핀: 검색 결과 > 선택한 위치
    val pin = searchPin ?: lotPin
    // 화면에 뭔가 보여줄 수 있는가 (차·핀 중 하나라도)
    val hasContent = carCoords != null || pin != null

    val submitSearch = {
        val q = query.trim()
        if (q.isNotEmpty()) {
            val match = lots.firstOrNull { it.name.contains(q, ignoreCase = true) }
            if (match != null) {
                searchPin = null
                searchHint = if (match.latitude == null) "'${match.name}'은 좌표가 없어요 — 위치관리에서 등록" else null
                onLotSelect(match)
            } else {
                geocodeOnce(context, q) { result ->
                    searchPin = result
                    searchHint = if (result == null) "'$q'를 찾지 못했어요" else null
                }
            }
        }
    }

    // 칩 줄 스크롤 상태 — 위치가 3개를 넘으면 화면 밖으로 나가므로 (v5.3)
    //  ① 선택된 위치가 밖에 있으면 그 칩으로 스크롤해 오고
    //  ② 오른쪽에 더 있다는 것을 페이드로 알린다
    val chipState = rememberLazyListState()
    val selectedIndex = lots.indexOfFirst { it.id == selectedLotId }
    LaunchedEffect(selectedLotId, lots.size) {
        if (selectedIndex >= 0) {
            // 검색 칩이 0번이므로 위치 칩은 +1
            runCatching { chipState.animateScrollToItem((selectedIndex + 1).coerceAtLeast(0)) }
        }
    }

    Column(
        modifier = modifier
            .appCard()
            .padding(vertical = 12.dp)
    ) {
        // ── 칩 줄: 검색 + 저장된 위치 ──
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = chipState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Concrete.BgPanel, CircleShape)
                            .then(
                                if (searching) Modifier.border(1.5.dp, Concrete.Neon, CircleShape)
                                else Modifier
                            )
                            .clickable {
                                searching = !searching
                                if (!searching) {
                                    query = ""
                                    searchPin = null
                                    searchHint = null
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "위치 검색",
                            tint = if (searching) Concrete.Neon else Concrete.TextSub,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                items(lots, key = { it.id }) { lot ->
                    LotChip(
                        label = lot.name,
                        active = lot.id == selectedLotId && searchPin == null,
                        onClick = {
                            searchPin = null
                            searchHint = null
                            onLotSelect(lot)
                        }
                    )
                }
                if (lots.isEmpty()) {
                    item {
                        Text(
                            "위치관리 탭에서 자주 가는 곳을 등록해요",
                            style = AppType.Hint,
                            color = Concrete.TextDim
                        )
                    }
                }
            }
            // 오른쪽에 칩이 더 있으면 카드색으로 페이드 — 스크롤할 수 있다는 신호
            if (chipState.canScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(24.dp)
                        .height(32.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Concrete.BgDeep)
                            )
                        )
                )
            }
        }

        // ── 검색 입력 (검색 칩을 켰을 때만) ──
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text("저장한 위치 이름 또는 주소·장소명", style = AppType.BodySmall)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Concrete.Neon,
                    unfocusedBorderColor = Concrete.Border,
                    focusedTextColor = Concrete.TextMain,
                    unfocusedTextColor = Concrete.TextMain,
                    cursorColor = Concrete.Neon,
                    focusedPlaceholderColor = Concrete.TextDim,
                    unfocusedPlaceholderColor = Concrete.TextDim
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
            searchHint?.let {
                Text(
                    it,
                    style = AppType.Hint,
                    color = Concrete.TextDim,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 지도 ──
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Concrete.BgPanel)
        ) {
            if (hasContent) {
                OsmCarMap(
                    car = carCoords,
                    me = myCoords,
                    pin = pin,
                    modifier = Modifier.fillMaxSize()
                )
                // 터치는 전부 이 투명 레이어가 받는다 — 세로 스크롤과 지도 드래그 충돌 방지
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { openInMapsApp(context, carCoords ?: pin!!) }
                )
                if (myCoords != null && carCoords != null) {
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
                            .background(Concrete.BgDeep.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
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
                } else if (selectedLot != null && pin != null && carCoords == null) {
                    Text(
                        selectedLot.name,
                        style = AppType.BodySmall,
                        color = Concrete.TextMain,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .background(Concrete.BgDeep.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
                Text(
                    "지도 앱으로 열기 →",
                    style = AppType.Hint,
                    color = Concrete.TextSub,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Concrete.BgDeep.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when {
                            parked -> "저장된 차 좌표가 없어요"
                            selectedLot != null -> "'${selectedLot.name}'은 좌표가 없어요"
                            else -> "주차하면 차 위치가 여기에 표시돼요"
                        },
                        style = AppType.Body,
                        color = Concrete.TextSub
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            parked -> "위치 권한이 꺼져 있거나 지하라 GPS를 못 잡았어요"
                            selectedLot != null -> "위치관리 탭에서 '현재 위치로 등록'을 눌러주세요"
                            else -> "위치 칩을 누르면 그곳을 지도에서 볼 수 있어요"
                        },
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
    val color = Concrete.TextMain
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
 * osmdroid MapView — 차 마커, 내 위치 점 + 점선, 선택/검색 핀.
 * 프리뷰 전용: 줌 버튼·멀티터치 없음, 위 Compose 레이어가 탭을 처리한다.
 */
@Composable
private fun OsmCarMap(
    car: Pair<Double, Double>?,
    me: Pair<Double, Double>?,
    pin: Pair<Double, Double>?,
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

    // 지도 위 색은 팔레트가 아니라 "지도에서 익숙한 색" (v5.2)
    val routeColor = MAP_INK
    val faceColor = android.graphics.Color.WHITE

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.clear()

            val density = map.resources.displayMetrics.density
            val carPoint = car?.let { GeoPoint(it.first, it.second) }
            val mePoint = me?.let { GeoPoint(it.first, it.second) }
            val pinPoint = pin?.let { GeoPoint(it.first, it.second) }

            if (mePoint != null && carPoint != null) {
                map.overlays.add(
                    Polyline(map).apply {
                        setPoints(listOf(mePoint, carPoint))
                        outlinePaint.color = routeColor
                        outlinePaint.strokeWidth = 3f * density
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.pathEffect =
                            DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
                        isEnabled = true
                    }
                )
            }
            if (mePoint != null) {
                map.overlays.add(
                    Marker(map).apply {
                        position = mePoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = dotDrawable(map.context, 16, MAP_BLUE, faceColor)
                        setInfoWindow(null)
                    }
                )
            }
            if (pinPoint != null) {
                map.overlays.add(
                    Marker(map).apply {
                        position = pinPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = pinDrawable(map.context, 34, faceColor, MAP_INK)
                        setInfoWindow(null)
                    }
                )
            }
            if (carPoint != null) {
                map.overlays.add(
                    Marker(map).apply {
                        position = carPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = carMarkerDrawable(map.context, 42, MAP_INK, faceColor)
                        setInfoWindow(null)
                    }
                )
            }

            val points = listOfNotNull(mePoint, carPoint, pinPoint)
            val frame = {
                if (points.size >= 2) {
                    val box = BoundingBox.fromGeoPointsSafe(points)
                    map.zoomToBoundingBox(box, false, (40 * density).roundToInt())
                    // 아주 가까우면 bounding box 줌이 과하게 커진다 — 상한
                    if (map.zoomLevelDouble > 18.0) map.controller.setZoom(18.0)
                } else if (points.size == 1) {
                    map.controller.setZoom(17.0)
                    map.controller.setCenter(points.first())
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

/** 위치 핀: 어두운 원판 + 네온 링 + "P" */
private fun pinDrawable(context: Context, sizeDp: Int, fill: Int, ring: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val px = (sizeDp * density).roundToInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val c = px / 2f
    paint.color = fill
    canvas.drawCircle(c, c, c - 2f * density, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f * density
    paint.color = ring
    canvas.drawCircle(c, c, c - 2f * density, paint)
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = com.eottadwotji.ui.theme.AppFont.black(context)
    paint.textSize = px * 0.5f
    val baseline = c - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText("P", c, baseline, paint)
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

/**
 * 주소·장소명 → 좌표 1회 (Android Geocoder, API 키 불필요).
 * 백그라운드 스레드에서 돌리고 결과만 상태에 쓴다 — Compose 스냅샷 상태는 스레드 안전.
 */
private fun geocodeOnce(context: Context, query: String, onResult: (Pair<Double, Double>?) -> Unit) {
    if (!Geocoder.isPresent()) {
        onResult(null)
        return
    }
    Thread {
        val result = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.KOREAN).getFromLocationName(query, 1)
                ?.firstOrNull()
                ?.let { it.latitude to it.longitude }
        }.getOrNull()
        onResult(result)
    }.start()
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
    else -> String.format(Locale.KOREAN, "%.1fkm", meters / 1_000)
}
