package com.jvcs.tracky.features.project.presentation.projectdetail.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.projectdetail.ExportFormat
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.cd_export_project
import tracky.composeapp.generated.resources.export_json
import tracky.composeapp.generated.resources.export_pdf
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
internal class ProjectDetailTopAppBarTest {

    private var exportClicks = 0
    private val formats = mutableListOf<ExportFormat>()

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.show(isExportMenuExpanded: Boolean = false) =
        setContent {
            TrackyTheme {
                ProjectDetailTopAppBar(
                    isEditMode = false,
                    headerColor = Color.White,
                    isExportMenuExpanded = isExportMenuExpanded,
                    onAction = {},
                    onExportClick = { exportClicks++ },
                    onExportMenuDismiss = {},
                    onExportFormatClick = { formats += it },
                )
            }
        }

    @Test
    fun exportIconOpensTheMenu() =
        runComposeUiTest {
            show()

            onNodeWithContentDescription(text(Res.string.cd_export_project)).performClick()

            assertThat(exportClicks).isEqualTo(1)
            onNodeWithText(text(Res.string.export_pdf)).assertDoesNotExist()
        }

    @Test
    fun pdfItemPicksPdf() =
        runComposeUiTest {
            show(isExportMenuExpanded = true)

            onNodeWithText(text(Res.string.export_json)).assertExists()
            onNodeWithText(text(Res.string.export_pdf)).performClick()

            assertThat(formats).containsExactly(ExportFormat.Pdf)
        }

    @Test
    fun jsonItemPicksJson() =
        runComposeUiTest {
            show(isExportMenuExpanded = true)

            onNodeWithText(text(Res.string.export_json)).performClick()

            assertThat(formats).containsExactly(ExportFormat.Json)
        }
}
