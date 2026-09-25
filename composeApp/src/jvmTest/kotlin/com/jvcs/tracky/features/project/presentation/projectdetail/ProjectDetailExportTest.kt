@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.features.project.presentation.projectdetail

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.endsWith
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import assertk.assertions.startsWith
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.testTimeManager
import com.jvcs.tracky.features.project.domain.export.ExportError
import com.jvcs.tracky.features.project.domain.export.FakeExportFileSharer
import com.jvcs.tracky.features.project.domain.export.FakeProjectJsonExporter
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.presentation.util.toUiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Instant

private const val PROJECT_TITLE = "Client work: Q3"

/** The export menu: JSON is shared straight away, a PDF round-trips through the Root to be drawn. */
class ProjectDetailExportTest {

    private val dispatcher = StandardTestDispatcher()
    private val exporter = FakeProjectJsonExporter()
    private val sharer = FakeExportFileSharer()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val project =
        Project(
            projectId = "p1",
            title = PROJECT_TITLE,
            description = null,
            colorArgb = null,
            totalDurationMillis = null,
            startDateTimeUtc = Instant.parse("2026-09-01T09:00:00Z"),
            isFinished = false,
            endDateTimeUtc = null,
            projectTasks = emptyList(),
        )

    private fun TestScope.viewModel(tree: Project? = project): ProjectDetailViewModel {
        val vm =
            ProjectDetailViewModel(
                projectId = "p1",
                projectRepository = TreeRepository(project, tree),
                projectTaskRepository = FakeProjectTaskRepository(),
                subTaskRepository = FakeSubTaskRepository(),
                timeManager = testTimeManager(),
                timeProvider = FakeTimeProvider(now = Instant.parse("2026-09-22T12:00:00Z")),
                projectJsonExporter = exporter,
                exportFileSharer = sharer,
                ioDispatcher = dispatcher,
            )
        // The state is WhileSubscribed, so it only folds updates in while something collects it.
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    private fun ProjectDetailViewModel.isExporting() = state.value.isExporting

    @Test
    fun exportMenuClickTogglesAndDismissCollapses() =
        runTest {
            val vm = viewModel()

            vm.onAction(ProjectDetailAction.OnExportMenuClick)
            runCurrent()
            assertThat(vm.state.value.isExportMenuExpanded).isTrue()
            vm.onAction(ProjectDetailAction.OnExportMenuClick)
            runCurrent()
            assertThat(vm.state.value.isExportMenuExpanded).isFalse()

            vm.onAction(ProjectDetailAction.OnExportMenuClick)
            vm.onAction(ProjectDetailAction.OnExportMenuDismiss)
            runCurrent()
            assertThat(vm.state.value.isExportMenuExpanded).isFalse()
        }

    @Test
    fun jsonExportSharesTheExportedFileAndCollapsesTheMenu() =
        runTest {
            val vm = viewModel()
            vm.onAction(ProjectDetailAction.OnExportMenuClick)

            vm.onAction(ProjectDetailAction.OnExportFormatClick(ExportFormat.Json))
            runCurrent()

            assertThat(vm.state.value.isExportMenuExpanded).isFalse()
            assertThat(exporter.exportedProjects).containsExactly(project)
            assertThat(sharer.sharedFiles.map { it.fileName }).containsExactly("$PROJECT_TITLE.json")
            assertThat(vm.isExporting()).isFalse()
        }

    @Test
    fun jsonExporterFailureReportsAnErrorAndSharesNothing() =
        runTest {
            exporter.result = Result.Error(ExportError.SERIALIZATION)
            val vm = viewModel()

            vm.events.test {
                vm.onAction(ProjectDetailAction.OnExportFormatClick(ExportFormat.Json))
                runCurrent()

                assertThat(awaitItem()).isEqualTo(ProjectDetailEvent.Error(ExportError.SERIALIZATION.toUiText()))
            }
            assertThat(sharer.sharedFiles).isEmpty()
            assertThat(vm.isExporting()).isFalse()
        }

    @Test
    fun shareFailureReportsAnError() =
        runTest {
            sharer.result = Result.Error(ExportError.SHARE_FAILED)
            val vm = viewModel()

            vm.events.test {
                vm.onAction(ProjectDetailAction.OnExportFormatClick(ExportFormat.Json))
                runCurrent()

                assertThat(awaitItem()).isEqualTo(ProjectDetailEvent.Error(ExportError.SHARE_FAILED.toUiText()))
            }
            assertThat(vm.isExporting()).isFalse()
        }

    @Test
    fun missingProjectReportsNotFound() =
        runTest {
            val vm = viewModel(tree = null)

            vm.events.test {
                vm.onAction(ProjectDetailAction.OnExportFormatClick(ExportFormat.Pdf))
                runCurrent()

                assertThat(awaitItem()).isEqualTo(ProjectDetailEvent.Error(ExportError.PROJECT_NOT_FOUND.toUiText()))
            }
            assertThat(exporter.exportedProjects).isEmpty()
            assertThat(vm.isExporting()).isFalse()
        }

    @Test
    fun pdfExportAsksTheRootToRenderThenSharesTheRenderedBytes() =
        runTest {
            val vm = viewModel()

            vm.events.test {
                vm.onAction(ProjectDetailAction.OnExportFormatClick(ExportFormat.Pdf))
                runCurrent()

                assertThat(awaitItem())
                    .isInstanceOf<ProjectDetailEvent.RenderPdf>()
                    .prop(ProjectDetailEvent.RenderPdf::report)
                    .prop("title") { it.title }
                    .isEqualTo(PROJECT_TITLE)
            }
            // Still exporting while the Root draws the page.
            assertThat(vm.isExporting()).isTrue()
            assertThat(sharer.sharedFiles).isEmpty()

            vm.onAction(ProjectDetailAction.OnPdfRendered(byteArrayOf(1, 2, 3)))
            runCurrent()

            val shared = sharer.sharedFiles.single()
            assertThat(shared.fileName).startsWith("Client_work_Q3_")
            assertThat(shared.fileName).endsWith(".pdf")
            assertThat(shared.mimeType).isEqualTo("application/pdf")
            assertThat(shared.bytes.toList()).isEqualTo(listOf<Byte>(1, 2, 3))
            assertThat(vm.isExporting()).isFalse()
        }

    @Test
    fun pdfRenderFailureReportsAnErrorAndSharesNothing() =
        runTest {
            val vm = viewModel()

            vm.events.test {
                vm.onAction(ProjectDetailAction.OnExportFormatClick(ExportFormat.Pdf))
                runCurrent()
                awaitItem()

                vm.onAction(ProjectDetailAction.OnPdfRenderFailed)
                runCurrent()

                assertThat(awaitItem()).isEqualTo(ProjectDetailEvent.Error(ExportError.RENDER_FAILED.toUiText()))
            }
            assertThat(sharer.sharedFiles).isEmpty()
            assertThat(vm.isExporting()).isFalse()
        }

    @Test
    fun cancelledRenderResetsSilentlyAndAllowsANewExport() =
        runTest {
            val vm = viewModel()

            vm.events.test {
                vm.onAction(ProjectDetailAction.OnExportFormatClick(ExportFormat.Pdf))
                runCurrent()
                awaitItem()

                vm.onAction(ProjectDetailAction.OnPdfRenderCancelled)
                runCurrent()
                assertThat(vm.isExporting()).isFalse()

                vm.onAction(ProjectDetailAction.OnExportFormatClick(ExportFormat.Pdf))
                runCurrent()
                assertThat(awaitItem()).isInstanceOf<ProjectDetailEvent.RenderPdf>()
                // The earlier name was dropped, but the new request's is pending, so the bytes are shared.
                vm.onAction(ProjectDetailAction.OnPdfRendered(byteArrayOf(1)))
                runCurrent()
            }
            assertThat(sharer.sharedFiles).hasSize(1)
            assertThat(vm.isExporting()).isFalse()
        }

    @Test
    fun renderedBytesWithoutARequestedPdfAreIgnored() =
        runTest {
            val vm = viewModel()

            vm.onAction(ProjectDetailAction.OnPdfRendered(byteArrayOf(1)))
            vm.onAction(ProjectDetailAction.OnPdfRenderFailed)
            runCurrent()

            assertThat(sharer.sharedFiles).isEmpty()
            assertThat(vm.isExporting()).isFalse()
        }

    @Test
    fun formatClickWhileExportingIsIgnored() =
        runTest {
            val vm = viewModel()

            vm.onAction(ProjectDetailAction.OnExportFormatClick(ExportFormat.Json))
            vm.onAction(ProjectDetailAction.OnExportFormatClick(ExportFormat.Json))
            runCurrent()
            assertThat(exporter.exportedProjects).containsExactly(project)

            // A PDF waiting on the Root still counts as exporting.
            vm.events.test {
                vm.onAction(ProjectDetailAction.OnExportFormatClick(ExportFormat.Pdf))
                runCurrent()
                awaitItem()
            }
            vm.onAction(ProjectDetailAction.OnExportMenuClick)
            vm.onAction(ProjectDetailAction.OnExportFormatClick(ExportFormat.Json))
            runCurrent()
            assertThat(exporter.exportedProjects).containsExactly(project)
            assertThat(vm.state.value.isExportMenuExpanded).isFalse()
            assertThat(vm.isExporting()).isTrue()
        }
}

/** The detail fake, except the task tree the export reads can be missing. */
private class TreeRepository(project: Project, tree: Project?) :
    ProjectRepository by FakeDetailProjectRepository(project) {
    private val tree = MutableStateFlow(tree)

    override fun observeProjectWithTaskTreeById(projectId: String): Flow<Project?> = tree
}
