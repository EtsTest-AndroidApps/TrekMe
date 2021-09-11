package com.peterlaurence.trekme.core.map.domain

data class Landmark(
    var name: String, var lat: Double, var lon: Double,
    var proj_x: Double?, var proj_y: Double?, var comment: String
)