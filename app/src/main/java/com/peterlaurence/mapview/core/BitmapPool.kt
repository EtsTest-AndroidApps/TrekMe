package com.peterlaurence.mapview.core

import android.graphics.Bitmap
import java.util.*

class BitmapPool {
    private val pool = LinkedList<Bitmap>()

    fun getBitmap(): Bitmap? {
        return if (pool.isNotEmpty()) {
            pool[0]
        } else {
            null
        }
    }

    fun putBitmap(bitmap: Bitmap) {
        pool.add(bitmap)
    }
}