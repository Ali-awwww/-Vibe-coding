package com.example.data.local

import androidx.room.*
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VibeStudioDao {
    // Workspaces
    @Query("SELECT * FROM workspaces ORDER BY lastAccessedAt DESC")
    fun getAllWorkspaces(): Flow<List<Workspace>>

    @Query("SELECT * FROM workspaces WHERE ownerId = :userId ORDER BY lastAccessedAt DESC")
    fun getWorkspacesForUser(userId: String): Flow<List<Workspace>>

    @Query("SELECT * FROM workspaces WHERE id = :id")
    suspend fun getWorkspaceById(id: String): Workspace?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspace(workspace: Workspace)

    @Query("UPDATE workspaces SET lastAccessedAt = :timestamp WHERE id = :id")
    suspend fun updateLastAccessed(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteWorkspace(id: String)

    // Files
    @Query("SELECT * FROM workspace_files WHERE workspaceId = :workspaceId ORDER BY isFolder DESC, name ASC")
    fun getFilesByWorkspace(workspaceId: String): Flow<List<WorkspaceFile>>

    @Query("SELECT * FROM workspace_files WHERE id = :id")
    suspend fun getFileById(id: String): WorkspaceFile?

    @Query("SELECT * FROM workspace_files WHERE workspaceId = :workspaceId AND path = :path")
    suspend fun getFileByPath(workspaceId: String, path: String): WorkspaceFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: WorkspaceFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<WorkspaceFile>)

    @Query("DELETE FROM workspace_files WHERE id = :id")
    suspend fun deleteFile(id: String)

    @Query("DELETE FROM workspace_files WHERE workspaceId = :workspaceId")
    suspend fun deleteFilesByWorkspace(workspaceId: String)

    // Project Context
    @Query("SELECT * FROM workspace_context WHERE workspaceId = :workspaceId")
    fun getProjectContext(workspaceId: String): Flow<ProjectContext?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectContext(context: ProjectContext)

    // Build Logs
    @Query("SELECT * FROM workspace_build_logs WHERE workspaceId = :workspaceId ORDER BY timestamp ASC")
    fun getBuildLogs(workspaceId: String): Flow<List<BuildLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildLog(log: BuildLog)

    @Query("DELETE FROM workspace_build_logs WHERE workspaceId = :workspaceId")
    suspend fun clearBuildLogs(workspaceId: String)

    // Search and Sync Files
    @Query("SELECT * FROM workspace_files WHERE workspaceId = :workspaceId AND (name LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR path LIKE '%' || :query || '%') ORDER BY isFolder DESC, name ASC")
    suspend fun searchFiles(workspaceId: String, query: String): List<WorkspaceFile>

    @Query("SELECT * FROM workspace_files WHERE workspaceId = :workspaceId")
    suspend fun getAllFilesSync(workspaceId: String): List<WorkspaceFile>

    // Checkpoints
    @Query("SELECT * FROM workspace_checkpoints WHERE workspaceId = :workspaceId ORDER BY timestamp DESC")
    fun getCheckpoints(workspaceId: String): Flow<List<WorkspaceCheckpoint>>

    @Query("SELECT * FROM workspace_checkpoints WHERE id = :id")
    suspend fun getCheckpointById(id: String): WorkspaceCheckpoint?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckpoint(checkpoint: WorkspaceCheckpoint)

    @Query("DELETE FROM workspace_checkpoints WHERE id = :id")
    suspend fun deleteCheckpoint(id: String)

    @Query("DELETE FROM workspace_checkpoints WHERE workspaceId = :workspaceId")
    suspend fun deleteCheckpointsByWorkspace(workspaceId: String)

    // Git Commits
    @Query("SELECT * FROM workspace_git_commits WHERE workspaceId = :workspaceId ORDER BY timestamp DESC")
    fun getCommits(workspaceId: String): Flow<List<WorkspaceGitCommit>>

    @Query("SELECT * FROM workspace_git_commits WHERE workspaceId = :workspaceId AND branch = :branch ORDER BY timestamp DESC")
    fun getCommitsByBranch(workspaceId: String, branch: String): Flow<List<WorkspaceGitCommit>>

    @Query("SELECT * FROM workspace_git_commits WHERE id = :id")
    suspend fun getCommitById(id: String): WorkspaceGitCommit?

    @Query("SELECT * FROM workspace_git_commits WHERE workspaceId = :workspaceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestCommit(workspaceId: String): WorkspaceGitCommit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommit(commit: WorkspaceGitCommit)

    @Query("UPDATE workspace_git_commits SET isPushedToRemote = 1 WHERE id IN (:commitIds)")
    suspend fun markCommitsPushed(commitIds: List<String>)

    // Git Branches
    @Query("SELECT * FROM workspace_git_branches WHERE workspaceId = :workspaceId ORDER BY isDefault DESC, name ASC")
    fun getBranches(workspaceId: String): Flow<List<WorkspaceGitBranch>>

    @Query("SELECT * FROM workspace_git_branches WHERE workspaceId = :workspaceId AND name = :name")
    suspend fun getBranchByName(workspaceId: String, name: String): WorkspaceGitBranch?

    @Query("SELECT * FROM workspace_git_branches WHERE workspaceId = :workspaceId AND isCurrent = 1 LIMIT 1")
    suspend fun getCurrentBranch(workspaceId: String): WorkspaceGitBranch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranch(branch: WorkspaceGitBranch)

    @Query("UPDATE workspace_git_branches SET isCurrent = CASE WHEN name = :branchName THEN 1 ELSE 0 END WHERE workspaceId = :workspaceId")
    suspend fun setCurrentBranch(workspaceId: String, branchName: String)

    @Query("UPDATE workspace_git_branches SET headCommitSha = :headSha WHERE workspaceId = :workspaceId AND name = :branchName")
    suspend fun updateBranchHead(workspaceId: String, branchName: String, headSha: String)

    @Query("DELETE FROM workspace_git_branches WHERE workspaceId = :workspaceId AND name = :branchName AND isDefault = 0")
    suspend fun deleteBranch(workspaceId: String, branchName: String)

    // Task Change Reviews
    @Query("SELECT * FROM workspace_task_reviews WHERE workspaceId = :workspaceId ORDER BY timestamp DESC")
    fun getTaskReviews(workspaceId: String): Flow<List<WorkspaceTaskReview>>

    @Query("SELECT * FROM workspace_task_reviews WHERE id = :id")
    suspend fun getTaskReviewById(id: String): WorkspaceTaskReview?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskReview(review: WorkspaceTaskReview)

    @Query("UPDATE workspace_task_reviews SET status = :status WHERE id = :id")
    suspend fun updateTaskReviewStatus(id: String, status: String)

    @Query("DELETE FROM workspace_git_commits WHERE workspaceId = :workspaceId")
    suspend fun deleteGitCommitsByWorkspace(workspaceId: String)

    @Query("DELETE FROM workspace_git_branches WHERE workspaceId = :workspaceId")
    suspend fun deleteGitBranchesByWorkspace(workspaceId: String)

    @Query("DELETE FROM workspace_task_reviews WHERE workspaceId = :workspaceId")
    suspend fun deleteTaskReviewsByWorkspace(workspaceId: String)

    // Agent Activity Logs
    @Query("SELECT * FROM agent_activity_logs WHERE workspaceId = :workspaceId ORDER BY timestamp DESC")
    fun getActivityLogs(workspaceId: String): Flow<List<AgentActivityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: AgentActivityLog)

    @Query("DELETE FROM agent_activity_logs WHERE workspaceId = :workspaceId")
    suspend fun clearActivityLogs(workspaceId: String)

    // Pending Approvals
    @Query("SELECT * FROM pending_approvals WHERE workspaceId = :workspaceId AND status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingApprovals(workspaceId: String): Flow<List<PendingApproval>>

    @Query("SELECT * FROM pending_approvals WHERE id = :id")
    fun getPendingApprovalById(id: String): Flow<PendingApproval?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingApproval(approval: PendingApproval)

    @Query("UPDATE pending_approvals SET status = :status WHERE id = :id")
    suspend fun updateApprovalStatus(id: String, status: String)

    @Query("DELETE FROM pending_approvals WHERE id = :id")
    suspend fun deletePendingApproval(id: String)
}
