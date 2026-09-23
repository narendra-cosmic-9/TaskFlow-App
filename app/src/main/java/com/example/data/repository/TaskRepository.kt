package com.example.data.repository

import com.example.data.local.TaskDao
import com.example.data.model.Task
import com.example.data.model.User
import com.example.data.model.UserStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskRepository(private val taskDao: TaskDao) {

    // --- User Repo Methods ---
    suspend fun getUserByEmail(email: String): User? = taskDao.getUserByEmail(email)

    fun getLoggedInUserFlow(): Flow<User?> = taskDao.getLoggedInUserFlow()

    suspend fun getLoggedInUser(): User? = taskDao.getLoggedInUser()

    suspend fun registerUser(user: User): Long {
        taskDao.logoutAllUsers() // Ensure single logged in user
        return taskDao.insertUser(user.copy(isLoggedIn = true))
    }

    suspend fun loginUser(user: User) {
        taskDao.logoutAllUsers()
        taskDao.updateUser(user.copy(isLoggedIn = true))
    }

    suspend fun logout() {
        taskDao.logoutAllUsers()
    }

    // --- Task Repo Methods ---
    val allTasks: Flow<List<Task>> = taskDao.getAllTasksFlow()

    fun getTasksByCategory(category: String): Flow<List<Task>> = taskDao.getTasksByCategoryFlow(category)

    suspend fun getTaskById(id: Int): Task? = taskDao.getTaskById(id)

    suspend fun saveTask(task: Task) {
        taskDao.insertTask(task)
        updateStatsAndScore()
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
        updateStatsAndScore()
    }

    suspend fun deleteTaskById(id: Int) {
        taskDao.deleteTaskById(id)
        updateStatsAndScore()
    }

    suspend fun toggleTaskCompletion(taskId: Int) {
        val task = taskDao.getTaskById(taskId) ?: return
        val today = getTodayDateString()
        val nextState = !task.isCompleted
        val updatedTask = task.copy(
            isCompleted = nextState,
            completionDate = if (nextState) today else null
        )
        taskDao.insertTask(updatedTask)
        updateStatsAndScore(completedTaskDate = if (nextState) today else null)
    }

    // --- User Stats ---
    val userStats: Flow<UserStats?> = taskDao.getUserStatsFlow()

    suspend fun getOrCreateStats(): UserStats {
        var stats = taskDao.getUserStats()
        if (stats == null) {
            stats = UserStats()
            taskDao.insertUserStats(stats)
        }
        return stats
    }

    // --- Streak & Score Computation logic ---
    private suspend fun updateStatsAndScore(completedTaskDate: String? = null) {
        val tasks = taskDao.getAllTasksFlow().firstOrNull() ?: emptyList()
        val totalCount = tasks.size
        val completedCount = tasks.count { it.isCompleted }

        val currentStats = getOrCreateStats()
        var newStreak = currentStats.streak
        var lastCompleted = currentStats.lastCompletedDate

        if (completedTaskDate != null) {
            val today = getTodayDateString()
            val yesterday = getYesterdayDateString()

            if (lastCompleted == null) {
                // First completed task ever
                newStreak = 1
                lastCompleted = today
            } else if (lastCompleted == yesterday) {
                // Completed yesterday, streak continues!
                newStreak += 1
                lastCompleted = today
            } else if (lastCompleted != today) {
                // Completed older than yesterday, start new streak
                newStreak = 1
                lastCompleted = today
            }
            // If already completed today, streak remains unchanged.
        }

        // Calculate productivity score
        val completionRatio = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
        var productivityScore = (completionRatio * 100).toInt()
        
        // Add a bonus of 2 points per streak day, up to 10 points bonus
        val bonus = (newStreak * 2).coerceAtMost(10)
        productivityScore = (productivityScore + bonus).coerceAtMost(100)

        val updatedStats = currentStats.copy(
            streak = newStreak,
            lastCompletedDate = lastCompleted,
            totalCompleted = completedCount,
            productivityScore = productivityScore
        )
        taskDao.insertUserStats(updatedStats)
    }

    fun checkAndResetStreakIfNeeded(stats: UserStats) {
        val lastCompleted = stats.lastCompletedDate ?: return
        val today = getTodayDateString()
        val yesterday = getYesterdayDateString()
        
        // If the streak wasn't updated today or yesterday, it died. Reset it.
        if (lastCompleted != today && lastCompleted != yesterday) {
            val resetStats = stats.copy(streak = 0)
            // Save on a background/coroutine context
        }
    }

    // Helpers
    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun getYesterdayDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(cal.time)
    }
}
