package com.loctracker.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.loctracker.app.domain.Fix
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/** CARTO "Dark Matter" tiles built from OpenStreetMap data (attribution shown in the app's About). */
private val DarkTiles = XYTileSource(
    "CartoDarkMatter", 0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
    ),
    "© OpenStreetMap contributors © CARTO",
)

@SuppressLint("ClickableViewAccessibility")
@Composable
fun LiveMap(
    fix: Fix?,
    follow: Boolean,
    recenterTick: Int,
    onUserMoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density

    val map = remember {
        MapView(context).apply {
            setTileSource(DarkTiles)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            controller.setZoom(3.0)
            controller.setCenter(GeoPoint(30.0, 10.0))
        }
    }
    val marker = remember {
        Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2DD4A7"))
                setStroke((3 * density).toInt(), Color.WHITE)
                setSize((22 * density).toInt(), (22 * density).toInt())
            }
            setInfoWindow(null)
        }
    }
    val centered = remember { booleanArrayOf(false) }
    val accuracy = remember {
        Polygon(map).apply {
            fillPaint.color = Color.argb(40, 45, 212, 167)
            outlinePaint.color = Color.argb(120, 45, 212, 167)
            outlinePaint.strokeWidth = 2f
            setInfoWindow(null)
        }
    }

    DisposableEffect(Unit) {
        map.overlays.add(accuracy)
        map.overlays.add(marker)
        map.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_MOVE) onUserMoved()
            false
        }
        onDispose { }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }

    LaunchedEffect(fix, follow, recenterTick) {
        if (fix == null) return@LaunchedEffect
        val p = GeoPoint(fix.lat, fix.lon)
        marker.position = p
        accuracy.points = Polygon.pointsAsCircle(p, fix.accuracyM.coerceAtMost(500f).toDouble())
        if (!centered[0]) {
            centered[0] = true
            map.controller.setZoom(16.0)
            map.controller.setCenter(p)
        } else if (follow) map.controller.animateTo(p)
        map.invalidate()
    }

    AndroidView(factory = { map }, modifier = modifier)
}
