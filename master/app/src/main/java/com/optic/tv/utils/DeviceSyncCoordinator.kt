package com.optic.tv.utils

import android.content.Context
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Resolves playlist config:
 * - **Public mode** ([SharedPrefManager.SP_USE_PRIVATE_PLAYLIST] false): always [sync/global].
 * - **Private mode** (pref true): if [DeviceAssignment] is active with a non-global [syncKey],
 *   loads [sync/syncKey] only; otherwise [onPrivateNotLinked].
 */
object DeviceSyncCoordinator {

    fun loadEffectivePlaylist(
        context: Context,
        onLoaded: (SyncData) -> Unit,
        onGlobalMissing: () -> Unit,
        onDedicatedMissing: () -> Unit,
        onPrivateNotLinked: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val prefs = SharedPrefManager(context)
        val service = GlobalSync.retrofit().create(IptvService::class.java)

        if (!prefs.getSpBoolean(SharedPrefManager.SP_USE_PRIVATE_PLAYLIST, false)) {
            fetchGlobal(service, onLoaded, onGlobalMissing, onFailure)
            return
        }

        val deviceCode = ActivationHelper.getDeviceCode(context)
        service.getDeviceAssignment(deviceCode).enqueue(object : Callback<DeviceAssignment> {
            override fun onResponse(
                call: Call<DeviceAssignment>,
                response: Response<DeviceAssignment>,
            ) {
                if (!response.isSuccessful) {
                    onPrivateNotLinked()
                    return
                }
                val assignment = response.body()
                val key = assignment?.syncKey?.trim()?.lowercase().orEmpty()
                val useDedicated = assignment?.active == true &&
                    key.isNotEmpty() &&
                    key != GlobalSync.SYNC_KEY_GLOBAL

                if (useDedicated) {
                    fetchDedicated(service, key, onLoaded, onDedicatedMissing, onFailure)
                } else {
                    onPrivateNotLinked()
                }
            }

            override fun onFailure(call: Call<DeviceAssignment>, t: Throwable) {
                val m = t.message ?: ""
                if (m.isBlank()) onPrivateNotLinked() else onFailure(m)
            }
        })
    }

    private fun fetchDedicated(
        service: IptvService,
        key: String,
        onLoaded: (SyncData) -> Unit,
        onDedicatedMissing: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        service.getSyncByKey(key).enqueue(object : Callback<SyncData> {
            override fun onResponse(call: Call<SyncData>, response: Response<SyncData>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    onLoaded(body)
                } else {
                    onDedicatedMissing()
                }
            }

            override fun onFailure(call: Call<SyncData>, t: Throwable) {
                val m = t.message ?: ""
                if (m.isBlank()) onDedicatedMissing() else onFailure(m)
            }
        })
    }

    private fun fetchGlobal(
        service: IptvService,
        onLoaded: (SyncData) -> Unit,
        onGlobalMissing: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        service.getGlobalSync().enqueue(object : Callback<SyncData> {
            override fun onResponse(call: Call<SyncData>, response: Response<SyncData>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    onLoaded(body)
                } else {
                    onGlobalMissing()
                }
            }

            override fun onFailure(call: Call<SyncData>, t: Throwable) {
                onFailure(t.message ?: "")
            }
        })
    }
}
