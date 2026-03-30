package com.example.updateapp.update

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.updateapp.update.models.UpdateInfo
import com.example.updateapp.update.network.UpdateApiService

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    object Installing : UpdateState()
    object UpToDate : UpdateState()
    object Success : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class UpdateManager(private val context: Context) {
    private val _state = mutableStateOf<UpdateState>(UpdateState.Idle)
    val state: State<UpdateState> = _state

    private val apiService: UpdateApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://your-server.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UpdateApiService::class.java)
    }

    private val updateService = UpdateService(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var currentUpdateInfo: UpdateInfo? = null

    fun checkForUpdates() {
        scope.launch {
            _state.value = UpdateState.Checking
            try {
                val updateInfo = withContext(Dispatchers.IO) {
                    apiService.checkForUpdate()
                }
                currentUpdateInfo = updateInfo
                _state.value = UpdateState.UpdateAvailable(updateInfo)
            } catch (e: Exception) {
                _state.value = UpdateState.Error("Failed to check for updates: ${e.message}")
            }
        }
    }

    fun downloadAndInstallApk() {
        val updateInfo = currentUpdateInfo ?: return
        scope.launch {
            _state.value = UpdateState.Downloading(0f)
            try {
                updateService.downloadApk(
                    downloadUrl = updateInfo.downloadUrl,
                    onProgress = { downloaded, total ->
                        val progress = (downloaded.toFloat() / total.toFloat()) * 100
                        _state.value = UpdateState.Downloading(progress)
                    },
                    onSuccess = { apkFile ->
                        _state.value = UpdateState.Installing
                        updateService.installApk(apkFile)
                        _state.value = UpdateState.Success
                    },
                    onError = { error ->
                        _state.value = UpdateState.Error("Download failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _state.value = UpdateState.Error("Installation failed: ${e.message}")
            }
        }
    }

    fun skipUpdate() {
        _state.value = UpdateState.UpToDate
    }

    fun cancel() {
        scope.cancel()
        updateService.cleanupApk()
        _state.value = UpdateState.Idle
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }
}