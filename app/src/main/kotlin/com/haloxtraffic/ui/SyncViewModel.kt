package com.haloxtraffic.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haloxtraffic.core.data.repository.IntegrityResult
import com.haloxtraffic.core.data.repository.SealingRepository
import com.haloxtraffic.core.evidence.SealedStore
import com.haloxtraffic.core.sync.SyncQueue
import com.haloxtraffic.core.sync.SyncWorker
import com.haloxtraffic.core.sync.api.HaloxApi
import com.haloxtraffic.core.sync.api.VahanLookupRequest
import com.haloxtraffic.core.sync.api.VahanLookupResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VahanState(val querying: Boolean = false, val result: VahanLookupResponse? = null, val error: String? = null)

@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncQueue: SyncQueue,
    private val sealingRepository: SealingRepository,
    private val sealedStore: SealedStore,
    private val api: HaloxApi,
) : ViewModel() {

    val pendingCount: StateFlow<Int> =
        syncQueue.pendingCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _integrity = MutableStateFlow<IntegrityResult?>(null)
    val integrity: StateFlow<IntegrityResult?> = _integrity

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes

    private val _vahan = MutableStateFlow(VahanState())
    val vahan: StateFlow<VahanState> = _vahan

    init { refreshStorage() }

    fun runSyncNow() = SyncWorker.runNow(context)

    fun checkIntegrity() = viewModelScope.launch { _integrity.value = sealingRepository.verifyIntegrity() }

    fun refreshStorage() = viewModelScope.launch { _storageBytes.value = sealedStore.totalBytes() }

    fun lookupVahan(plate: String) = viewModelScope.launch {
        _vahan.value = VahanState(querying = true)
        _vahan.value = runCatching { api.vahanLookup(VahanLookupRequest(plate.uppercase().filter { it.isLetterOrDigit() })) }
            .fold(
                onSuccess = { VahanState(result = it) },
                onFailure = { VahanState(error = it.message ?: "lookup failed (offline?)") },
            )
    }
}
