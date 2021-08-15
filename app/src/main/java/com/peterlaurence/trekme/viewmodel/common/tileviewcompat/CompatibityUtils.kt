package com.peterlaurence.trekme.viewmodel.common.tileviewcompat

import android.util.Log
import com.peterlaurence.trekme.core.map.Map
import com.peterlaurence.trekme.core.map.TileStream
import com.peterlaurence.trekme.core.map.TileStreamProvider
import java.io.File
import java.io.FileInputStream
import ovh.plrapps.mapcompose.core.TileStreamProvider as MapComposeTileStreamProvider
import ovh.plrapps.mapview.core.TileStreamProvider as MapViewTileStreamProvider


/**
 * This utility function converts the [Map]'s [TileStreamProvider] into whatever's type needed by
 * the view that fragments use to display tiles.
 * For instance, fragments use MapView, so the returned type is [MapViewTileStreamProvider].
 */
fun makeMapViewTileStreamProvider(map: Map): MapViewTileStreamProvider? {
    return if (map.origin != null) {
        MapViewTileStreamProvider { row, col, zoomLvl ->
            val relativePathString = "$zoomLvl${File.separator}$row${File.separator}$col${map.imageExtension}"

            try {
                FileInputStream(File(map.directory, relativePathString))
            } catch (e: Exception) {
                null
            }
        }
    } else {
        Log.e(TAG, "Unknown map origin ${map.origin}")
        null
    }
}

fun TileStreamProvider.toMapViewTileStreamProvider(): MapViewTileStreamProvider {
    return ovh.plrapps.mapview.core.TileStreamProvider { row, col, zoomLvl ->
        val tileResult = this@toMapViewTileStreamProvider.getTileStream(row, col, zoomLvl)
        (tileResult as? TileStream)?.tileStream
    }
}

fun TileStreamProvider.toMapComposeTileStreamProvider(): MapComposeTileStreamProvider {
    return MapComposeTileStreamProvider { row, col, zoomLvl ->
        val tileResult = this@toMapComposeTileStreamProvider.getTileStream(row, col, zoomLvl)
        (tileResult as? TileStream)?.tileStream
    }
}

const val TAG = "CompatibilityUtils.kt"