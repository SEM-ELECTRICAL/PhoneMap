package com.example.mapproject
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class TrackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var trackBitmap: Bitmap? = null

    // Config: Map Corners
    private var topLeftLat: Double = 0.0
    private var topLeftLon: Double = 0.0
    private var bottomRightLat: Double = 0.0
    private var bottomRightLon: Double = 0.0

    // Dynamic State
    private var playerLat: Double = 0.0
    private var playerLon: Double = 0.0
    private var playerHeading: Float = 0.0f

    // Ghost State
    private var isGhostActive: Boolean = false
    private var ghostLat: Double = 0.0
    private var ghostLon: Double = 0.0

    var calibrationLatOffset: Double = -0.000045
    var calibrationLonOffset: Double = -0.000035


    // Zoom: 1.0 is fit to screen, 3.0 is zoomed in
    var zoomLevel: Float = 6.0f

    // Paints
    private val playerPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL; isAntiAlias = true }
    private val ghostPaint = Paint().apply { color = Color.parseColor("#448AFF"); style = Paint.Style.FILL; alpha = 150; isAntiAlias = true }
    private val mapPaint = Paint().apply { isFilterBitmap = true }

    fun setupTrack(bitmap: Bitmap, tlLat: Double, tlLon: Double, brLat: Double, brLon: Double) {
        this.trackBitmap = bitmap
        this.topLeftLat = tlLat
        this.topLeftLon = tlLon
        this.bottomRightLat = brLat
        this.bottomRightLon = brLon
        invalidate()
    }

    fun updatePlayerPosition(lat: Double, lon: Double, heading: Float) {
        this.playerLat = lat
        this.playerLon = lon
        this.playerHeading = heading
        invalidate()
    }

    fun updateGhostPosition(lat: Double, lon: Double) {
        this.isGhostActive = true
        this.ghostLat = lat
        this.ghostLon = lon
        // We rely on the player update to trigger redraw, or call invalidate() here if GPS is slow
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trackBitmap == null) return

        val cx = width / 2f
        val cy = height / 2f

        val (playerX, playerY) = latLonToPixels(playerLat, playerLon)
        val (ghostX, ghostY) = latLonToPixels(ghostLat, ghostLon)

//        if (System.currentTimeMillis() % 1000 < 20) {
//            android.util.Log.d("TRACK_DEBUG", "------------------------------------------")
//            android.util.Log.d("TRACK_DEBUG", "Map Size: W=${trackBitmap?.width} H=${trackBitmap?.height}")
//            android.util.Log.d("TRACK_DEBUG", "Player Lat/Lon: $playerLat, $playerLon")
//            android.util.Log.d("TRACK_DEBUG", "Calculated Pixels: X=$playerX Y=$playerY")
//
//            // Check if we are totally off the map
//            if (playerX < 0 || playerX > (trackBitmap?.width ?: 0) || playerY < 0 || playerY > (trackBitmap?.height ?: 0)) {
//                android.util.Log.e("TRACK_DEBUG", "ALERT: Player is OFF THE MAP IMAGE!")
//            } else {
//                android.util.Log.d("TRACK_DEBUG", "STATUS: Player is SAFELY inside the map.")
//            }
//        }

        canvas.save()

        // 1. Move Camera to Center
        canvas.translate(cx, cy)

        // 2. Rotate World (Inverse of Player Heading)
        canvas.rotate(-playerHeading)

        // 3. Apply Zoom
        canvas.scale(zoomLevel, zoomLevel)

        // 4. Move World under Player
        canvas.translate(-playerX, -playerY)

        // Draw World
        canvas.drawBitmap(trackBitmap!!, 0f, 0f, mapPaint)

        if (isGhostActive) {
            canvas.drawCircle(ghostX, ghostY, 20f, ghostPaint)
        }

        canvas.restore()

        // Draw Player (Fixed in center)
        canvas.drawCircle(cx, cy, 30f, playerPaint)
    }

    private fun latLonToPixels(lat: Double, lon: Double): Pair<Float, Float> {
        if (trackBitmap == null) return Pair(0f, 0f)

        // Apply the Manual Calibration Nudge
        val adjustedLat = lat + calibrationLatOffset
        val adjustedLon = lon + calibrationLonOffset

        val mapWidth = trackBitmap!!.width.toFloat()
        val mapHeight = trackBitmap!!.height.toFloat()

        val latSpan = topLeftLat - bottomRightLat
        val lonSpan = bottomRightLon - topLeftLon

        // Use adjusted coordinates for calculation
        val latProgress = (topLeftLat - adjustedLat) / latSpan
        val lonProgress = (adjustedLon - topLeftLon) / lonSpan

        val x = (lonProgress * mapWidth).toFloat()
        val y = (latProgress * mapHeight).toFloat()

        return Pair(x, y)
    }
}