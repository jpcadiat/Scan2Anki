package com.scan2anki.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Crop169
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scan2anki.R
import com.scan2anki.ocr.OcrLine
import com.scan2anki.parse.ColumnParser
import com.scan2anki.parse.ColumnParser.ZoneRect
import com.scan2anki.vm.ZoneEditorViewModel
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.launch
import timber.log.Timber

/** Overlay colours, shared between the canvas and the legend so they cannot drift apart. */
private val FrontColor = Color(0xFF2E7D32)
private val BackColor = Color(0xFF1565C0)
private val UnpairedColor = Color(0xFFC62828)
private val IgnoredColor = Color(0xFF616161)
private val SplitColor = Color(0xFFF9A825)
private val ConnectorColor = Color(0xFF9E9E9E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneEditorScreen(
    sessionId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: ZoneEditorViewModel = hiltViewModel(),
    focusedPageId: Long? = null,
    useCloud: Boolean = false,
    restoreFromCache: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addingZone by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Selection?>(null) }
    var pendingSplitX by remember { mutableStateOf<Float?>(null) }
    var pendingZone by remember { mutableStateOf<ZoneRect?>(null) }
    // Which existing zone [pendingZone] is a live edit of; null means it is a brand-new zone
    // being drawn. Kept separate from [selection] so a drag never has to fake a selection.
    var pendingZoneIndex by remember { mutableStateOf<Int?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    val zoom = rememberZoomState()

    val currentOnDone by rememberUpdatedState(onDone)

    val undoLabel = stringResource(R.string.action_undo)
    val pageResetMsg = stringResource(R.string.undo_page_reset)
    val splitAddedMsg = stringResource(R.string.undo_split_added)
    val splitDeletedMsg = stringResource(R.string.undo_split_deleted)
    val zoneDeletedMsg = stringResource(R.string.undo_zone_deleted)
    val lineDeletedMsg = stringResource(R.string.undo_line_deleted)
    val zoneAddedMsg = stringResource(R.string.undo_zone_added)

    /** Runs [action], then offers a snackbar that rolls the page back to how it was. */
    fun withUndo(label: String, action: () -> Unit) {
        val snapshot = viewModel.snapshotCurrentPage()
        action()
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = label,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && snapshot != null) {
                Timber.d("ZoneEditorScreen: undo \"%s\"", label)
                viewModel.restore(snapshot)
                selection = null
            }
        }
    }

    LaunchedEffect(sessionId, focusedPageId, useCloud, restoreFromCache) {
        viewModel.init(sessionId, focusedPageId, useCloud, restoreFromCache)
    }
    LaunchedEffect(state.done) {
        Timber.d("ZoneEditorScreen: state.done changed to %b", state.done)
        if (state.done) currentOnDone()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(state.currentIndex) {
        addingZone = false
        selection = null
        pendingSplitX = null
        pendingZone = null
        pendingZoneIndex = null
        zoom.reset()
    }

    val currentPage = state.pages.getOrNull(state.currentIndex)
    val currentPageState by rememberUpdatedState(currentPage)

    val canReset = currentPage?.let { page ->
        page.layout.ignoreZones.isNotEmpty() ||
            page.deletedLines.isNotEmpty() ||
            page.layout.splitX != ColumnParser.detectColumnSplit(page.visibleLines)
    } ?: false

    val effectivePreview = remember(currentPage, pendingSplitX, pendingZone, pendingZoneIndex) {
        val page = currentPage ?: return@remember emptyList()
        val split = pendingSplitX ?: page.layout.splitX
        val zones = effectiveZones(page.layout.ignoreZones, pendingZone, pendingZoneIndex)
        ColumnParser.parseWithLayout(page.visibleLines, split, zones)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.zone_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        Timber.d("ZoneEditorScreen: back to capture")
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            Timber.d("ZoneEditorScreen: done")
                            viewModel.done()
                        },
                        enabled = !state.isProcessing,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.cd_done))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_reset_page)) },
                                enabled = canReset && !state.isProcessing,
                                leadingIcon = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
                                onClick = {
                                    Timber.d("ZoneEditorScreen: reset page")
                                    menuExpanded = false
                                    withUndo(pageResetMsg) { viewModel.resetPage() }
                                    selection = null
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            ZoneEditorActionBar(
                addingZone = addingZone,
                selection = selection,
                hasSplit = currentPage?.layout?.splitX != null,
                enabled = currentPage?.isLoaded == true && !state.isProcessing,
                onAddSplit = {
                    val page = currentPage ?: return@ZoneEditorActionBar
                    val at = ColumnParser.detectColumnSplit(page.visibleLines) ?: 0.5f
                    Timber.d("ZoneEditorScreen: add split at %.3f", at)
                    withUndo(splitAddedMsg) { viewModel.addSplit(at) }
                },
                onAddZone = {
                    Timber.d("ZoneEditorScreen: arm add-zone")
                    selection = null
                    addingZone = true
                },
                onCancelAddZone = {
                    addingZone = false
                    pendingZone = null
                    pendingZoneIndex = null
                },
                onDeleteSelection = {
                    val sel = selection ?: return@ZoneEditorActionBar
                    Timber.d("ZoneEditorScreen: delete %s", sel)
                    val label = when (sel) {
                        is Selection.Split -> splitDeletedMsg
                        is Selection.Zone -> zoneDeletedMsg
                        is Selection.Line -> lineDeletedMsg
                    }
                    withUndo(label) {
                        when (sel) {
                            is Selection.Split -> viewModel.removeSplit()
                            is Selection.Zone -> viewModel.removeIgnoreZone(sel.index)
                            is Selection.Line -> viewModel.deleteLine(sel.index)
                        }
                    }
                    selection = null
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (currentPage != null && currentPage.isLoaded) {
                ZoomableBox(
                    state = zoom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .aspectRatio(
                            currentPage.imageWidth.toFloat() / currentPage.imageHeight.toFloat(),
                        ),
                ) {
                    AsyncImage(
                        model = File(currentPage.page.imagePath),
                        contentDescription = stringResource(R.string.page_number, currentPage.page.order + 1),
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("zone-canvas")
                            .pointerInput(currentPage.page.id, addingZone) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val start = down.position
                                    val page = currentPageState
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    if (page == null || !page.isLoaded) return@awaitEachGesture

                                    // Touch tolerances are specified on screen; divide by the
                                    // zoom so they stay a constant physical size as you zoom in.
                                    val grab = zoom.touchSlopFor(24.dp.toPx())
                                    val edge = zoom.touchSlopFor(maxOf(16.dp.toPx(), 16f))
                                    // Smaller than grab/edge: lines can sit close together
                                    // vertically, so a large radius risks grabbing the neighbor.
                                    val lineRadius = zoom.touchSlopFor(12.dp.toPx())

                                    var dragMode = DragMode.NONE
                                    var resizeHandle: ResizeHandle? = null
                                    var anchor = start
                                    var moved = false
                                    var grabbedZone = -1
                                    // Whether the thing under the finger was already selected,
                                    // so a tap on it can toggle the selection back off.
                                    var wasSelected = false

                                    if (addingZone) {
                                        dragMode = DragMode.DRAW
                                    } else {
                                        val split = page.layout.splitX
                                        val zoneIdx = page.layout.ignoreZones.indexOfFirst { z ->
                                            start.x in (z.left * w - edge)..(z.right * w + edge) &&
                                                start.y in (z.top * h - edge)..(z.bottom * h + edge)
                                        }
                                        when {
                                            split != null && abs(start.x - split * w) <= grab -> {
                                                dragMode = DragMode.SPLIT
                                                wasSelected = selection is Selection.Split
                                                pendingSplitX = split
                                            }
                                            zoneIdx >= 0 -> {
                                                val zone = page.layout.ignoreZones[zoneIdx]
                                                val handle = handleAt(start, zone, w, h, edge)
                                                grabbedZone = zoneIdx
                                                wasSelected = selection == Selection.Zone(zoneIdx)
                                                pendingZone = zone
                                                pendingZoneIndex = zoneIdx
                                                dragMode = if (handle != null) {
                                                    resizeHandle = handle
                                                    DragMode.RESIZE
                                                } else {
                                                    DragMode.MOVE
                                                }
                                            }
                                            else -> dragMode = DragMode.NONE
                                        }
                                    }

                                    if (dragMode == DragMode.NONE) {
                                        // Nothing grabbable under the finger: this is a tap that
                                        // selects (or deselects) whatever is there.
                                        val up = waitForUpOrCancellation()
                                        if (up != null) {
                                            // Nearest line within radius, not plain containment:
                                            // lines have thin boxes, so a tap just outside one
                                            // should still hit it rather than falling through.
                                            val lineIdx = page.lines.indices.asSequence()
                                                .filter { it !in page.deletedLines }
                                                .map { i ->
                                                    val l = page.lines[i]
                                                    val dx = maxOf(l.left * w - start.x, 0f, start.x - l.right * w)
                                                    val dy = maxOf(l.top * h - start.y, 0f, start.y - l.bottom * h)
                                                    i to (dx * dx + dy * dy)
                                                }
                                                .filter { (_, distSq) -> distSq <= lineRadius * lineRadius }
                                                .minByOrNull { (_, distSq) -> distSq }
                                                ?.first
                                            val tapped = lineIdx?.let { Selection.Line(it) }
                                            selection = if (tapped == selection) null else tapped
                                            Timber.d("ZoneEditorScreen: tap selection=%s", selection)
                                        }
                                        return@awaitEachGesture
                                    }

                                    drag(down.id) { change ->
                                        change.consume()
                                        moved = true
                                        when (dragMode) {
                                            DragMode.SPLIT -> {
                                                val dx = change.position.x - anchor.x
                                                pendingSplitX =
                                                    ((pendingSplitX ?: 0.5f) + dx / w).coerceIn(0f, 1f)
                                                anchor = change.position
                                            }
                                            DragMode.MOVE -> pendingZone?.let { z ->
                                                val dx = change.position.x - anchor.x
                                                val dy = change.position.y - anchor.y
                                                val zw = z.right - z.left
                                                val zh = z.bottom - z.top
                                                val left = (z.left + dx / w).coerceIn(0f, 1f - zw)
                                                val top = (z.top + dy / h).coerceIn(0f, 1f - zh)
                                                pendingZone = ZoneRect(left, top, left + zw, top + zh)
                                                anchor = change.position
                                            }
                                            DragMode.RESIZE -> pendingZone?.let { z ->
                                                resizeHandle?.let { handle ->
                                                    pendingZone = resize(
                                                        z, handle,
                                                        (change.position.x / w).coerceIn(0f, 1f),
                                                        (change.position.y / h).coerceIn(0f, 1f),
                                                    )
                                                }
                                            }
                                            DragMode.DRAW -> {
                                                pendingZone = ZoneRect(
                                                    left = minOf(anchor.x, change.position.x) / w,
                                                    top = minOf(anchor.y, change.position.y) / h,
                                                    right = maxOf(anchor.x, change.position.x) / w,
                                                    bottom = maxOf(anchor.y, change.position.y) / h,
                                                )
                                            }
                                            DragMode.NONE -> Unit
                                        }
                                    }

                                    // Commit on release -- the user has already watched the
                                    // result track their finger, so there is nothing to confirm.
                                    when (dragMode) {
                                        DragMode.SPLIT ->
                                            if (moved) {
                                                pendingSplitX?.let { viewModel.setSplitX(it) }
                                                selection = Selection.Split
                                            } else {
                                                selection = if (wasSelected) null else Selection.Split
                                            }
                                        DragMode.MOVE ->
                                            if (moved) {
                                                pendingZone?.let {
                                                    viewModel.updateIgnoreZone(grabbedZone, it)
                                                }
                                                selection = Selection.Zone(grabbedZone)
                                            } else {
                                                selection =
                                                    if (wasSelected) null else Selection.Zone(grabbedZone)
                                            }
                                        DragMode.RESIZE ->
                                            if (moved) {
                                                val zone = pendingZone
                                                if (zone != null && isZoneSizeValid(zone, w, h)) {
                                                    viewModel.updateIgnoreZone(grabbedZone, zone)
                                                } else {
                                                    Timber.v("ZoneEditorScreen: discarding undersized resize")
                                                }
                                                selection = Selection.Zone(grabbedZone)
                                            } else {
                                                selection =
                                                    if (wasSelected) null else Selection.Zone(grabbedZone)
                                            }
                                        DragMode.DRAW -> {
                                            val zone = pendingZone
                                            val bigEnough = zone != null && isZoneSizeValid(zone, w, h)
                                            if (bigEnough) {
                                                withUndo(zoneAddedMsg) { viewModel.addIgnoreZone(zone) }
                                            } else {
                                                Timber.v("ZoneEditorScreen: discarding tiny zone")
                                            }
                                            addingZone = false
                                        }
                                        DragMode.NONE -> Unit
                                    }
                                    pendingSplitX = null
                                    pendingZone = null
                                    pendingZoneIndex = null
                                }
                            },
                    ) {
                        val w = size.width
                        val h = size.height
                        val effectiveSplit = pendingSplitX ?: currentPage.layout.splitX
                        val sel = selection
                        val effectiveZones = effectiveZones(
                            currentPage.layout.ignoreZones,
                            pendingZone,
                            pendingZoneIndex,
                        )
                        // A zone being drawn from scratch is appended, so it is the last one.
                        val drawingIndex =
                            if (pendingZone != null && pendingZoneIndex == null) {
                                effectiveZones.lastIndex
                            } else {
                                -1
                            }
                        val visibleOriginalIndices =
                            currentPage.lines.indices.filter { it !in currentPage.deletedLines }
                        val regions = ColumnParser.classifyLines(currentPage.visibleLines, effectiveSplit)
                        val frontPositions = mutableListOf<Int>()
                        val backPositions = mutableListOf<Int>()
                        currentPage.visibleLines.forEachIndexed { pos, line ->
                            if (effectiveZones.any { it.contains(line.centerX, line.centerY) }) {
                                return@forEachIndexed
                            }
                            when (regions[pos]) {
                                ColumnParser.LineRegion.LEFT -> frontPositions += pos
                                ColumnParser.LineRegion.RIGHT -> backPositions += pos
                                ColumnParser.LineRegion.FULL_WIDTH -> Unit
                            }
                        }
                        val sortedFrontPositions = frontPositions.sortedBy { currentPage.visibleLines[it].centerY }
                        val sortedBackPositions = backPositions.sortedBy { currentPage.visibleLines[it].centerY }
                        val pairedCount = minOf(sortedFrontPositions.size, sortedBackPositions.size)
                        val unpairedPositions =
                            (sortedFrontPositions.drop(pairedCount) + sortedBackPositions.drop(pairedCount)).toSet()
                        ColumnParser.pairLines(
                            sortedFrontPositions.map { currentPage.visibleLines[it] },
                            sortedBackPositions.map { currentPage.visibleLines[it] },
                        ).forEach { (front, back) ->
                            drawLine(
                                color = ConnectorColor,
                                start = Offset(w * front.right, h * front.centerY),
                                end = Offset(w * back.left, h * back.centerY),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                        visibleOriginalIndices.zip(currentPage.visibleLines)
                            .forEachIndexed { pos, (origIndex, line) ->
                                val isSelected = sel is Selection.Line && sel.index == origIndex
                                val ignored = effectiveZones.any { it.contains(line.centerX, line.centerY) }
                                val color = when {
                                    ignored -> IgnoredColor
                                    pos in unpairedPositions -> UnpairedColor
                                    regions[pos] == ColumnParser.LineRegion.LEFT -> FrontColor
                                    regions[pos] == ColumnParser.LineRegion.RIGHT -> BackColor
                                    else -> UnpairedColor
                                }
                                drawRect(
                                    color = color,
                                    topLeft = Offset(w * line.left, h * line.top),
                                    size = Size(w * (line.right - line.left), h * (line.bottom - line.top)),
                                    style = Stroke(width = if (isSelected) 4.dp.toPx() else 2.dp.toPx()),
                                )
                            }
                        effectiveSplit?.let { split ->
                            drawLine(
                                color = SplitColor,
                                start = Offset(w * split, 0f),
                                end = Offset(w * split, h),
                                strokeWidth = if (sel is Selection.Split) 5.dp.toPx() else 3.dp.toPx(),
                                pathEffect = if (pendingSplitX != null) {
                                    PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                } else {
                                    null
                                },
                            )
                        }
                        effectiveZones.forEachIndexed { index, zone ->
                            val isSelected = sel is Selection.Zone && sel.index == index
                            val isPending = index == drawingIndex || index == pendingZoneIndex
                            drawRect(
                                color = IgnoredColor.copy(alpha = if (isSelected || isPending) 0.5f else 0.3f),
                                topLeft = Offset(w * zone.left, h * zone.top),
                                size = Size(w * (zone.right - zone.left), h * (zone.bottom - zone.top)),
                            )
                            if (isSelected || isPending) {
                                drawRect(
                                    color = IgnoredColor,
                                    topLeft = Offset(w * zone.left, h * zone.top),
                                    size = Size(
                                        w * (zone.right - zone.left),
                                        h * (zone.bottom - zone.top),
                                    ),
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = if (isPending) {
                                            PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                                        } else {
                                            null
                                        },
                                    ),
                                )
                            }
                        }
                    }

                }
                OverlayLegend()
                // Only worth its vertical space when there is somewhere to page to. Deliberately
                // not overlaid on the image: it would cover the page on short/wide scans.
                if (state.pages.size > 1) {
                    PagePager(
                        index = state.currentIndex,
                        count = state.pages.size,
                        enabled = focusedPageId == null && !state.isProcessing,
                        onPrevious = {
                            Timber.d("ZoneEditorScreen: previous page (from index=%d)", state.currentIndex)
                            viewModel.setCurrentPage(state.currentIndex - 1)
                        },
                        onNext = {
                            Timber.d("ZoneEditorScreen: next page (from index=%d)", state.currentIndex)
                            viewModel.setCurrentPage(state.currentIndex + 1)
                        },
                    )
                }
            } else if (currentPage != null && state.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
                }
            } else if (currentPage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    OutlinedButton(
                        onClick = {
                            Timber.d("ZoneEditorScreen: retry loading page")
                            viewModel.retryCurrentPage()
                        },
                        enabled = !state.isProcessing,
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }

            if (currentPage != null) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(effectivePreview.size) { index ->
                        PreviewRow(effectivePreview[index])
                    }
                }
            }
        }
    }
}

/** Compact page stepper, rendered only for multi-page sessions. */
@Composable
private fun PagePager(
    index: Int,
    count: Int,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = enabled && index > 0,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_previous_page))
        }
        Text(
            stringResource(R.string.page_of_count, index + 1, count),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(
            onClick = onNext,
            enabled = enabled && index < count - 1,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.cd_next_page))
        }
    }
}

/** Fixed-height action bar: creation tools when idle, a delete action when something is picked. */
@Composable
internal fun ZoneEditorActionBar(
    addingZone: Boolean,
    selection: Selection?,
    hasSplit: Boolean,
    enabled: Boolean,
    onAddSplit: () -> Unit,
    onAddZone: () -> Unit,
    onCancelAddZone: () -> Unit,
    onDeleteSelection: () -> Unit,
    windowInsets: WindowInsets = WindowInsets.navigationBars,
) {
    // The Scaffold places the bottomBar flush with the screen edge, so the bar
    // must pad itself by the navigation-bar height (3-button nav would otherwise
    // cover the buttons; the Surface background still extends behind the bar).
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                addingZone -> {
                    Text(
                        stringResource(R.string.hint_drag_to_ignore),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onCancelAddZone) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Text(stringResource(R.string.action_cancel), modifier = Modifier.padding(start = 4.dp))
                    }
                }
                selection != null -> {
                    val deleteLabel = when (selection) {
                        is Selection.Split -> stringResource(R.string.action_delete_split)
                        is Selection.Zone -> stringResource(R.string.action_delete_zone)
                        is Selection.Line -> stringResource(R.string.action_delete_line)
                    }
                    FilledTonalButton(onClick = onDeleteSelection) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text(
                            deleteLabel,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                else -> {
                    if (!hasSplit) {
                        OutlinedButton(onClick = onAddSplit, enabled = enabled) {
                            Icon(Icons.Filled.VerticalSplit, contentDescription = null)
                            Text(stringResource(R.string.action_add_split), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    OutlinedButton(onClick = onAddZone, enabled = enabled) {
                        Icon(Icons.Filled.Crop169, contentDescription = null)
                        Text(stringResource(R.string.action_add_zone), modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

/** Explains the overlay colours, which are otherwise pure guesswork. */
@Composable
private fun OverlayLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(FrontColor, stringResource(R.string.label_front))
        LegendDot(BackColor, stringResource(R.string.label_back))
        LegendDot(UnpairedColor, stringResource(R.string.legend_unpaired))
        LegendDot(IgnoredColor, stringResource(R.string.legend_ignored))
        LegendDot(SplitColor, stringResource(R.string.legend_split))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PreviewRow(row: ColumnParser.ParsedRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (row.isUnpaired) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.front,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text("→", modifier = Modifier.padding(horizontal = 8.dp))
        Text(
            text = row.back,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (row.isUnpaired) {
            Text(
                stringResource(R.string.label_unpaired_lowercase),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Folds an in-flight zone edit into the committed list: [pendingIndex] replaces that entry,
 * a null index means [pending] is a new zone being drawn and is appended.
 */
private fun effectiveZones(
    committed: List<ZoneRect>,
    pending: ZoneRect?,
    pendingIndex: Int?,
): List<ZoneRect> = when {
    pending == null -> committed
    pendingIndex != null -> committed.mapIndexed { i, z -> if (i == pendingIndex) pending else z }
    else -> committed + pending
}

/** A zone under 2% of the canvas in either dimension is too small to be worth keeping. */
private fun isZoneSizeValid(zone: ZoneRect, w: Float, h: Float): Boolean =
    (zone.right - zone.left) * w >= w * 0.02f && (zone.bottom - zone.top) * h >= h * 0.02f

private fun handleAt(at: Offset, zone: ZoneRect, w: Float, h: Float, edge: Float): ResizeHandle? {
    val l = zone.left * w
    val t = zone.top * h
    val r = zone.right * w
    val b = zone.bottom * h
    return when {
        abs(at.x - l) <= edge && abs(at.y - t) <= edge -> ResizeHandle.TOP_LEFT
        abs(at.x - r) <= edge && abs(at.y - t) <= edge -> ResizeHandle.TOP_RIGHT
        abs(at.x - l) <= edge && abs(at.y - b) <= edge -> ResizeHandle.BOTTOM_LEFT
        abs(at.x - r) <= edge && abs(at.y - b) <= edge -> ResizeHandle.BOTTOM_RIGHT
        abs(at.x - l) <= edge -> ResizeHandle.LEFT
        abs(at.x - r) <= edge -> ResizeHandle.RIGHT
        abs(at.y - t) <= edge -> ResizeHandle.TOP
        abs(at.y - b) <= edge -> ResizeHandle.BOTTOM
        else -> null
    }
}

private fun resize(zone: ZoneRect, handle: ResizeHandle, nx: Float, ny: Float): ZoneRect {
    var left = zone.left
    var right = zone.right
    var top = zone.top
    var bottom = zone.bottom
    when (handle) {
        ResizeHandle.LEFT, ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT -> left = minOf(nx, zone.right)
        ResizeHandle.RIGHT, ResizeHandle.TOP_RIGHT, ResizeHandle.BOTTOM_RIGHT -> right = maxOf(nx, zone.left)
        else -> Unit
    }
    when (handle) {
        ResizeHandle.TOP, ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT -> top = minOf(ny, zone.bottom)
        ResizeHandle.BOTTOM, ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM_RIGHT -> bottom = maxOf(ny, zone.top)
        else -> Unit
    }
    return ZoneRect(left, top, right, bottom)
}

private enum class DragMode { NONE, SPLIT, MOVE, RESIZE, DRAW }

private enum class ResizeHandle {
    LEFT, RIGHT, TOP, BOTTOM,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
}

internal sealed interface Selection {
    data object Split : Selection
    data class Zone(val index: Int) : Selection
    data class Line(val index: Int) : Selection
}
