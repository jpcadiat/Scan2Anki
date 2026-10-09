package com.scan2anki.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scan2anki.R
import com.scan2anki.data.Page
import com.scan2anki.data.WordPair
import com.scan2anki.parse.OcrCleanup
import com.scan2anki.vm.ReviewViewModel
import com.scan2anki.vm.SendResult
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    sessionId: Long,
    onBack: () -> Unit,
    onRerunOcr: (pageId: Long, useCloud: Boolean) -> Unit,
    onViewZones: (pageId: Long) -> Unit,
    onSentSuccessfully: () -> Unit = {},
    onStartNewSession: () -> Unit = {},
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCleanupSheet by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val ankiPermissionDeniedMsg = stringResource(R.string.error_anki_permission_denied)
    val cardsSentMsg = stringResource(R.string.msg_cards_sent)
    val rowDeletedMsg = stringResource(R.string.msg_row_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val sendToAnkiDroid = rememberAnkiPermissionAction(
        onGranted = { viewModel.sendToAnkiDroid() },
        onDenied = { scope.launch { snackbarHostState.showSnackbar(ankiPermissionDeniedMsg) } },
    )
    val browseDecks = rememberAnkiPermissionAction(
        onGranted = viewModel::openDeckPicker,
        onDenied = { scope.launch { snackbarHostState.showSnackbar(ankiPermissionDeniedMsg) } },
    )
    val browseNoteTypes = rememberAnkiPermissionAction(
        onGranted = viewModel::openNoteTypePicker,
        onDenied = { scope.launch { snackbarHostState.showSnackbar(ankiPermissionDeniedMsg) } },
    )
    BackHandler(onBack = onBack)
    LaunchedEffect(sessionId) { viewModel.init(sessionId) }
    LaunchedEffect(state.sendResult) {
        when (state.sendResult) {
            SendResult.SENT -> {
                snackbarHostState.showSnackbar(cardsSentMsg)
                viewModel.consumeSendResult()
                onSentSuccessfully()
            }
            null -> Unit
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val wordCount = state.pairs.count { !it.isHeader }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.review_title))
                            Text(
                                pluralStringResource(R.plurals.word_count, wordCount, wordCount),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            Timber.d("ReviewScreen: open cleanup sheet")
                            showCleanupSheet = true
                        }) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = stringResource(R.string.cd_clean_up))
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
                                    text = { Text(stringResource(R.string.menu_swap_columns)) },
                                    leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                                    onClick = {
                                        Timber.d("ReviewScreen: swap columns")
                                        menuExpanded = false
                                        viewModel.swapColumns()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_add_row)) },
                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                    onClick = {
                                        Timber.d("ReviewScreen: add row")
                                        menuExpanded = false
                                        viewModel.addPair()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_start_new_scan)) },
                                    leadingIcon = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
                                    onClick = {
                                        Timber.i("ReviewScreen: starting a new scan, discarding this session")
                                        menuExpanded = false
                                        onStartNewSession()
                                    },
                                )
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        // A FAB has no enabled flag, so re-entry is guarded here instead.
                        if (state.isProcessing) {
                            Timber.d("ReviewScreen: ignoring send while processing")
                            return@ExtendedFloatingActionButton
                        }
                        Timber.i("ReviewScreen: send to AnkiDroid (deck=\"%s\")", state.deckName)
                        sendToAnkiDroid()
                    },
                    icon = { Icon(Icons.Filled.Send, contentDescription = null) },
                    text = {
                        Text(
                            stringResource(
                                if (state.isProcessing) R.string.processing else R.string.action_send_to_ankidroid,
                            ),
                        )
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
                DeckNameRow(
                    deckName = state.deckName,
                    onBrowseDecks = browseDecks,
                )
                NoteTypeRow(
                    noteTypeName = state.noteTypeName,
                    onBrowseNoteTypes = browseNoteTypes,
                )

                if (state.pages.isNotEmpty()) {
                    PageStrip(
                        pages = state.pages,
                        enabled = !state.isProcessing,
                        cloudOcrAvailable = state.cloudOcrAvailable,
                        onView = onViewZones,
                        onRerunOcr = onRerunOcr,
                        onDelete = { viewModel.deletePage(it) },
                    )
                }

                PairListHeader()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        // Clear the FAB so the last row is never trapped under it.
                        bottom = 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.pairs, key = { it.id }) { pair ->
                        PairRow(
                            pair = pair,
                            onFrontChange = { viewModel.updateFront(pair.id, it) },
                            onBackChange = { viewModel.updateBack(pair.id, it) },
                            onDelete = {
                                Timber.d("ReviewScreen: delete pair id=%d", pair.id)
                                viewModel.deletePair(pair.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = rowDeletedMsg,
                                        actionLabel = undoLabel,
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restorePair(pair)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        if (showCleanupSheet) {
            CleanupOverlay(
                initialConfig = state.cleanupConfig,
                onDismiss = { showCleanupSheet = false },
                onConfigChange = viewModel::setCleanupConfig,
                onPreview = { config -> viewModel.previewCleanup(config) },
                onApply = { config ->
                    viewModel.applyCleanup(config)
                    showCleanupSheet = false
                },
            )
        }

        if (state.showDeckPicker) {
            DeckPickerDialog(
                deckNames = state.deckNames,
                isLoading = state.isLoadingDeckNames,
                onSelect = viewModel::setDeckName,
                onDismiss = viewModel::dismissDeckPicker,
            )
        }

        if (state.showNoteTypePicker) {
            NoteTypePickerDialog(
                noteTypeNames = state.noteTypeNames,
                isLoading = state.isLoadingNoteTypeNames,
                onSelect = viewModel::setNoteType,
                onDismiss = viewModel::dismissNoteTypePicker,
            )
        }
    }

}

/** Deck name is picked from AnkiDroid's existing decks only; the app never creates one. */
@Composable
private fun DeckNameRow(
    deckName: String,
    onBrowseDecks: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBrowseDecks)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.deck_label_with_name,
                deckName.ifBlank { stringResource(R.string.deck_name_not_set) },
            ),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onBrowseDecks) {
            Icon(Icons.Filled.FormatListBulleted, contentDescription = stringResource(R.string.cd_browse_decks))
        }
    }
}

@Composable
private fun NoteTypeRow(
    noteTypeName: String,
    onBrowseNoteTypes: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBrowseNoteTypes)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.note_type_label_with_name,
                noteTypeName.ifBlank { stringResource(R.string.default_note_type_name) },
            ),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onBrowseNoteTypes) {
            Icon(Icons.Filled.Style, contentDescription = stringResource(R.string.cd_browse_note_types))
        }
    }
}

/**
 * Page management as a thumbnail strip: the per-page actions live in a menu behind each
 * thumbnail rather than as four permanently-rendered controls per page.
 */
@Composable
private fun PageStrip(
    pages: List<Page>,
    enabled: Boolean,
    cloudOcrAvailable: Boolean,
    onView: (Long) -> Unit,
    onRerunOcr: (pageId: Long, useCloud: Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(pages, key = { it.id }) { page ->
            var expanded by remember(page.id) { mutableStateOf(false) }
            Box {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = File(page.imagePath),
                        contentDescription = stringResource(R.string.page_number, page.order + 1),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clickable(enabled = enabled) { expanded = true },
                    )
                    Text(
                        stringResource(R.string.page_number, page.order + 1),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_view_detected_lines)) },
                        leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                        onClick = {
                            Timber.d("ReviewScreen: view overlay for page=%d", page.id)
                            expanded = false
                            onView(page.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_rerun_ocr_on_device)) },
                        onClick = {
                            Timber.d("ReviewScreen: re-run on-device OCR for page=%d", page.id)
                            expanded = false
                            onRerunOcr(page.id, false)
                        },
                    )
                    if (cloudOcrAvailable) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_rerun_ocr_cloud)) },
                            onClick = {
                                Timber.d("ReviewScreen: re-run cloud OCR for page=%d", page.id)
                                expanded = false
                                onRerunOcr(page.id, true)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_page, page.order + 1)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            Timber.d("ReviewScreen: delete page id=%d", page.id)
                            expanded = false
                            onDelete(page.id)
                        },
                    )
                }
            }
        }
    }
}

/** Column captions, shown once, so the rows themselves need no per-field labels. */
@Composable
private fun PairListHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
    ) {
        Text(
            stringResource(R.string.label_front),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.label_back),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairRow(
    pair: WordPair,
    onFrontChange: (String) -> Unit,
    onBackChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
            // Must return true for every value, including Settled: returning false vetoes the
            // transition, so the box can never settle back to Settled (e.g. after a partial swipe,
            // or dismissState.reset()) and its drag gesture stays permanently disabled.
            true
        },
    )
    // LazyColumn reuses this row's composition (and its dismissState) when a pair
    // is deleted and then restored via Undo with the same id, so the box would
    // otherwise reappear stuck in its dismissed EndToStart position.
    LaunchedEffect(pair.id) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.testTag("pair_row_${pair.id}"),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cd_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = pair.front,
                onValueChange = onFrontChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pair_front_${pair.id}")
                    .onFocusChanged { state ->
                        if (!state.isFocused) {
                            val trimmed = pair.front.trim()
                            if (trimmed != pair.front) onFrontChange(trimmed)
                        }
                    },
            )
            OutlinedTextField(
                value = pair.back,
                onValueChange = onBackChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .testTag("pair_back_${pair.id}")
                    .onFocusChanged { state ->
                        if (!state.isFocused) {
                            val trimmed = pair.back.trim()
                            if (trimmed != pair.back) onBackChange(trimmed)
                        }
                    },
            )
        }
    }
}

@Composable
private fun CleanupOverlay(
    initialConfig: OcrCleanup.CleanupConfig,
    onDismiss: () -> Unit,
    onConfigChange: (OcrCleanup.CleanupConfig) -> Unit,
    onPreview: (OcrCleanup.CleanupConfig) -> List<Pair<WordPair, WordPair>>,
    onApply: (OcrCleanup.CleanupConfig) -> Unit,
) {
    var config by remember { mutableStateOf(initialConfig) }
    var showDiff by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    // Recomputed as the rules change, so the change count is always live -- no Preview round-trip
    // is needed before Apply becomes available. Keyed on the config so it does not re-run the
    // whole rule set on every recomposition.
    val changed = remember(config) { onPreview(config) }

    // Pushes every rule/scope/field change up to the ViewModel (and from there to AppSettings)
    // as it happens, so the dialog's settings are remembered next time it's opened. Guarded
    // against firing on the initial composition only (hasSeeded starts false): without this,
    // a dialog opened before ReviewViewModel.init()'s async load resolves would echo the
    // still-default initialConfig straight back over the user's real persisted config. Unlike
    // comparing against initialConfig directly, this still fires on every later change --
    // including a user toggling a rule on then back off, which lands back on a value equal to
    // initialConfig but must still be persisted.
    var hasSeeded by remember { mutableStateOf(false) }
    LaunchedEffect(config) {
        if (hasSeeded) onConfigChange(config) else hasSeeded = true
    }

    // The diff expands below the rules, which on a full dialog is off-screen; bring it into view
    // so "Preview" visibly does something.
    LaunchedEffect(showDiff) {
        if (showDiff) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.9f),
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxHeight()) {
                Text(stringResource(R.string.dialog_cleanup_title), style = MaterialTheme.typography.titleMedium)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RuleSection(
                        label = stringResource(R.string.rule_trim_junk),
                        checkboxTag = "cleanup_trimJunk_checkbox",
                        scopeTagPrefix = "cleanup_trimJunk_scope",
                        enabled = config.trimJunk.enabled,
                        scope = config.trimJunk.scope,
                        onEnabledChange = { config = config.copy(trimJunk = config.trimJunk.copy(enabled = it)) },
                        onScopeChange = { config = config.copy(trimJunk = config.trimJunk.copy(scope = it)) },
                    ) {
                        OutlinedTextField(
                            value = config.trimJunk.extraChars,
                            onValueChange = { config = config.copy(trimJunk = config.trimJunk.copy(extraChars = it)) },
                            label = { Text(stringResource(R.string.label_extra_chars_to_strip)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    RuleSection(
                        label = stringResource(R.string.rule_strip_page_number),
                        checkboxTag = "cleanup_pageNumber_checkbox",
                        scopeTagPrefix = "cleanup_pageNumber_scope",
                        enabled = config.pageNumber.enabled,
                        scope = config.pageNumber.scope,
                        onEnabledChange = { config = config.copy(pageNumber = config.pageNumber.copy(enabled = it)) },
                        onScopeChange = { config = config.copy(pageNumber = config.pageNumber.copy(scope = it)) },
                    )

                    RuleSection(
                        label = stringResource(R.string.rule_cut_after_separator),
                        checkboxTag = "cleanup_separator_checkbox",
                        scopeTagPrefix = "cleanup_separator_scope",
                        enabled = config.separatorCut.enabled,
                        scope = config.separatorCut.scope,
                        onEnabledChange = { config = config.copy(separatorCut = config.separatorCut.copy(enabled = it)) },
                        onScopeChange = { config = config.copy(separatorCut = config.separatorCut.copy(scope = it)) },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(",", "(", "/", ";").forEach { preset ->
                                OutlinedButton(
                                    onClick = {
                                        config = config.copy(separatorCut = config.separatorCut.copy(separator = preset))
                                    },
                                ) { Text(preset) }
                            }
                        }
                        OutlinedTextField(
                            value = config.separatorCut.separator,
                            onValueChange = { config = config.copy(separatorCut = config.separatorCut.copy(separator = it)) },
                            label = { Text(stringResource(R.string.label_separator)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    RuleSection(
                        label = stringResource(R.string.rule_fix_casing),
                        checkboxTag = "cleanup_casing_checkbox",
                        scopeTagPrefix = "cleanup_casing_scope",
                        enabled = config.casing.enabled,
                        scope = config.casing.scope,
                        onEnabledChange = { config = config.copy(casing = config.casing.copy(enabled = it)) },
                        onScopeChange = { config = config.copy(casing = config.casing.copy(scope = it)) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = config.casing.mode == OcrCleanup.CasingMode.SENTENCE,
                                onClick = { config = config.copy(casing = config.casing.copy(mode = OcrCleanup.CasingMode.SENTENCE)) },
                            )
                            Text(stringResource(R.string.casing_sentence_case))
                            RadioButton(
                                selected = config.casing.mode == OcrCleanup.CasingMode.LOWERCASE,
                                onClick = { config = config.copy(casing = config.casing.copy(mode = OcrCleanup.CasingMode.LOWERCASE)) },
                            )
                            Text(stringResource(R.string.casing_lowercase))
                        }
                    }

                    if (showDiff) {
                        Text(
                            stringResource(R.string.review_changes_count, changed.size),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (changed.isEmpty()) {
                            Text(stringResource(R.string.no_changes_to_apply))
                        } else {
                            changed.forEach { (old, new) ->
                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                    if (old.front != new.front) {
                                        Text(stringResource(R.string.diff_front_template, old.front, new.front))
                                    }
                                    if (old.back != new.back) {
                                        Text(stringResource(R.string.diff_back_template, old.back, new.back))
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(
                        onClick = { showDiff = !showDiff },
                        enabled = changed.isNotEmpty(),
                    ) {
                        Text(stringResource(if (showDiff) R.string.action_hide_changes else R.string.action_preview))
                    }
                    Button(
                        onClick = { onApply(config) },
                        enabled = changed.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.action_apply_count, changed.size))
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleSection(
    label: String,
    checkboxTag: String,
    scopeTagPrefix: String,
    enabled: Boolean,
    scope: OcrCleanup.ColumnScope,
    onEnabledChange: (Boolean) -> Unit,
    onScopeChange: (OcrCleanup.ColumnScope) -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.testTag(checkboxTag),
            )
            Text(label)
        }
        // Scope chips and extra options only mean something once the rule is on; showing them
        // for disabled rules made most of the dialog inert decoration.
        if (enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    OcrCleanup.ColumnScope.FRONT to R.string.label_front,
                    OcrCleanup.ColumnScope.BACK to R.string.label_back,
                    OcrCleanup.ColumnScope.BOTH to R.string.label_both,
                ).forEach { (value, textRes) ->
                    FilterChip(
                        selected = scope == value,
                        onClick = { onScopeChange(value) },
                        label = { Text(stringResource(textRes)) },
                        modifier = Modifier.testTag("${scopeTagPrefix}_${value.name}"),
                    )
                }
            }
            extraContent?.invoke()
        }
    }
}
