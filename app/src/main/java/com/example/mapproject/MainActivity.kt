package com.example.mapproject

import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var trackView: TrackView
    private lateinit var tvLapTime: TextView
    private lateinit var tvSpeed: TextView

    private lateinit var locationManager: LocationManager
    private lateinit var ghostManager: GhostManager

    private var isRacing = false
    private var raceStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    // --- SIMULATION VARIABLES ---
    // If true, we use CSV data. If false, we use real GPS.
    private val IS_SIMULATION_MODE = true

    private var lastSimLat = 0.0
    private var lastSimLon = 0.0


    private val gameLoop = object : Runnable {
        override fun run() {
            if (isRacing) {
                val currentTimeMs = System.currentTimeMillis() - raceStartTime

                // 1. Update Timer UI
                val seconds = (currentTimeMs / 1000) % 60
                val minutes = (currentTimeMs / (1000 * 60)) % 60
                val millis = (currentTimeMs % 1000) / 10
                tvLapTime.text = String.format("%02d:%02d.%02d", minutes, seconds, millis)

                // 2. SIMULATION LOGIC: Drive the car using CSV
                if (IS_SIMULATION_MODE) {
                    val simPoint = ghostManager.getPositionAtTime(currentTimeMs)

                    if (simPoint != null) {
                        // We need to calculate heading (rotation) manually
                        // by comparing current point to previous point
                        val heading = calculateBearing(lastSimLat, lastSimLon, simPoint.lat, simPoint.lon)

                        // Update the PLAYER (You) with the CSV data
                        trackView.updatePlayerPosition(simPoint.lat, simPoint.lon, heading)

                        // Update last known position for next frame's heading calc
                        if (simPoint.lat != 0.0) {
                            lastSimLat = simPoint.lat
                            lastSimLon = simPoint.lon
                        }
                    }
                } else {
                    // REAL MODE: Update the Ghost (You are driving against it)
                    val ghostPoint = ghostManager.getPositionAtTime(currentTimeMs)
                    if (ghostPoint != null) {
                        trackView.updateGhostPosition(ghostPoint.lat, ghostPoint.lon)
                    }
                }

                trackView.invalidate() // Force redraw
                handler.postDelayed(this, 16) // ~60fps
            }
        }
    }

    // Helper to calculate rotation angle between two GPS points
    private fun calculateBearing(startLat: Double, startLon: Double, endLat: Double, endLon: Double): Float {
        val startLocation = Location("start")
        startLocation.latitude = startLat
        startLocation.longitude = startLon

        val endLocation = Location("end")
        endLocation.latitude = endLat
        endLocation.longitude = endLon

        return startLocation.bearingTo(endLocation)
    }

    // --- STANDARD IMAGE LOADING HELPERS ---
    fun decodeSampledBitmapFromResource(res: Resources, resId: Int, reqWidth: Int, reqHeight: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, resId, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(res, resId, options)
    }

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        trackView = findViewById(R.id.trackView)
        tvLapTime = findViewById(R.id.tvLapTime)
        tvSpeed = findViewById(R.id.tvSpeed)

        ghostManager = GhostManager(this)

        // NEW: Send the full path to the view for drawing
        trackView.setGhostLine(ghostManager.getAllPoints())

        // 1. Load Image
        var bitmap = decodeSampledBitmapFromResource(resources, R.drawable.track_map, 2048, 2048)

        // 2. Force Resize Safety Net
        val maxDimension = 2048
        if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = Math.min(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
            val width = (bitmap.width * ratio).toInt()
            val height = (bitmap.height * ratio).toInt()
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        }

        // 3. Setup Track (Lusail Coordinates)
        trackView.setupTrack(
            bitmap = bitmap,
            tlLat = 25.497417,
            tlLon = 51.445786,
            brLat = 25.483625,
            brLon = 51.461261
        )

        // 4. Setup GPS (Only if NOT in simulation mode)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!IS_SIMULATION_MODE) {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startGPS()
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        } else {
            Toast.makeText(this, "SIMULATION MODE ACTIVE", Toast.LENGTH_LONG).show()
        }

        // 5. Start Button
        trackView.setOnClickListener {
            if (!isRacing) {
                isRacing = true
                raceStartTime = System.currentTimeMillis()
                handler.post(gameLoop)
                Toast.makeText(this, "Race Started!", Toast.LENGTH_SHORT).show()
            }
        }

        // --- CALIBRATION LOGIC ---
        val btnUp = findViewById<android.widget.Button>(R.id.btnUp)
        val btnDown = findViewById<android.widget.Button>(R.id.btnDown)
        val btnLeft = findViewById<android.widget.Button>(R.id.btnLeft)
        val btnRight = findViewById<android.widget.Button>(R.id.btnRight)
        val btnLog = findViewById<android.widget.Button>(R.id.btnLog)
        val tvOffsets = findViewById<TextView>(R.id.tvDebugOffsets)

        // 1 meter is roughly 0.00001 degrees
        val NUDGE_AMOUNT = 0.000005

        fun updateCalibration() {
            trackView.invalidate() // Redraw immediately
            tvOffsets.text = String.format("Lat: %.6f\nLon: %.6f", trackView.calibrationLatOffset, trackView.calibrationLonOffset)
        }

        btnUp.setOnClickListener {
            trackView.calibrationLatOffset += NUDGE_AMOUNT
            updateCalibration()
        }
        btnDown.setOnClickListener {
            trackView.calibrationLatOffset -= NUDGE_AMOUNT
            updateCalibration()
        }
        // Note: Moving "Left" means decreasing Longitude (moving West)
        btnLeft.setOnClickListener {
            trackView.calibrationLonOffset -= NUDGE_AMOUNT
            updateCalibration()
        }
        btnRight.setOnClickListener {
            trackView.calibrationLonOffset += NUDGE_AMOUNT
            updateCalibration()
        }

        btnLog.setOnClickListener {
            val msg = "FINAL CALIBRATION:\nOffset Lat: ${trackView.calibrationLatOffset}\nOffset Lon: ${trackView.calibrationLonOffset}"
            android.util.Log.d("CALIBRATION", msg)
            Toast.makeText(this, "Check Logcat for numbers!", Toast.LENGTH_LONG).show()
        }
    }



    private fun startGPS() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    override fun onLocationChanged(location: Location) {
        // Only update from real GPS if we are NOT simulating
        if (!IS_SIMULATION_MODE) {
            trackView.updatePlayerPosition(location.latitude, location.longitude, location.bearing)
            val speedKmh = location.speed * 3.6f
            tvSpeed.text = String.format("%.0f km/h", speedKmh)
        }
    }

    // Boilerplate permission stuff
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (!IS_SIMULATION_MODE) startGPS()
        }
    }
}