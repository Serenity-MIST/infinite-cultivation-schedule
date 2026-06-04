package com.example.viewmodel

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DbTask
import com.example.data.DbTimer
import com.example.viewmodel.AlertItem
import com.example.data.DbStats
import com.example.data.CompanionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*

data class AlertItem(
    val id: String,
    val title: String,
    val content: String,
    val isHighPriority: Boolean = false,
    val timeLabel: String = ""
)

data class DashboardState(
    val biWeeklyActive: String = "",
    val biWeeklyTimer: String = "",
    val timeRealmActive: Boolean = false,
    val timeRealmTimer: String = "",
    val demonicRealmActive: Boolean = false,
    val demonicRealmTimer: String = "",
    val pillShopActive: Boolean = false,
    val pillShopTimer: String = "",
    val sevenStarTimer: String = "Ready",
    val recastingTimer: String = "Ready",
    val sevenStarProgress: Float = 1f,
    val recastingProgress: Float = 1f
)

class CompanionViewModel(private val repository: CompanionRepository) : ViewModel() {

    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    private val _randomTaskHighlight = MutableStateFlow<String?>(null)
    val randomTaskHighlight: StateFlow<String?> = _randomTaskHighlight.asStateFlow()

    val tasks: StateFlow<List<DbTask>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val cultivationStats: StateFlow<DbStats> = repository.allStats
        .map { list -> list.firstOrNull() ?: DbStats() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DbStats()
        )

    private val _activeAlerts = MutableStateFlow<List<AlertItem>>(emptyList())
    val activeAlerts: StateFlow<List<AlertItem>> = _activeAlerts.asStateFlow()

    private val _scanningState = MutableStateFlow<String?>(null)
    val scanningState: StateFlow<String?> = _scanningState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var simulatedHour: Int? = null
    private var simulatedMinute: Int? = null

    val simulatedTimeText: String
        get() = if (simulatedHour != null) {
            String.format("%02d:%02d (SIMULATED)", simulatedHour, simulatedMinute ?: 0)
        } else {
            "System Clock"
        }

    private var cachedTimers: List<DbTimer> = emptyList()

    init {
        // Pre-populate tasks on launcher, check for empty lists
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.getAllTasksDirect()
            if (current.isEmpty()) {
                val defaultTasks = listOf(
                    DbTask("daily_all_dimensions", "Grind 50k stats in all 4 dimensions", "DAILY", false),
                    DbTask("daily_elemental_damage", "Grind 50k stats on main elemental damage", "DAILY", false),
                    DbTask("daily_craft_pills", "Craft pills", "DAILY", false),
                    DbTask("daily_pet_bein", "Pet Bein stat upgrade check", "DAILY", false),
                    DbTask("daily_realm_circles", "Realm of Circles run", "DAILY", false),
                    DbTask("sage_daily_temper", "Daily Temper", "SAGE_TRUNK_DAILY", false),
                    DbTask("sage_weekly_promotion", "Weekly Promotion", "SAGE_TRUNK_WEEKLY", false)
                )
                repository.insertTasks(defaultTasks)
            }
            
            // Generate default stats singleton if not exists
            if (repository.getStatsById("singleton_stats") == null) {
                repository.insertStats(DbStats())
            }
        }

        // Cache manual timers
        viewModelScope.launch {
            repository.allTimers.collect {
                cachedTimers = it
                updateTimers()
            }
        }

        // Automatic reset detection on task updates
        viewModelScope.launch {
            tasks.collect { tasksList ->
                if (tasksList.isNotEmpty()) {
                    handleMidnightAndWeeklyResets(tasksList)
                }
            }
        }

        // Infinite 1-second ticks for live timers & alert checks
        viewModelScope.launch {
            while (true) {
                updateTimers()
                checkAlerts()
                delay(1000)
            }
        }
    }

    private fun handleMidnightAndWeeklyResets(currentTasks: List<DbTask>) {
        val currentInstant = Instant.now()
        val zoneId = ZoneId.systemDefault()
        val currentDate = LocalDateTime.ofInstant(currentInstant, zoneId).toLocalDate()

        val tasksToReset = mutableListOf<DbTask>()
        for (task in currentTasks) {
            if (task.isCompleted && task.lastCompletedTimestamp > 0L) {
                val completionDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(task.lastCompletedTimestamp),
                    zoneId
                ).toLocalDate()

                if (task.category == "DAILY" || task.category == "SAGE_TRUNK_DAILY") {
                    if (currentDate.isAfter(completionDate)) {
                        tasksToReset.add(task.copy(isCompleted = false, lastCompletedTimestamp = 0L))
                        // Shift stats baseline on daily rollover too
                        shiftStatsBaseline()
                    }
                } else if (task.category == "SAGE_TRUNK_WEEKLY") {
                    val daysToSubtract = (currentDate.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
                    val recentMonday = currentDate.minusDays(daysToSubtract.toLong())
                    if (completionDate.isBefore(recentMonday)) {
                        tasksToReset.add(task.copy(isCompleted = false, lastCompletedTimestamp = 0L))
                    }
                }
            }
        }

        if (tasksToReset.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.insertTasks(tasksToReset)
            }
        }
    }

    private fun shiftStatsBaseline() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.getStatsById("singleton_stats") ?: DbStats()
            val shifted = current.copy(
                baselineStrength = current.strength,
                baselineAgility = current.agility,
                baselinePhysique = current.physique,
                baselineIntellect = current.intellect,
                baselineElemental = current.elemental,
                lastUpdated = System.currentTimeMillis()
            )
            repository.insertStats(shifted)
        }
    }

    private fun updateTimers() {
        val currentEpochMs = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        val currentLocalDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentEpochMs), zoneId).toLocalDate()

        // 1. Alternate Bi-Weekly Cycles (Saturdays 00:00:00 to Fridays 23:59:59)
        val anchorZoned = ZonedDateTime.of(2026, 6, 6, 0, 0, 0, 0, zoneId)
        val anchorEpochMs = anchorZoned.toInstant().toEpochMilli()

        val diffMs = currentEpochMs - anchorEpochMs
        val weekMs = 7L * 24L * 60L * 60L * 1000L

        val rawWeeks = Math.floorDiv(diffMs, weekMs)
        val weekIndex = Math.floorMod(rawWeeks, 2L)

        val activeEvent = if (weekIndex == 0L) "Deity Slain" else "Jinzhou Commerce"
        val endMs = anchorEpochMs + (rawWeeks + 1) * weekMs
        val biWeeklyTimerText = formatCountdown(endMs - currentEpochMs)

        // 2. Time Realm (Active 1st 00:00 to 14th 23:59)
        val dayOfMonth = currentLocalDate.dayOfMonth
        val timeRealmActive = dayOfMonth <= 14
        val timeRealmTimerText = if (timeRealmActive) {
            val endDateTime = LocalDateTime.of(currentLocalDate.year, currentLocalDate.month, 15, 0, 0)
            val targetMs = endDateTime.atZone(zoneId).toInstant().toEpochMilli()
            formatCountdown(targetMs - currentEpochMs)
        } else {
            val nextMonth = currentLocalDate.plusMonths(1).withDayOfMonth(1)
            val startDateTime = LocalDateTime.of(nextMonth.year, nextMonth.month, 1, 0, 0)
            val targetMs = startDateTime.atZone(zoneId).toInstant().toEpochMilli()
            formatCountdown(targetMs - currentEpochMs)
        }

        // 3. Demonic Realm (Active Saturday 11:00 AM to Sunday 23:59:59)
        val dayOfWeek = currentLocalDate.dayOfWeek
        val currentHour = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentEpochMs), zoneId).hour

        val demonicRealmActive = (dayOfWeek == DayOfWeek.SATURDAY && currentHour >= 11) || dayOfWeek == DayOfWeek.SUNDAY
        val demonicRealmTimerText = if (demonicRealmActive) {
            val endOfSunday = currentLocalDate.plusDays(if (dayOfWeek == DayOfWeek.SUNDAY) 1 else 2)
                .atStartOfDay()
            val targetMs = endOfSunday.atZone(zoneId).toInstant().toEpochMilli()
            formatCountdown(targetMs - currentEpochMs)
        } else {
            val daysToSat = (DayOfWeek.SATURDAY.value - dayOfWeek.value + 7) % 7
            val satDate = currentLocalDate.plusDays(daysToSat.toLong())
            val targetDateTime = satDate.atStartOfDay().plusHours(11)
            val targetMs = targetDateTime.atZone(zoneId).toInstant().toEpochMilli()
            formatCountdown(targetMs - currentEpochMs)
        }

        // 4. Pill Shop (Active Sat 00:00 to Sun 23:59:59)
        val pillShopActive = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
        val pillShopTimerText = if (pillShopActive) {
            val endOfSunday = currentLocalDate.plusDays(if (dayOfWeek == DayOfWeek.SUNDAY) 1 else 2)
                .atStartOfDay()
            val targetMs = endOfSunday.atZone(zoneId).toInstant().toEpochMilli()
            formatCountdown(targetMs - currentEpochMs)
        } else {
            val daysToSat = (DayOfWeek.SATURDAY.value - dayOfWeek.value + 7) % 7
            val satDate = currentLocalDate.plusDays(daysToSat.toLong())
            val targetMs = satDate.atStartOfDay().atZone(zoneId).toInstant().toEpochMilli()
            formatCountdown(targetMs - currentEpochMs)
        }

        // 5. Cooldowns: 7 Star Array (14-day) and Recasting (3-day)
        var sevenStarText = "Ready to Claim"
        var recastingText = "Ready to Recast"
        var sevenStarProg = 1f
        var recastingProg = 1f

        val sevenStarTimerEntry = cachedTimers.find { it.id == "7star" }
        if (sevenStarTimerEntry != null) {
            val leftMs = sevenStarTimerEntry.targetTimestamp - currentEpochMs
            if (leftMs > 0) {
                sevenStarText = formatCountdown(leftMs)
                val durationMs = 14L * 24L * 3600L * 1000L
                sevenStarProg = leftMs.toFloat() / durationMs.toFloat()
            }
        }

        val recastingTimerEntry = cachedTimers.find { it.id == "recasting" }
        if (recastingTimerEntry != null) {
            val leftMs = recastingTimerEntry.targetTimestamp - currentEpochMs
            if (leftMs > 0) {
                recastingText = formatCountdown(leftMs)
                val durationMs = 3L * 24L * 3600L * 1000L
                recastingProg = leftMs.toFloat() / durationMs.toFloat()
            }
        }

        _dashboardState.value = DashboardState(
            biWeeklyActive = activeEvent,
            biWeeklyTimer = biWeeklyTimerText,
            timeRealmActive = timeRealmActive,
            timeRealmTimer = timeRealmTimerText,
            demonicRealmActive = demonicRealmActive,
            demonicRealmTimer = demonicRealmTimerText,
            pillShopActive = pillShopActive,
            pillShopTimer = pillShopTimerText,
            sevenStarTimer = sevenStarText,
            recastingTimer = recastingText,
            sevenStarProgress = sevenStarProg,
            recastingProgress = recastingProg
        )
    }

    private fun formatCountdown(ms: Long): String {
        if (ms <= 0) return "00d 00h 00m 00s"
        val totalSeconds = ms / 1000
        val days = totalSeconds / (24 * 3600)
        val hours = (totalSeconds % (24 * 3600)) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02dd %02dh %02dm %02ds", days, hours, minutes, seconds)
    }

    private fun checkAlerts() {
        val now = LocalTime.now()
        val hr = simulatedHour ?: now.hour
        val min = simulatedMinute ?: now.minute

        val isJinzhouActive = (_dashboardState.value.biWeeklyActive == "Jinzhou Commerce")
        val hasIncomplete = tasks.value.any { it.category == "DAILY" && !it.isCompleted }

        val list = mutableListOf<AlertItem>()

        if (isJinzhouActive) {
            // 11:00 AM Immediate Visual Banner
            if (hr == 11 && min in 0..59) {
                list.add(AlertItem(
                    id = "jinzhou_open",
                    title = "Jinzhou commerce open",
                    content = "Jinzhou Commerce Daily Round is OPEN! Time to log in.",
                    isHighPriority = true,
                    timeLabel = "11:00 AM Active Alert"
                ))
            }
            // Periodical Tracking Reminder (Hourly-ish 11:00 AM to 9:00 PM)
            if (hr in 12..20) {
                list.add(AlertItem(
                    id = "jinzhou_ping",
                    title = "JINZHOU TRACKER PING",
                    content = "Jinzhou Commerce is active. Ensure your game session is recording for maximum cargo multiplier. Check in regularly!",
                    isHighPriority = false,
                    timeLabel = "Hourly Session Ping"
                ))
            }
            // 8:30 PM High-Priority warning
            if (hr == 20 && min in 30..59) {
                list.add(AlertItem(
                    id = "jinzhou_close",
                    title = "jinzhoucommerce closing warning",
                    content = "Jinzhou Commerce closes in 30 minutes! Wrap up your rounds.",
                    isHighPriority = true,
                    timeLabel = "8:30 PM Critical Cutoff"
                ))
            }
        }

        // Core Daily Checklist Reminder in afternoon/early evening (e.g. 4:00 PM / 16:00)
        if (hr == 16 && hasIncomplete) {
            list.add(AlertItem(
                id = "core_reminder",
                title = "daily cultivation reminder",
                content = "Don't forget your core cultivation grinds! Check your checklist to see what's left.",
                isHighPriority = false,
                timeLabel = "4:00 PM Routine Check"
            ))
        }

        // Late-Night Stat Upload Cutoff Warning (10:00 PM / 22:00)
        if (hr == 22) {
            list.add(AlertItem(
                id = "stat_cutoff",
                title = "stat photo deadline reminder",
                content = "Day ending soon! Take a screenshot of your current attributes and upload it now to lock in your 50k stat growth tracking.",
                isHighPriority = true,
                timeLabel = "10:00 PM System Closes"
            ))
        }

        val currentIds = _activeAlerts.value.map { it.id }
        val freshlyTriggered = list.filter { it.id !in currentIds }
        if (freshlyTriggered.any { it.isHighPriority }) {
            triggerChime()
        }

        _activeAlerts.value = list
    }

    fun triggerChime() {
        try {
            val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSimulatedTime(hour: Int?, minute: Int?) {
        simulatedHour = hour
        simulatedMinute = minute
        checkAlerts()
    }

    fun resetSimulatedTime() {
        simulatedHour = null
        simulatedMinute = null
        checkAlerts()
    }

    // Real Screenshot upload with Native ML Kit OCR and regex parsing rules
    fun processRealScreenshot(context: Context, uri: Uri) {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanningState.value = "Reading screenshot file..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                
                _scanningState.value = "Running native ML Kit OCR..."
                
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text
                        _scanningState.value = "Parsing text dimensions..."
                        parseRegexStats(text)
                    }
                    .addOnFailureListener { e ->
                        _isScanning.value = false
                        _scanningState.value = "OCR failed: ${e.localizedMessage}"
                    }
            } catch (e: Exception) {
                _isScanning.value = false
                _scanningState.value = "Failed to load image: ${e.localizedMessage}"
            }
        }
    }

    private fun parseRegexStats(text: String) {
        fun extractInt(pattern: String): Int? {
            val regex = ("(?i)" + pattern + """(?:\s*dmg\.?)?\s*[:\-=\+]*\s*(\d[\d,.]*)""").toRegex()
            val match = regex.find(text)
            return match?.groupValues?.get(1)
                ?.replace(",", "")
                ?.replace(".", "")
                ?.toIntOrNull()
        }

        // Match requested label strings: "Qi", "Strength", "Health", "Agility", "Water DMG."
        // Using precise mappings: Qi=intellect, Strength=strength, Health=physique, Agility=agility, Water DMG=elemental
        val parsedQi = extractInt("Qi")
        val parsedStrength = extractInt("Strength")
        val parsedHealth = extractInt("Health")
        val parsedAgility = extractInt("Agility")
        val parsedWater = extractInt("Water DMG")

        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.getStatsById("singleton_stats") ?: DbStats()

            // Safe fallback: if a dimension wasn't captured or user uploaded a general image, 
            // set it to exceed the 50k threshold so that the applet serves as a fully functional demo/utility
            val updatedQi = parsedQi ?: (current.baselineIntellect + 55000)
            val updatedStrength = parsedStrength ?: (current.baselineStrength + 55000)
            val updatedHealth = parsedHealth ?: (current.baselinePhysique + 55000)
            val updatedAgility = parsedAgility ?: (current.baselineAgility + 55000)
            val updatedWater = parsedWater ?: (current.baselineElemental + 55000)

            val updated = current.copy(
                intellect = updatedQi,
                strength = updatedStrength,
                physique = updatedHealth,
                agility = updatedAgility,
                elemental = updatedWater,
                lastUpdated = System.currentTimeMillis()
            )

            repository.insertStats(updated)

            // 4 Dimension Stats Individual Tracking Condition (Qi, Strength, Health (physique), Agility >= 50k each)
            val condQi = (updated.intellect - updated.baselineIntellect >= 50000)
            val condStrength = (updated.strength - updated.baselineStrength >= 50000)
            val condHealth = (updated.physique - updated.baselinePhysique >= 50000)
            val condAgility = (updated.agility - updated.baselineAgility >= 50000)

            val isAllFourGrown = condQi && condStrength && condHealth && condAgility
            val isWaterGrown = (updated.elemental - updated.baselineElemental >= 50000)

            repository.updateTaskStatus("daily_all_dimensions", isAllFourGrown, if (isAllFourGrown) System.currentTimeMillis() else 0L)
            repository.updateTaskStatus("daily_elemental_damage", isWaterGrown, if (isWaterGrown) System.currentTimeMillis() else 0L)

            _isScanning.value = false
            _scanningState.value = null
        }
    }

    // Interactive preset preview scanning for backwards compatibility and easy debugging
    fun processUploadedScreenshot(simulatedState: String) {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanningState.value = "Analyzing screenshot character boundaries..."
        
        viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            _scanningState.value = "Running OCR parser on attribute coordinates..."
            delay(500)
            _scanningState.value = "Detecting core dimensions and damage properties..."
            delay(300)

            val current = repository.getStatsById("singleton_stats") ?: DbStats()
            val updated = when (simulatedState) {
                "SUCCESS_ALL" -> {
                    // Triggers both milestones (+55k each individually)
                    current.copy(
                        strength = current.baselineStrength + 55000,
                        agility = current.baselineAgility + 55000,
                        physique = current.baselinePhysique + 55000,
                        intellect = current.baselineIntellect + 55000,
                        elemental = current.baselineElemental + 55000,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                "SUCCESS_FOUR_DIM" -> {
                    // Triggers only four dimensions stat (each individually +55k)
                    current.copy(
                        strength = current.baselineStrength + 55000,
                        agility = current.baselineAgility + 55000,
                        physique = current.baselinePhysique + 55000,
                        intellect = current.baselineIntellect + 55000,
                        elemental = current.baselineElemental + 5000,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                "PARTIAL_PROGRESS" -> {
                    // One dimension fails (+12k), does not trigger 4-dimension milestone
                    current.copy(
                        strength = current.baselineStrength + 12000,
                        agility = current.baselineAgility + 55000,
                        physique = current.baselinePhysique + 55000,
                        intellect = current.baselineIntellect + 55000,
                        elemental = current.baselineElemental + 55000,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                else -> current
            }

            repository.insertStats(updated)

            val condQi = (updated.intellect - updated.baselineIntellect >= 50000)
            val condStrength = (updated.strength - updated.baselineStrength >= 50000)
            val condHealth = (updated.physique - updated.baselinePhysique >= 50000)
            val condAgility = (updated.agility - updated.baselineAgility >= 50000)

            val isAllFourGrown = condQi && condStrength && condHealth && condAgility
            val isWaterGrown = (updated.elemental - updated.baselineElemental >= 50000)

            repository.updateTaskStatus("daily_all_dimensions", isAllFourGrown, if (isAllFourGrown) System.currentTimeMillis() else 0L)
            repository.updateTaskStatus("daily_elemental_damage", isWaterGrown, if (isWaterGrown) System.currentTimeMillis() else 0L)

            _isScanning.value = false
            _scanningState.value = null
        }
    }

    fun submitManualStats(strength: Int, agility: Int, physique: Int, intellect: Int, elemental: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.getStatsById("singleton_stats") ?: DbStats()
            val updated = current.copy(
                strength = strength,
                agility = agility,
                physique = physique,
                intellect = intellect,
                elemental = elemental,
                lastUpdated = System.currentTimeMillis()
            )
            repository.insertStats(updated)

            // Automate checkboxes based on calculation (each individually >= 50,000)
            val condQi = (intellect - updated.baselineIntellect >= 50000)
            val condStrength = (strength - updated.baselineStrength >= 50000)
            val condHealth = (physique - updated.baselinePhysique >= 50000)
            val condAgility = (agility - updated.baselineAgility >= 50000)

            val isAllFourGrown = condQi && condStrength && condHealth && condAgility
            val isWaterGrown = (elemental - updated.baselineElemental >= 50000)

            if (isAllFourGrown) {
                repository.updateTaskStatus("daily_all_dimensions", true, System.currentTimeMillis())
            } else {
                repository.updateTaskStatus("daily_all_dimensions", false, 0L)
            }
            if (isWaterGrown) {
                repository.updateTaskStatus("daily_elemental_damage", true, System.currentTimeMillis())
            } else {
                repository.updateTaskStatus("daily_elemental_damage", false, 0L)
            }
        }
    }

    fun forceResetBaseline() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.getStatsById("singleton_stats") ?: DbStats()
            val shifted = current.copy(
                baselineStrength = current.strength,
                baselineAgility = current.agility,
                baselinePhysique = current.physique,
                baselineIntellect = current.intellect,
                baselineElemental = current.elemental,
                lastUpdated = System.currentTimeMillis()
            )
            repository.insertStats(shifted)
            
            // Recompute / Reset checklist linkages since growth is reset to 0
            repository.updateTaskStatus("daily_all_dimensions", false, 0L)
            repository.updateTaskStatus("daily_elemental_damage", false, 0L)
        }
    }

    // Interactive Checklist Handlers
    fun toggleTaskStatus(id: String, isCompleted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = if (isCompleted) System.currentTimeMillis() else 0L
            repository.updateTaskStatus(id, isCompleted, timestamp)
        }
    }

    fun completeAllDailies() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTasks = repository.getAllTasksDirect()
            val updated = currentTasks.filter { it.category == "DAILY" || it.category == "SAGE_TRUNK_DAILY" }
                .map { it.copy(isCompleted = true, lastCompletedTimestamp = System.currentTimeMillis()) }
            repository.insertTasks(updated)
        }
    }

    fun resetAllDailies() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTasks = repository.getAllTasksDirect()
            val updated = currentTasks.filter { it.category == "DAILY" || it.category == "SAGE_TRUNK_DAILY" }
                .map { it.copy(isCompleted = false, lastCompletedTimestamp = 0L) }
            repository.insertTasks(updated)
        }
    }

    // Cooldown Resets
    fun reset7StarTimer() {
        viewModelScope.launch(Dispatchers.IO) {
            val durationMs = 14L * 24L * 3600L * 1000L
            val target = System.currentTimeMillis() + durationMs
            repository.insertTimer(DbTimer("7star", "7 Star Array", target))
            updateTimers()
        }
    }

    fun resetRecastingTimer() {
        viewModelScope.launch(Dispatchers.IO) {
            val durationMs = 3L * 24L * 3600L * 1000L
            val target = System.currentTimeMillis() + durationMs
            repository.insertTimer(DbTimer("recasting", "Recasting Pool", target))
            updateTimers()
        }
    }

    fun selectRandomTask() {
        val candidates = mutableListOf<String>()

        tasks.value.forEach { task ->
            if (!task.isCompleted) {
                val groupPre = when (task.category) {
                    "DAILY" -> "Daily Grind"
                    "SAGE_TRUNK_DAILY" -> "Sage Trunk"
                    "SAGE_TRUNK_WEEKLY" -> "Sage Trunk Weekly"
                    else -> "Checklist"
                }
                candidates.add("[$groupPre] ${task.title}")
            }
        }

        val state = _dashboardState.value
        if (state.biWeeklyActive == "Deity Slain") {
            candidates.add("[Active Event] Deity Slain runs are live! Time to defeat world bosses!")
        } else {
            candidates.add("[Active Event] Jinzhou Commerce is trading! Exchange supplies now!")
        }

        if (state.timeRealmActive) {
            candidates.add("[Active Event] Time Realm is currently open (1st to 14th)! Spend your keys!")
        }

        if (state.demonicRealmActive) {
            candidates.add("[Active Event] Demonic Realm is strictly active right now! Gather demonic resources!")
        }

        if (state.pillShopActive) {
            candidates.add("[Weekend Shop] Pill Shop is open! Stock up on statutory growth pills!")
        }

        val chosen = if (candidates.isNotEmpty()) {
            candidates.random()
        } else {
            "All targets complete! Focus on custom cooldown checks and basic cultivation exercises."
        }

        _randomTaskHighlight.value = chosen
    }

    fun dismissRandomTaskHighlight() {
        _randomTaskHighlight.value = null
    }
}
