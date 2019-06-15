package com.peterlaurence.mapview.core

/**
 * Resolves the visible tiles
 *
 * @param levelCount Number of levels
 * @param fullWidth Width of the map at scale 1.0f
 * @param fullHeight Height of the map at scale 1.0f
 * @param magnifyingFactor Alters the level at which tiles are picked for a given scale. By default,
 * the level immediately higher (in index) is picked, to avoid sub-sampling. This corresponds to a
 * [magnifyingFactor] of 0. The value 1 will result in picking the current level at a given scale,
 * which will be at a relative scale between 1.0 and 2.0
 */
class VisibleTilesResolver(private val levelCount: Int, private val fullWidth: Int,
                           private val fullHeight: Int, private val tileSize: Int = 256,
                           private val magnifyingFactor: Int = 0) {

    private var scale: Float = 1.0f
    private var currentLevel = levelCount - 1
    private var subSample: Int = 0

    /**
     * Last level is at scale 1.0f, others are at scale 1.0 / power_of_2
     */
    private val scaleForLevel = (0 until levelCount).associateWith {
        (1.0 / Math.pow(2.0, (levelCount - it - 1).toDouble())).toFloat()
    }

    fun setScale(scale: Float) {
        this.scale = scale

        this.subSample = if (scale < scaleForLevel[0] ?: Float.MIN_VALUE) {
            (Math.log((scaleForLevel[0] ?: error("")).toDouble() / scale + 2.0)/ Math.log(2.0)).toInt()
        } else {
            0
        }
//        println("subsample: $subSample")
//        println("scale $scale / ${scaleForLevel[0]}")

        /* Update current level */
        currentLevel = getLevel(scale, magnifyingFactor)
    }

    fun getCurrentLevel(): Int {
        return currentLevel
    }

    /**
     * Get the scale for a given [level] (also called zoom).
     * @return the scale or null if no such level was configured.
     */
    fun getScaleForLevel(level: Int): Float? {
        return scaleForLevel[level]
    }

    /**
     * Returns the level an entire value belonging to [0 ; [levelCount] - 1]
     */
    private fun getLevel(scale: Float, magnifyingFactor: Int = 0): Int {
        /* This value can be negative */
        val partialLevel = levelCount - 1 - magnifyingFactor +
                Math.log(scale.toDouble()) / Math.log(2.0)

        /* The level can't be greater than levelCount - 1.0 */
        val capedLevel = Math.min(partialLevel, levelCount - 1.0)

        /* The level can't be lower than 0 */
        return Math.ceil(Math.max(capedLevel, 0.0)).toInt()
    }

    /**
     * Get the [VisibleTiles], given the visible area in pixels.
     *
     * @param viewport The [Viewport] which represents the visible area. Its values depend on the
     * scale.
     */
    fun getVisibleTiles(viewport: Viewport): VisibleTiles {
        val scaleAtLevel = scaleForLevel[currentLevel] ?: throw AssertionError()
        val relativeScale = scale / scaleAtLevel

        /* At the current level, row and col index have maximum values */
        val maxCol = (fullWidth * scaleAtLevel / tileSize).toInt()
        val maxRow = (fullHeight * scaleAtLevel / tileSize).toInt()

        fun Int.lowerThan(limit: Int): Int {
            return if (this <= limit) this else limit
        }

        val scaledTileSize = tileSize.toDouble() * relativeScale

        val colLeft = Math.floor(viewport.left / scaledTileSize).toInt().lowerThan(maxCol)
        val rowTop = Math.floor(viewport.top / scaledTileSize).toInt().lowerThan(maxRow)
        val colRight = (Math.ceil(viewport.right / scaledTileSize).toInt() - 1).lowerThan(maxCol)
        val rowBottom = (Math.ceil(viewport.bottom / scaledTileSize).toInt() - 1).lowerThan(maxRow)

        return VisibleTiles(currentLevel, colLeft, rowTop, colRight, rowBottom, subSample)
    }
}

/**
 * @param level 0-based level index
 */
data class VisibleTiles(var level: Int, val colLeft: Int, val rowTop: Int, val colRight: Int,
                        val rowBottom: Int, val subSample: Int = 0)