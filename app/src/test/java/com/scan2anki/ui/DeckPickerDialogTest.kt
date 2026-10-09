package com.scan2anki.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeckPickerDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsDeckNames_andSelectingOneInvokesOnSelectAndOnDismiss() {
        var selected: String? = null
        var dismissed = false
        composeRule.setContent {
            DeckPickerDialog(
                deckNames = listOf("Spanish", "French"),
                isLoading = false,
                onSelect = { selected = it },
                onDismiss = { dismissed = true },
            )
        }
        composeRule.onNodeWithText("Spanish").assertIsDisplayed()
        composeRule.onNodeWithText("French").assertIsDisplayed()

        composeRule.onNodeWithText("French").performClick()

        assertThat(selected).isEqualTo("French")
        assertThat(dismissed).isTrue()
    }

    @Test
    fun showsEmptyMessage_whenNotLoadingAndNoDecks() {
        composeRule.setContent {
            DeckPickerDialog(
                deckNames = emptyList(),
                isLoading = false,
                onSelect = {},
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("No decks found").assertIsDisplayed()
    }

    @Test
    fun hidesEmptyMessage_whileLoading() {
        composeRule.setContent {
            DeckPickerDialog(
                deckNames = emptyList(),
                isLoading = true,
                onSelect = {},
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("No decks found").assertDoesNotExist()
    }

    @Test
    fun clickingCancel_invokesOnDismissWithoutOnSelect() {
        var selected: String? = null
        var dismissed = false
        composeRule.setContent {
            DeckPickerDialog(
                deckNames = listOf("Spanish"),
                isLoading = false,
                onSelect = { selected = it },
                onDismiss = { dismissed = true },
            )
        }

        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(selected).isNull()
        assertThat(dismissed).isTrue()
    }

    @Test
    fun showsNoteTypeNames_andSelectingOneInvokesOnSelectAndOnDismiss() {
        var selected: String? = null
        var dismissed = false
        composeRule.setContent {
            NoteTypePickerDialog(
                noteTypeNames = listOf("General", "Basic (and reversed card)"),
                isLoading = false,
                onSelect = { selected = it },
                onDismiss = { dismissed = true },
            )
        }
        composeRule.onNodeWithText("General").assertIsDisplayed()
        composeRule.onNodeWithText("Basic (and reversed card)").assertIsDisplayed()

        composeRule.onNodeWithText("Basic (and reversed card)").performClick()

        assertThat(selected).isEqualTo("Basic (and reversed card)")
        assertThat(dismissed).isTrue()
    }

    @Test
    fun noteTypePicker_showsEmptyMessage_whenNotLoadingAndNoNoteTypes() {
        composeRule.setContent {
            NoteTypePickerDialog(
                noteTypeNames = emptyList(),
                isLoading = false,
                onSelect = {},
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("No note types found").assertIsDisplayed()
    }

    @Test
    fun noteTypePicker_hidesEmptyMessage_whileLoading() {
        composeRule.setContent {
            NoteTypePickerDialog(
                noteTypeNames = emptyList(),
                isLoading = true,
                onSelect = {},
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("No note types found").assertDoesNotExist()
    }

    @Test
    fun noteTypePicker_clickingCancel_invokesOnDismissWithoutOnSelect() {
        var selected: String? = null
        var dismissed = false
        composeRule.setContent {
            NoteTypePickerDialog(
                noteTypeNames = listOf("General"),
                isLoading = false,
                onSelect = { selected = it },
                onDismiss = { dismissed = true },
            )
        }

        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(selected).isNull()
        assertThat(dismissed).isTrue()
    }
}
