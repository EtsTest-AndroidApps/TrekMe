package com.peterlaurence.trekme.features.map.presentation.viewmodel.layers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.peterlaurence.trekme.core.location.Location
import com.peterlaurence.trekme.core.map.Map
import com.peterlaurence.trekme.core.map.MapBounds
import com.peterlaurence.trekme.core.settings.RotationMode
import com.peterlaurence.trekme.core.settings.Settings
import com.peterlaurence.trekme.features.map.presentation.viewmodel.DataState
import com.peterlaurence.trekme.ui.common.PositionOrientationMarker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.plrapps.mapcompose.api.*
import ovh.plrapps.mapcompose.ui.state.MapState

class LocationOrientationLayer(
    private val scope: CoroutineScope,
    private val settings: Settings,
    private val dataStateFlow: Flow<DataState>
) {
    private var hasCenteredOnFirstLocation = false
    val locationFlow = MutableSharedFlow<Location>(1, 0, BufferOverflow.DROP_OLDEST)

    /* Internal angle data flow, which depends the orientation and the display screen angle. */
    private val angleFlow = MutableSharedFlow<Float>(1, 0, BufferOverflow.DROP_OLDEST)

    /* Represents the arrow angle state, which also depends on settings.
     * When the value is null, the orientation arrow isn't displayed. */
    private val arrowAngleState = mutableStateOf<Float?>(null)

    val isLockedOnPosition = mutableStateOf(false)

    init {
        locationFlow.map { loc ->
            val (map, mapState) = dataStateFlow.first()
            onLocation(loc, mapState, map)
        }.launchIn(scope)

        scope.launch {
            /* At every map, orientation setting, and rotation mode change:
             * - collect the angle flow, if orientation is enabled,
             * - hide the orientation arrow and align to the north, if orientation is disabled.
             */
            combine(
                dataStateFlow,
                settings.getOrientationVisibility(),
                settings.getRotationMode()
            ) { dataState, showOrientation, rotationMode ->
                Triple(dataState, showOrientation, rotationMode)
            }.collectLatest { (dataState, showOrientation, rotationMode) ->
                val mapState = dataState.mapState
                applyRotationMode(mapState, rotationMode)

                if (showOrientation) {
                    angleFlow.collect { angle ->
                        if (rotationMode == RotationMode.FOLLOW_ORIENTATION) {
                            dataState.mapState.rotation = -angle
                        }
                        arrowAngleState.value = angle
                    }
                } else {
                    if (rotationMode == RotationMode.FOLLOW_ORIENTATION) {
                        dataState.mapState.rotateTo(0f)
                    }
                    arrowAngleState.value = null
                }
            }
        }

        /* At every map change, set the internal flag */
        dataStateFlow.map {
            hasCenteredOnFirstLocation = false
        }.launchIn(scope)
    }

    fun onLocation(location: Location) {
        locationFlow.tryEmit(location)
    }

    fun onOrientation(intrinsicAngle: Double, displayRotation: Int) {
        val orientation = (Math.toDegrees(intrinsicAngle) + 360 + displayRotation).toFloat() % 360
        angleFlow.tryEmit(orientation)
    }

    fun toggleLockedOnPosition() {
        isLockedOnPosition.value = !isLockedOnPosition.value
    }

    fun centerOnPosition() = scope.launch {
        val mapState = dataStateFlow.first().mapState
        centerOnPosMarker(mapState)
    }

    private fun onLocation(location: Location, mapState: MapState, map: Map) {
        scope.launch {
            /* Project lat/lon off UI thread */
            val projectedValues = withContext(Dispatchers.Default) {
                map.projection?.doProjection(location.latitude, location.longitude)
            }

            /* Update the position */
            val mapBounds = map.mapBounds
            if (projectedValues != null) {
                val X = projectedValues[0]
                val Y = projectedValues[1]
                if (mapBounds.contains(X, Y)) {
                    updatePosition(mapState, mapBounds, X, Y)
                }
            }
        }
    }

    private suspend fun updatePosition(
        mapState: MapState,
        mapBounds: MapBounds,
        X: Double,
        Y: Double
    ) {
        updatePositionMarker(mapState, mapBounds, X, Y)

        if (!hasCenteredOnFirstLocation) {
            centerOnPosMarker(mapState)
            hasCenteredOnFirstLocation = true
        }
    }

    private suspend fun centerOnPosMarker(mapState: MapState) {
        val scaleCentered = getScaleCentered().first()
        val defineScaleCentered = settings.getDefineScaleCentered().first()
        if (defineScaleCentered) {
            mapState.centerOnMarker(positionMarkerId, scaleCentered)
        } else {
            mapState.centerOnMarker(positionMarkerId)
        }
    }

    /**
     * Update the position on the map. The first time we update the position, we add the
     * position marker.
     *
     * @param X the projected X coordinate
     * @param Y the projected Y coordinate
     */
    private suspend fun updatePositionMarker(
        mapState: MapState,
        mapBounds: MapBounds,
        X: Double,
        Y: Double
    ) {
        val x = normalize(X, mapBounds.X0, mapBounds.X1)
        val y = normalize(Y, mapBounds.Y0, mapBounds.Y1)

        if (mapState.hasMarker(positionMarkerId)) {
            mapState.moveMarker(positionMarkerId, x, y)
        } else {
            mapState.addMarker(
                positionMarkerId,
                x,
                y,
                relativeOffset = Offset(-0.5f, -0.5f),
                clickable = false
            ) {
                val angle by arrowAngleState
                PositionOrientationMarker(angle = angle?.let { it + mapState.rotation })
            }
        }

        if (isLockedOnPosition.value) {
            mapState.scrollTo(x, y)
        }
    }

    private fun normalize(t: Double, min: Double, max: Double): Double {
        return (t - min) / (max - min)
    }

    private fun MapBounds.contains(x: Double, y: Double): Boolean {
        return x in X0..X1 && y in Y1..Y0
    }

    private fun getScaleCentered(): Flow<Float> {
        return settings.getScaleRatioCentered()
            .combine(settings.getMaxScale()) { scaleRatio, maxScale ->
                scaleRatio * maxScale / 100f
            }
    }

    private fun applyRotationMode(mapState: MapState, rotationMode: RotationMode) {
        when (rotationMode) {
            RotationMode.NONE -> {
                mapState.rotation = 0f
                mapState.disableRotation()
            }
            RotationMode.FOLLOW_ORIENTATION -> {
                mapState.disableRotation()
            }
            RotationMode.FREE -> mapState.enableRotation()
        }
    }
}

const val positionMarkerId = "position"