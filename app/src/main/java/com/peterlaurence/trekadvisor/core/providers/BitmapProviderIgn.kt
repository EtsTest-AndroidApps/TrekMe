package com.peterlaurence.trekadvisor.core.providers

import android.content.Context
import android.graphics.Bitmap
import com.peterlaurence.trekadvisor.core.mapsource.IGNCredentials
import com.peterlaurence.trekadvisor.core.providers.generic.GenericBitmapProviderAuth
import com.peterlaurence.trekadvisor.core.providers.layers.IgnLayers
import com.peterlaurence.trekadvisor.core.providers.urltilebuilder.UrlTileBuilderIgn
import com.qozix.tileview.graphics.BitmapProvider
import com.qozix.tileview.tiles.Tile


/**
 * Luckily, IGN's [WMTS service](https://geoservices.ign.fr/documentation/geoservices/wmts.html) has
 * a grid coordinates that is exactly the same as the one [TileView] uses. <br>
 * Consequently, to make a valid HTTP request, we just have to format the URL with raw zoom-level,
 * row and col numbers. <br>
 * Additional information have to be provided though, like IGN credentials.
 */
class BitmapProviderIgn(credentials: IGNCredentials, layer: String = IgnLayers.ScanExpressStandard.realName) : BitmapProvider {
    private val genericProvider: GenericBitmapProviderAuth

    init {
        val urlTileBuilder = UrlTileBuilderIgn(credentials.api ?: "", layer)
        genericProvider = GenericBitmapProviderAuth(urlTileBuilder, credentials.user ?: "", credentials.pwd ?: "")
    }


    override fun getBitmap(tile: Tile, p1: Context?): Bitmap? {
        val zoomLvl = tile.data as Int

        return genericProvider.getBitmap(zoomLvl, tile.row, tile.column)
    }
}