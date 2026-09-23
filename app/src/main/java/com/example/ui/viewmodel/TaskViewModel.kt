package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Task
import com.example.data.model.User
import com.example.data.model.UserStats
import com.example.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {

    // --- Authentication State ---
    val loggedInUser: StateFlow<User?> = repository.getLoggedInUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // --- Search & Filters ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _selectedPriority = MutableStateFlow<String?>(null)
    val selectedPriority: StateFlow<String?> = _selectedPriority.asStateFlow()

    private val _selectedCalendarDate = MutableStateFlow(getTodayDateString())
    val selectedCalendarDate: StateFlow<String> = _selectedCalendarDate.asStateFlow()

    // --- Tasks State Flow (Reactive Filtering) ---
    val filteredTasks: StateFlow<List<Task>> = combine(
        repository.allTasks,
        _searchQuery,
        _selectedCategory,
        _selectedPriority,
        _selectedCalendarDate
    ) { tasks, query, category, priority, calDate ->
        tasks.filter { task ->
            val matchesQuery = query.isEmpty() || 
                    task.title.contains(query, ignoreCase = true) || 
                    task.description.contains(query, ignoreCase = true)
            val matchesCategory = category == null || task.category == category
            val matchesPriority = priority == null || task.priority == priority
            // Let calendar filter optionally or match date if calendar screen is active
            matchesQuery && matchesCategory && matchesPriority
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Stats Flow ---
    val userStats: StateFlow<UserStats?> = repository.userStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Theme State (Persisted in-memory for session) ---
    private val _isDarkTheme = MutableStateFlow(true) // Premium dark theme by default
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // --- Notifications Setting ---
    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    // --- Backup & Sync State Simulation ---
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<String>>(emptyList())
    val syncLogs: StateFlow<List<String>> = _syncLogs.asStateFlow()

    // --- Achievements list ---
    val achievements = listOf(
        Achievement("First Step", "Complete your first task", "ic_award", 1),
        Achievement("On Fire", "Maintain a 3-day daily completion streak", "ic_fire", 3),
        Achievement("Productivity Master", "Achieve a productivity score of 80% or more", "ic_star", 5),
        Achievement("Clean Slate", "Complete 5 tasks in total", "ic_verified", 5),
        Achievement("Elite Planner", "Create 10 tasks in any category", "ic_speed", 10)
    )

    init {
        viewModelScope.launch {
            // Trigger stats creation if not exists
            repository.getOrCreateStats()
        }
    }

    // --- Auth Actions ---
    fun register(name: String, email: String, passwordHash: String) {
        viewModelScope.launch {
            if (name.isBlank() || email.isBlank() || passwordHash.isBlank()) {
                _authError.value = "All fields are required"
                return@launch
            }
            val existing = repository.getUserByEmail(email)
            if (existing != null) {
                _authError.value = "Email is already registered"
                return@launch
            }
            val user = User(name = name, email = email, passwordHash = passwordHash)
            repository.registerUser(user)
            _authError.value = null
        }
    }

    fun login(email: String, passwordHash: String) {
        viewModelScope.launch {
            if (email.isBlank() || passwordHash.isBlank()) {
                _authError.value = "Please enter both email and password"
                return@launch
            }
            val user = repository.getUserByEmail(email)
            if (user == null || user.passwordHash != passwordHash) {
                _authError.value = "Invalid email or password"
                return@launch
            }
            repository.loginUser(user)
            _authError.value = null
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    // --- Task Actions ---
    fun addTask(title: String, description: String, category: String, priority: String, dueDate: String, dueTime: String) {
        viewModelScope.launch {
            val newTask = Task(
                title = title,
                description = description,
                category = category,
                priority = priority,
                dueDate = dueDate,
                dueTime = dueTime
            )
            repository.saveTask(newTask)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.saveTask(task)
        }
    }

    fun toggleTaskCompletion(taskId: Int) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(taskId)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun deleteTaskById(id: Int) {
        viewModelScope.launch {
            repository.deleteTaskById(id)
        }
    }

    // --- Filter Controls ---
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun selectPriority(priority: String?) {
        _selectedPriority.value = priority
    }

    fun selectCalendarDate(date: String) {
        _selectedCalendarDate.value = date
    }

    // --- Theme & Settings Controls ---
    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun toggleNotifications() {
        _notificationsEnabled.value = !_notificationsEnabled.value
    }

    // --- Backup & Sync Simulation ---
    fun runBackupAndSync() {
        viewModelScope.launch {
            if (_syncing.value) return@launch
            _syncing.value = true
            _syncLogs.value = listOf("Initializing secure handshake...", "Connecting to cloud sync service...")
            
            kotlinx.coroutines.delay(1000)
            _syncLogs.value = _syncLogs.value + "Checking local database (Room)..."
            
            val tasks = repository.allTasks.firstOrNull() ?: emptyList()
            kotlinx.coroutines.delay(800)
            _syncLogs.value = _syncLogs.value + "Found ${tasks.size} tasks to sync."
            _syncLogs.value = _syncLogs.value + "Uploading encrypted payload (AES-256)..."
            
            kotlinx.coroutines.delay(1200)
            _syncLogs.value = _syncLogs.value + "Updating completion records & streak data..."
            
            kotlinx.coroutines.delay(1000)
            _syncLogs.value = _syncLogs.value + "Sync complete! Cloud records are fully up to date."
            _syncing.value = false
        }
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
}

class TaskViewModelFactory(private val repository: TaskRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class Achievement(
    val title: String,
    val description: String,
    val iconKey: String,
    val targetValue: Int
)
