package com.example.mapproject

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

// Created a Integration branch
data class GhostPoint(var timeMs: Long, val lat: Double, val lon: Double)

class GhostManager(context: Context) {
    private val points = mutableListOf<GhostPoint>()

    fun getAllPoints(): List<GhostPoint> {
        return points
    }

    init {
        try {
            Log.d("GHOST_DEBUG", "Attempting to open ghost_data.csv...")
            val inputStream = context.resources.openRawResource(R.raw.ghost_data)
            val reader = BufferedReader(InputStreamReader(inputStream))

            var lineCount = 0
            reader.forEachLine { line ->
                lineCount++
                val parts = line.split(",")
                if (parts.size >= 3) {
                    try {
                        // Parse Seconds -> Milliseconds
                        val timeSeconds = parts[0].trim().toDouble()
                        val t = (timeSeconds * 1000).toLong()
                        val lat = parts[1].trim().toDouble()
                        val lon = parts[2].trim().toDouble()
                        points.add(GhostPoint(t, lat, lon))
                    } catch (e: NumberFormatException) {}
                }
            }

            // Normalize Start Time to 0
            if (points.isNotEmpty()) {
                points.sortBy { it.timeMs }
                val startTime = points[0].timeMs
                if (startTime > 0) {
                    for (point in points) point.timeMs -= startTime
                }
            }
        } catch (e: Exception) {
            Log.e("GHOST_DEBUG", "Error reading CSV", e)
        }
    }

    /**
     * NOW WITH SMOOTHING!
     * Calculates the exact interpolated position between two data points.
     */
    fun getPositionAtTime(currentTimeMs: Long): GhostPoint? {
        if (points.isEmpty()) return null

        // 1. Find the two points we are in between
        // 'index' is the first point that is AFTER our current time
        val index = points.indexOfFirst { it.timeMs >= currentTimeMs }

        // Edge Case: Before start of race
        if (index == 0) return points.first()

        // Edge Case: After end of race
        if (index == -1) return points.last()

        // 2. Get the "Previous" and "Next" points
        val nextPoint = points[index]
        val prevPoint = points[index - 1]

        // 3. Calculate ratio (0.0 to 1.0)
        // How far are we between Prev and Next?
        val timeGap = nextPoint.timeMs - prevPoint.timeMs
        val timeProgress = currentTimeMs - prevPoint.timeMs

        // Avoid division by zero
        val fraction = if (timeGap == 0L) 0.0 else timeProgress.toDouble() / timeGap.toDouble()

        // 4. LERP (Linear Interpolation)
        val smoothLat = prevPoint.lat + (nextPoint.lat - prevPoint.lat) * fraction
        val smoothLon = prevPoint.lon + (nextPoint.lon - prevPoint.lon) * fraction

        // Return a new temporary point for drawing
        return GhostPoint(currentTimeMs, smoothLat, smoothLon)
    }
}