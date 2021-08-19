package com.peterlaurence.trekme.ui.mapcreate.wmtsfragment.components

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.findFragment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peterlaurence.trekme.R
import com.peterlaurence.trekme.ui.mapcreate.wmtsfragment.GoogleMapWmtsViewFragment
import com.peterlaurence.trekme.ui.theme.TrekMeTheme
import com.peterlaurence.trekme.viewmodel.mapcreate.*
import ovh.plrapps.mapcompose.api.DefaultCanvas
import ovh.plrapps.mapcompose.api.fullSize
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.ui.MapUI
import ovh.plrapps.mapcompose.ui.state.MapState
import kotlin.math.abs
import kotlin.math.min

@Composable
fun GoogleMapWmtsUI(wmtsState: WmtsState) {
    when (wmtsState) {
        is MapReady -> {
            MapUI(state = wmtsState.mapState)
        }
        is Loading -> {

        }
        is AreaSelection -> {
            MapUI(state = wmtsState.mapState) {
                val mapState = wmtsState.mapState
                Area(
                    modifier = Modifier,
                    mapState = mapState,
                    backgroundColor = colorResource(id = R.color.colorBackgroundAreaView),
                    strokeColor = colorResource(id = R.color.colorStrokeAreaView),
                    p1 = with(wmtsState.areaUiController) {
                        Offset(
                            (p1x * mapState.fullSize.width).toFloat(),
                            (p1y * mapState.fullSize.height).toFloat()
                        )
                    },
                    p2 = with(wmtsState.areaUiController) {
                        Offset(
                            (p2x * mapState.fullSize.width).toFloat(),
                            (p2y * mapState.fullSize.height).toFloat()
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun Area(
    modifier: Modifier,
    mapState: MapState,
    backgroundColor: Color,
    strokeColor: Color,
    p1: Offset,
    p2: Offset
) {
    DefaultCanvas(
        modifier = modifier,
        mapState = mapState
    ) {
        val topLeft = Offset(min(p1.x, p2.x), min(p1.y, p2.y))

        drawRect(
            backgroundColor,
            topLeft = topLeft,
            size = Size(abs(p2.x - p1.x), abs(p2.y - p1.y))
        )
        drawRect(
            strokeColor, topLeft = topLeft, size = Size(abs(p2.x - p1.x), abs(p2.y - p1.y)),
            style = Stroke(width = 1.dp.toPx() / mapState.scale)
        )
    }
}

class GoogleMapWmtsUiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AbstractComposeView(context, attrs, defStyle) {

    @Composable
    override fun Content() {
        val viewModel: GoogleMapWmtsViewModel =
            viewModel(findFragment<GoogleMapWmtsViewFragment>().requireActivity())
        val state by viewModel.state.collectAsState()

        TrekMeTheme {
            GoogleMapWmtsUI(state)
        }
    }
}