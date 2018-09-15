package com.peterlaurence.trekadvisor.util.gpx.model

import com.peterlaurence.trekadvisor.core.track.TrackStatistics

/**
 * Represents a track - an ordered list of Track Segment describing a path.
 *
 * @author peterLaurence on 12/02/17.
 */
data class Track @JvmOverloads constructor(
        val trackSegments: List<TrackSegment>,
        val name: String = "",
        var statistics: TrackStatistics? = null
)
