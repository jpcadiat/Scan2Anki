package com.scan2anki.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.scan2anki.R

@Composable
fun NoteTypePickerDialog(
    noteTypeNames: List<String>,
    isLoading: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AnkiListPickerDialog(
        title = stringResource(R.string.title_select_note_type),
        emptyMessage = stringResource(R.string.note_type_picker_empty),
        names = noteTypeNames,
        isLoading = isLoading,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}
