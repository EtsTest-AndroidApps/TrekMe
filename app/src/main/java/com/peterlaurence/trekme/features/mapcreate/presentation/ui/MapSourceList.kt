package com.peterlaurence.trekme.features.mapcreate.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.AlertDialog
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.peterlaurence.trekme.R
import com.peterlaurence.trekme.core.mapsource.WmtsSource
import com.peterlaurence.trekme.ui.common.OnBoardingTip
import com.peterlaurence.trekme.ui.common.PopupOrigin
import com.peterlaurence.trekme.features.mapcreate.presentation.viewmodel.MapSourceListViewModel


@Composable
private fun SourceList(sources: List<WmtsSource>, onSourceClick: (WmtsSource) -> Unit) {
    LazyColumn {
        items(sources) { wmtsSource ->
            SourceRow(wmtsSource, onSourceClick)
            Divider(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun SourceRow(source: WmtsSource, onSourceClick: (WmtsSource) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(106.dp)
            .clickable { onSourceClick(source) }
    ) {
        Column(
            Modifier
                .padding(start = 16.dp, top = 24.dp, end = 8.dp)
                .weight(1f)
        ) {
            Text(
                text = getTitleForSource(source),
                fontSize = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colorResource(id = R.color.colorPrimaryTextBlack)
            )
            Text(
                text = getSubtitleForSource(source),
                color = colorResource(id = R.color.colorPrimaryTextBlack)
            )

            if (source == WmtsSource.IGN) {
                val annotatedString = buildAnnotatedString {
                    val text = stringResource(id = R.string.ign_legal_notice_btn)
                    append(text)
                    addStyle(
                        style = SpanStyle(
                            color = colorResource(id = R.color.colorPrimaryTextBlack),
                            fontSize = 12.sp,
                            textDecoration = TextDecoration.Underline
                        ), start = 0, end = text.length
                    )
                }
                val openDialog = remember { mutableStateOf(false) }

                if (openDialog.value) {
                    AlertDialog(
                        onDismissRequest = {
                            openDialog.value = false
                        },
                        text = {
                            Text(text = stringResource(id = R.string.ign_legal_notice))
                        },
                        buttons = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 16.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { openDialog.value = false }
                                ) {
                                    Text(stringResource(id = R.string.ok_dialog))
                                }
                            }
                        }
                    )
                }

                ClickableText(
                    text = annotatedString,
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = { openDialog.value = true }
                )
            }
        }
        Image(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 8.dp, end = 16.dp)
                .size(90.dp),
            painter = getImageForSource(source),
            contentDescription = stringResource(id = R.string.accessibility_mapsource_image)
        )
    }
}

@Composable
private fun getTitleForSource(source: WmtsSource): String {
    return when (source) {
        WmtsSource.IGN -> stringResource(R.string.ign_source)
        WmtsSource.SWISS_TOPO -> stringResource(R.string.swiss_topo_source)
        WmtsSource.OPEN_STREET_MAP -> stringResource(R.string.open_street_map_source)
        WmtsSource.USGS -> stringResource(R.string.usgs_map_source)
        WmtsSource.IGN_SPAIN -> stringResource(R.string.ign_spain_source)
        WmtsSource.ORDNANCE_SURVEY -> stringResource(R.string.ordnance_survey_source)
    }
}

@Composable
private fun getSubtitleForSource(source: WmtsSource): String {
    return when (source) {
        WmtsSource.IGN -> stringResource(R.string.ign_source_description)
        WmtsSource.SWISS_TOPO -> stringResource(R.string.swiss_topo_source_description)
        WmtsSource.OPEN_STREET_MAP -> stringResource(R.string.open_street_map_source_description)
        WmtsSource.USGS -> stringResource(R.string.usgs_map_source_description)
        WmtsSource.IGN_SPAIN -> stringResource(R.string.ign_spain_source_description)
        WmtsSource.ORDNANCE_SURVEY -> stringResource(R.string.ordnance_survey_source_description)
    }
}

@Composable
private fun getImageForSource(source: WmtsSource): Painter {
    return when (source) {
        WmtsSource.IGN -> painterResource(R.drawable.ign_logo)
        WmtsSource.SWISS_TOPO -> painterResource(R.drawable.ic_swiss_topo_logo)
        WmtsSource.OPEN_STREET_MAP -> painterResource(R.drawable.openstreetmap_logo)
        WmtsSource.USGS -> painterResource(R.drawable.usgs_logo)
        WmtsSource.IGN_SPAIN -> painterResource(R.drawable.ign_spain_logo)
        WmtsSource.ORDNANCE_SURVEY -> painterResource(R.drawable.ordnance_survey_logo)
    }
}

@Composable
fun MapSourceListStateful(
    viewModel: MapSourceListViewModel,
    onSourceClick: (WmtsSource) -> Unit
) {
    val sourceList by viewModel.sourceList
    var showOnBoarding by viewModel.showOnBoarding

    BoxWithConstraints {
        SourceList(sourceList, onSourceClick)
        if (showOnBoarding) {
            OnBoardingTip(
                modifier = Modifier
                    .width(min(maxWidth * 0.8f, 310.dp))
                    .padding(bottom = 16.dp)
                    .align(Alignment.BottomCenter),
                popupOrigin = PopupOrigin.BottomCenter,
                text = stringResource(
                    id = R.string.onboarding_map_create
                ),
                delayMs = 500,
                onAcknowledge = { showOnBoarding = false }
            )
        }
    }
}
