package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Holds whole project trees; a test edits [trees] to simulate writes landing in Room. */
class FakeLocalProjectDataSource(initial: List<Project> = emptyList()) : LocalProjectDataSource {

    val trees = MutableStateFlow(initial)

    /** Answers [getAllProjectTrees] in order before it starts succeeding. */
    val treeReadErrors = ArrayDeque<DataError.Local>()

    override fun getProjects(): Flow<List<Project>> = trees

    override fun observeProjectCount(): Flow<Int> = trees.map { it.size }

    override fun getActiveProjects(): Flow<List<Project>> = trees

    override suspend fun getProjectById(projectId: String): Result<Project?, DataError.Local> =
        Result.Success(trees.value.firstOrNull { it.projectId == projectId }?.copy(projectTasks = null))

    override fun observeProjectById(projectId: String): Flow<Project?> =
        trees.map { list -> list.firstOrNull { it.projectId == projectId } }

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Result<Project?, DataError.Local> =
        Result.Success(trees.value.firstOrNull { it.projectId == projectId })

    override fun observeProjectWithTaskTreeById(projectId: String): Flow<Project?> = observeProjectById(projectId)

    override suspend fun getAllProjectTrees(): Result<List<Project>, DataError.Local> =
        treeReadErrors.removeFirstOrNull()?.let { Result.Error(it) } ?: Result.Success(trees.value)

    override suspend fun upsertProject(project: Project): EmptyResult<DataError.Local> {
        trees.value = trees.value.filterNot { it.projectId == project.projectId } + project
        return Result.Success(Unit)
    }

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError.Local> {
        trees.value = trees.value.filterNot { it.projectId == projectId }
        return Result.Success(Unit)
    }

    override suspend fun deleteAllProjects(): EmptyResult<DataError.Local> {
        trees.value = emptyList()
        return Result.Success(Unit)
    }
}
