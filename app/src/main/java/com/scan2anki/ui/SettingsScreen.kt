package com.scan2anki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scan2anki.R
import com.scan2anki.settings.AgeCheck
import com.scan2anki.vm.SettingsViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

private val OCR_SCRIPTS = listOf("latin" to R.string.ocr_script_latin, "chinese" to R.string.ocr_script_chinese)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var apiKeyVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ankiPermissionDeniedMsg = stringResource(R.string.error_anki_permission_denied)
    LaunchedEffect(Unit) { viewModel.init() }

    val testAnkiDroid = rememberAnkiPermissionAction(
        onGranted = viewModel::testAnkiDroid,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader(stringResource(R.string.section_text_recognition))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                OCR_SCRIPTS.forEachIndexed { index, (value, labelRes) ->
                    SegmentedButton(
                        selected = state.ocrScript == value,
                        onClick = {
                            Timber.d("SettingsScreen: OCR script set to %s", value)
                            viewModel.setOcrScript(value)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, OCR_SCRIPTS.size),
                    ) {
                        Text(stringResource(labelRes))
                    }
                }
            }

            when (state.ageCheck) {
                AgeCheck.ADULT_OR_TEEN -> {
                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = viewModel::setApiKey,
                        label = { Text(stringResource(R.string.label_vision_api_key)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (apiKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    imageVector = if (apiKeyVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = stringResource(
                                        if (apiKeyVisible) R.string.cd_hide_api_key else R.string.cd_show_api_key,
                                    ),
                                )
                            }
                        },
                        supportingText = {
                            Text(stringResource(R.string.supporting_ocr_explainer))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )

                    OutlinedButton(
                        onClick = viewModel::testApiKey,
                        enabled = !state.isTestingApiKey,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        if (state.isTestingApiKey) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_checking))
                        } else {
                            Text(stringResource(R.string.action_check_api_key))
                        }
                    }

                    state.apiKeyTestResult?.let { result ->
                        val tint = if (result.ok) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (result.ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                contentDescription = stringResource(
                                    if (result.ok) R.string.cd_api_key_valid else R.string.cd_api_key_invalid,
                                ),
                                tint = tint,
                                modifier = Modifier.size(20.dp),
                            )
                            SelectionContainer {
                                Text(
                                    text = result.message,
                                    color = tint,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                AgeCheck.UNKNOWN -> {
                    OutlinedButton(
                        onClick = viewModel::openAgeDialog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_use_cloud_vision))
                    }
                    Text(
                        text = stringResource(R.string.supporting_ocr_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                AgeCheck.UNDER_13 -> {
                    Text(
                        text = stringResource(R.string.msg_cloud_vision_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                null -> Unit
            }

            if (state.showAgeDialog) {
                AgeCheckDialog(
                    onSubmit = viewModel::submitBirthYear,
                    onDismiss = viewModel::dismissAgeDialog,
                )
            }

            SectionHeader(stringResource(R.string.section_ankidroid))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.defaultDeckName,
                    onValueChange = viewModel::setDefaultDeckName,
                    label = { Text(stringResource(R.string.label_default_deck_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = browseDecks) {
                    Icon(Icons.Filled.FormatListBulleted, contentDescription = stringResource(R.string.cd_browse_decks))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.defaultNoteType,
                    onValueChange = viewModel::setDefaultNoteType,
                    label = { Text(stringResource(R.string.label_default_note_type)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = browseNoteTypes) {
                    Icon(Icons.Filled.Style, contentDescription = stringResource(R.string.cd_browse_note_types))
                }
            }

            if (state.showDeckPicker) {
                DeckPickerDialog(
                    deckNames = state.deckNames,
                    isLoading = state.isLoadingDeckNames,
                    onSelect = viewModel::setDefaultDeckName,
                    onDismiss = viewModel::dismissDeckPicker,
                )
            }

            if (state.showNoteTypePicker) {
                NoteTypePickerDialog(
                    noteTypeNames = state.noteTypeNames,
                    isLoading = state.isLoadingNoteTypeNames,
                    onSelect = viewModel::setDefaultNoteType,
                    onDismiss = viewModel::dismissNoteTypePicker,
                )
            }

            OutlinedButton(
                onClick = testAnkiDroid,
                enabled = !state.isTestingAnkiDroid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                if (state.isTestingAnkiDroid) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_checking))
                } else {
                    Text(stringResource(R.string.action_check_connection))
                }
            }

            state.ankiDroidTestResult?.let { result ->
                val tint = if (result.ok) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (result.ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        contentDescription = stringResource(
                            if (result.ok) R.string.cd_connected else R.string.cd_not_connected,
                        ),
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = result.message,
                        color = tint,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}
