package com.peterlaurence.trekme.viewmodel.mapview

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peterlaurence.trekme.core.map.Map
import com.peterlaurence.trekme.core.map.domain.Route
import com.peterlaurence.trekme.core.track.toMarker
import com.peterlaurence.trekme.events.recording.GpxRecordEvents
import com.peterlaurence.trekme.events.recording.LiveRoutePause
import com.peterlaurence.trekme.events.recording.LiveRoutePoint
import com.peterlaurence.trekme.events.recording.LiveRouteStop
import com.peterlaurence.trekme.repositories.map.MapRepository
import com.peterlaurence.trekme.util.gpx.model.TrackPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * This view-model backs the feature that enables a fragment to be aware of a recording
 * (e.g a list of [TrackPoint]) potentially launched way before **and** to be aware of new recording
 * points as they arrive. The use case is for example:
 * 1- User opens one map (MapViewFragment)
 * 2- User launches a recording for the RecordingFragment.
 * 3- The "live route" starts drawing on the screen (MapViewFragment)
 * 4- For some reason the user leaves the MapViewFragment or even closes the app (without stopping
 *    the recording)
 * 5- User returns to his map and expects to see his "live route" still there.
 *
 * Actually it should work for any map: the "live route" can be drawn for any map while a recording
 * is running.
 *
 * **Implementation**
 *
 * A producer-consumer pattern is employed here. The producer is the gpx recording service, the
 * consumer is a coroutine inside this view-model. Between the two, the [GpxRecordEvents]
 * exposes a SharedFlow of events.
 *
 * The coroutine collects this SharedFlow, and adds new [TrackPoint]s to the [RouteBuilder] as they
 * arrive. If the coroutine receives a [LiveRouteStop] or [LiveRoutePause] event, it creates a new
 * [RouteBuilder]. So actually, the live route is a list of route, as each pause results in the
 * creation of a new route.
 * For each received event, the [route] is updated.
 *
 * The coroutine runs off UI thread. However the [MutableLiveData] triggers observers in the UI thread.
 */
@HiltViewModel
class InMapRecordingViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val gpxRecordEvents: GpxRecordEvents
) : ViewModel() {
    private val route = MutableLiveData<LiveRoute>()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val map = mapRepository.getCurrentMap() ?: return@launch

            var routeBuilderList = mutableListOf<RouteBuilder>()

            fun newRouteBuilder(): RouteBuilder {
                val routeBuilder = RouteBuilder(map)
                routeBuilderList.add(routeBuilder)
                return routeBuilder
            }

            var routeBuilder = newRouteBuilder()

            gpxRecordEvents.liveRouteFlow.collect {
                when (it) {
                    is LiveRoutePoint -> routeBuilder.add(it.pt)
                    LiveRouteStop -> {
                        routeBuilderList = mutableListOf()
                        routeBuilder = newRouteBuilder()
                    }
                    LiveRoutePause -> {
                        /* Add previous route builder */
                        routeBuilderList.add(routeBuilder)

                        /* Create and add a new route builder */
                        routeBuilder = newRouteBuilder()
                    }
                }

                route.postValue(
                    routeBuilderList.mapNotNull { builder ->
                        builder.route.takeIf { route -> route.routeMarkers.isNotEmpty() }
                    }
                )
            }
        }
    }

    fun getLiveRoute(): LiveData<LiveRoute> {
        return route
    }
}

private class RouteBuilder(val map: Map) {
    val route = Route()

    fun add(point: TrackPoint) {
        val marker = point.toMarker(map)
        route.addMarker(marker)
    }
}

/* A LiveRoute is just a list of route */
typealias LiveRoute = List<Route>