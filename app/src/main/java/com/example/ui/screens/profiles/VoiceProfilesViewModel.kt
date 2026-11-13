package com.example.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.VoiceProfileDao
import com.example.data.local.VoiceProfileEntity
import com.example.data.preferences.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoiceProfilesViewModel(
    private val voiceProfileDao: VoiceProfileDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val profiles: StateFlow<List<VoiceProfileEntity>> = voiceProfileDao.observeAllProfiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _unlockedProfileIds = MutableStateFlow<Set<Long>>(emptySet())
    val unlockedProfileIds: StateFlow<Set<Long>> = _unlockedProfileIds.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    fun renameProfile(id: Long, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            voiceProfileDao.renameProfile(id, clean)
        }
    }

    fun toggleProtection(profile: VoiceProfileEntity) {
        viewModelScope.launch {
            voiceProfileDao.setProfileProtected(profile.id, !profile.isProtected)
            if (!profile.isProtected) {
                _unlockedProfileIds.value = _unlockedProfileIds.value - profile.id
            }
        }
    }

    fun deleteProfile(profile: VoiceProfileEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { File(profile.sampleFilePath).delete() }
            }
            voiceProfileDao.deleteProfileById(profile.id)
        }
    }

    fun verifyAndUnlock(profileId: Long, enteredPin: String, storedHash: String?, wrongPinMsg: String): Boolean {
        val ok = settingsRepository.verifyPin(enteredPin, storedHash)
        if (ok) {
            _unlockedProfileIds.value = _unlockedProfileIds.value + profileId
            _pinError.value = null
        } else {
            _pinError.value = wrongPinMsg
        }
        return ok
    }

    fun clearPinError() {
        _pinError.value = null
    }
}
