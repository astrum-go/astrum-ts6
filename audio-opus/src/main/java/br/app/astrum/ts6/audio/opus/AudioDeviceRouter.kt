package br.app.astrum.ts6.audio.opus

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

class AudioDeviceRouter(
    context: Context,
    private val onRoutingChanged: (AudioRoutingState) -> Unit,
) : AutoCloseable {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val lifecycleLock = Any()
    @Volatile
    private var started = false
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeakerphoneOn = false
    private var previousBluetoothScoOn = false
    private var selectedRouteId = SYSTEM_AUDIO_ROUTE_ID

    @Volatile
    var state: AudioRoutingState = AudioRoutingState.Default
        private set

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshDevices()
        }
    }

    @Suppress("DEPRECATION")
    fun start() {
        synchronized(lifecycleLock) {
            if (started) return
            previousMode = audioManager.mode
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
            previousBluetoothScoOn = audioManager.isBluetoothScoOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.registerAudioDeviceCallback(deviceCallback, callbackHandler)
            started = true
        }
        refreshDevices()
    }

    fun selectRoute(routeId: Int) {
        val routes = availableRoutes()
        if (routes.none { it.id == routeId }) {
            publishState(
                routes = routes,
                error = "Dispositivo de áudio indisponível",
            )
            return
        }

        val output = outputDevice(routeId)
        val switched = runCatching { applySystemRoute(output) }
            .getOrElse { error ->
                publishState(routes, "Falha ao trocar de dispositivo de áudio: ${error.message ?: error.javaClass.simpleName}")
                return
            }
        if (!switched) {
            publishState(routes, "O sistema recusou a troca para este dispositivo de áudio")
            return
        }

        selectedRouteId = routeId
        publishState(routes, error = null)
    }

    fun preferredOutputDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return null
        return outputDevice(selectedRouteId)
    }

    fun preferredInputDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return null
        val output = outputDevice(selectedRouteId) ?: return null
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        val preferredTypes = inputTypesFor(output.type)
        val sameAddressInput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            inputs.firstOrNull { candidate ->
                candidate.type == output.type &&
                    output.address.isNotBlank() &&
                    candidate.address == output.address
            }
        } else {
            null
        }
        return sameAddressInput ?: preferredTypes.firstNotNullOfOrNull { type ->
            inputs.firstOrNull { it.type == type }
        }
    }

    override fun close() = stop()

    @Suppress("DEPRECATION")
    fun stop() {
        synchronized(lifecycleLock) {
            if (!started) return
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                if (previousBluetoothScoOn) {
                    audioManager.startBluetoothSco()
                } else {
                    audioManager.stopBluetoothSco()
                }
                audioManager.isBluetoothScoOn = previousBluetoothScoOn
                audioManager.isSpeakerphoneOn = previousSpeakerphoneOn
            }
            audioManager.mode = previousMode
            selectedRouteId = SYSTEM_AUDIO_ROUTE_ID
            state = AudioRoutingState.Default
            started = false
        }
    }

    private fun refreshDevices() {
        if (!started) return
        val routes = availableRoutes()
        val resolvedRouteId = resolveSelectedRouteId(selectedRouteId, routes)
        val routeWasRemoved = resolvedRouteId != selectedRouteId
        if (routeWasRemoved) {
            selectedRouteId = SYSTEM_AUDIO_ROUTE_ID
            runCatching { applySystemRoute(null) }
        } else if (selectedRouteId != SYSTEM_AUDIO_ROUTE_ID) {
            runCatching { applySystemRoute(outputDevice(selectedRouteId)) }
        }
        publishState(
            routes = routes,
            error = if (routeWasRemoved) "Dispositivo de áudio selecionado desconectado; voltando ao modo automático do sistema" else null,
        )
    }

    private fun publishState(routes: List<AudioRouteOption>, error: String?) {
        val newState = AudioRoutingState(
            routes = routes,
            selectedRouteId = resolveSelectedRouteId(selectedRouteId, routes),
            error = error,
        )
        state = newState
        onRoutingChanged(newState)
    }

    private fun availableRoutes(): List<AudioRouteOption> {
        val outputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
        return buildList {
            add(AudioRoutingState.SystemRoute)
            outputs
                .mapNotNull(::toRouteOption)
                .distinctBy(AudioRouteOption::id)
                .sortedWith(compareBy({ routeOrder(it.kind) }, AudioRouteOption::label))
                .forEach(::add)
        }
    }

    private fun outputDevice(routeId: Int): AudioDeviceInfo? {
        if (routeId == SYSTEM_AUDIO_ROUTE_ID) return null
        val outputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
        return outputs.firstOrNull { it.id == routeId }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun applySystemRoute(output: AudioDeviceInfo?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (output == null) {
                audioManager.clearCommunicationDevice()
                return true
            }
            return audioManager.setCommunicationDevice(output)
        }

        when (output?.let(::routeKind)) {
            AudioRouteKind.SPEAKER -> {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.isSpeakerphoneOn = true
            }

            AudioRouteKind.BLUETOOTH -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }

            else -> {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.isSpeakerphoneOn = false
            }
        }
        return true
    }

    private fun toRouteOption(device: AudioDeviceInfo): AudioRouteOption? {
        val kind = routeKind(device)
        if (kind == AudioRouteKind.OTHER) return null
        val baseLabel = routeLabel(kind)
        val productName = device.productName?.toString()?.trim().orEmpty()
        val shouldShowProductName = kind in setOf(
            AudioRouteKind.WIRED,
            AudioRouteKind.BLUETOOTH,
            AudioRouteKind.USB,
        )
        val label = if (
            shouldShowProductName &&
            productName.isNotBlank() &&
            !productName.equals(baseLabel, ignoreCase = true)
        ) {
            "$baseLabel · $productName"
        } else {
            baseLabel
        }
        return AudioRouteOption(device.id, kind, label)
    }

    private companion object {
        fun routeKind(device: AudioDeviceInfo): AudioRouteKind = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRouteKind.EARPIECE
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRouteKind.SPEAKER
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            -> AudioRouteKind.WIRED

            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
            -> AudioRouteKind.BLUETOOTH

            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            -> AudioRouteKind.USB

            else -> AudioRouteKind.OTHER
        }

        fun routeLabel(kind: AudioRouteKind): String = when (kind) {
            AudioRouteKind.SYSTEM -> "Sistema (automático)"
            AudioRouteKind.EARPIECE -> "Auricular"
            AudioRouteKind.SPEAKER -> "Alto-falante"
            AudioRouteKind.WIRED -> "Fone com fio"
            AudioRouteKind.BLUETOOTH -> "Dispositivo Bluetooth"
            AudioRouteKind.USB -> "Áudio USB"
            AudioRouteKind.OTHER -> "Outro dispositivo"
        }

        fun routeOrder(kind: AudioRouteKind): Int = when (kind) {
            AudioRouteKind.SYSTEM -> 0
            AudioRouteKind.EARPIECE -> 1
            AudioRouteKind.SPEAKER -> 2
            AudioRouteKind.WIRED -> 3
            AudioRouteKind.BLUETOOTH -> 4
            AudioRouteKind.USB -> 5
            AudioRouteKind.OTHER -> 6
        }

        fun inputTypesFor(outputType: Int): List<Int> = when (outputType) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> listOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BUILTIN_MIC,
            )

            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> listOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BUILTIN_MIC,
            )

            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            -> listOf(
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_BUILTIN_MIC,
            )

            else -> listOf(AudioDeviceInfo.TYPE_BUILTIN_MIC)
        }
    }
}
