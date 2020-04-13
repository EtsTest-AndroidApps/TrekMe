package com.peterlaurence.trekme.core.wifip2p

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.*
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.peterlaurence.trekme.R
import com.peterlaurence.trekme.core.TrekMeContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WifiP2pService : Service() {
    private val notificationChannelId = "peterlaurence.WifiP2pService"
    private val wifiP2pServiceNofificationId = 659531
    private val intentFilter = IntentFilter()
    private var mode: StartAction? = null
    private var channel: WifiP2pManager.Channel? = null
    private var manager: WifiP2pManager? = null
    private val peerListChannel = Channel<WifiP2pDeviceList>()
    private var job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main)

    private val serviceName = "_trekme_mapshare"
    private val serviceType = "_presence._tcp"
    private val listenPort = 8988

    private var isWifiP2pEnabled = false
        private set(value) {
            field = value
//            if (!value) resetWifiP2p()
        }

    private var isDiscoveryActive = false

    private var isNetworkAvailable = false

    enum class StartAction {
        START_SEND, START_RCV
    }

    object StopAction

    private var serviceStarted = false

    private var wifiP2pState: WifiP2pState = Stopped
        private set(value) {
            field = value
            stateChannel.offer(value)
        }

    companion object {
        private val stateChannel = ConflatedBroadcastChannel<WifiP2pState>()
        val stateFlow: Flow<WifiP2pState> = stateChannel.asFlow()

//        private val progressChannel = ConflatedBroadcastChannel<Int>()
//        val progressFlow: Flow<Int> = progressChannel.asFlow()
    }

    private val receiver: BroadcastReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        isWifiP2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        println("Wifi P2p enabled: $isWifiP2pEnabled")
                    }
                    WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
                        isDiscoveryActive = state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        println("Peers changed")
                        /* Request available peers */
                        val channel = channel ?: return
                        scope.launch {
                            val peers = manager?.requestPeers(channel)
                            if (peers != null) peerListChannel.offer(peers)
                            // we have a list of peers - should display in a list ?
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        manager?.let { manager ->
                            println("Notice $isNetworkAvailable")

                            if (channel == null) return@let
                            manager.requestConnectionInfo(channel, object : WifiP2pManager.ConnectionInfoListener {
                                override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
                                    println("Connection info")
                                    println(info?.isGroupOwner)
                                    println(info?.groupOwnerAddress?.hostAddress)

                                    if (info == null) return
                                    if (info.isGroupOwner) {
                                        // server
                                        if (mode!! == StartAction.START_RCV) {
                                            serverReceives()
                                        } else {
                                            serverSends()
                                        }
                                    } else {
                                        // client
                                        if (info.groupOwnerAddress == null) return
                                        val inetSocketAddress = InetSocketAddress(info.groupOwnerAddress?.hostAddress, listenPort)
                                        if (mode!! == StartAction.START_RCV) {
                                            clientReceives(inetSocketAddress)
                                        } else {
                                            clientSends(inetSocketAddress)
                                        }
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onCreate() {
        super.onCreate()

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerNetworkCallback(NetworkRequest.Builder().build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        println("Network changed: available")
                        isNetworkAvailable = true
                    }

                    override fun onLost(network: Network) {
                        println("Network changed: lost")
                        isNetworkAvailable = false
                    }
                })

        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager

        /* register the BroadcastReceiver with the intent values to be matched  */
        registerReceiver(receiver, intentFilter)
    }

    /**
     * Accepts only three possible actions:
     * * [StartAction.START_RCV]  -> Starts the service in receiving mode
     * * [StartAction.START_SEND] -> Starts the service in sending mode
     * * [StopAction]             -> Stops the service
     */
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action !in (StartAction.values().map { it.name } + StopAction::class.java.name)) {
            Log.e(TAG, "Illegal action sent to WifiP2pService")
            return START_NOT_STICKY
        }

        /* If the user used the notification action-stop button, stop the service */
        if (intent.action == StopAction::class.java.name) {
            /* Unregister Android-specific listener */
            runCatching {
                // May throw IllegalStateException
                unregisterReceiver(receiver)
            }

            /* Stop the WifiP2p framework */
            scope.launch(NonCancellable) {
                stopForeground(true)
                resetWifiP2p()

                /* Stop the service */
                serviceStarted = false
                scope.cancel()
                stopSelf()
            }

            return START_NOT_STICKY
        }

        val notificationBuilder = NotificationCompat.Builder(applicationContext, notificationChannelId)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.service_location_action))
                .setSmallIcon(R.drawable.ic_share_black_24dp)
                .setOngoing(true)

        /* This is only needed on Devices on Android O and above */
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chan = NotificationChannel(notificationChannelId, getText(R.string.service_wifip2p_name), NotificationManager.IMPORTANCE_DEFAULT)
            chan.enableLights(true)
            chan.lightColor = Color.YELLOW
            notificationManager.createNotificationChannel(chan)
            notificationBuilder.setChannelId(notificationChannelId)
        }

        startForeground(wifiP2pServiceNofificationId, notificationBuilder.build())

        serviceStarted = true

        scope.launch {
            runCatching {
                initialize()
                if (intent.action == StartAction.START_RCV.name) {
                    mode = StartAction.START_RCV
                    startRegistration()
                }
                if (intent.action == StartAction.START_SEND.name) {
                    mode = StartAction.START_SEND
                    val device = discoverReceivingDevice()
                    connectDevice(device)
                }
            }.onFailure {
                println("Caught exception $it")
                // Warn the user that Wifi P2P isn't supported
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun initialize() {
        channel = manager?.initialize(this, mainLooper) {
            channel = null

            /* Notify stopped */
            wifiP2pState = Stopped
        }

        /* Notify started */
        wifiP2pState = Started

        println("Starting peer discovery..")
        val channel = channel ?: return
        manager?.discoverPeers(channel)
        peerListChannel.receive().also {
            println("Peer list updated")
            println(it)
        }
    }

    private suspend fun startRegistration() {
        val record: Map<String, String> = mapOf(
                "listenport" to listenPort.toString(),
                "available" to "visible"
        )

        val serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance(serviceName, "_presence._tcp", record)

        val channel = channel ?: return
        val manager = manager ?: return
        manager.addLocalService(channel, serviceInfo).also { success ->
            if (success) {
                println("Local service SUCCESS")
            } else {
                println("Local service FAIL")
            }
        }
    }

    /**
     * Discovers the service which the receiving device exposes.
     * If the service is discovered, this suspending function resumes with a [WifiP2pDevice].
     * If anything goes wrong other than an IllegalStateException, the continuation is cancelled.
     * An IllegalStateException is thrown if WifiP2P isn't supported by the sending device.
     */
    private suspend fun discoverReceivingDevice(): WifiP2pDevice = suspendCancellableCoroutine { cont ->
        val channel = channel ?: return@suspendCancellableCoroutine
        val manager = manager ?: return@suspendCancellableCoroutine
        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomain, record, device ->
            println("First listener")
            Log.d(TAG, "DnsSdTxtRecord available -$record")
            if (fullDomain.startsWith("_trekme_mapshare")) {
                record["listenport"]?.also {
                    println(device.deviceAddress)
                    if (cont.isActive) cont.resume(device)
                }
            }
        }

        val servListener = WifiP2pManager.DnsSdServiceResponseListener { _, _, _ ->
            // Don't care for now
        }

        manager.setDnsSdResponseListeners(channel, servListener, txtListener)

        /* Now that listeners are set, discover the service */
        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance(serviceName, serviceType)
        scope.launch {
            runCatching {
                println("Making service request..")
                val serviceAdded = manager.addServiceRequest(channel, serviceRequest)
                if (serviceAdded) {
                    println("Discovering services..")
                    manager.discoverServices(channel).also { success ->
                        if (success) {
                            println("Service successfully discovered")
                        } else {
                            // Something went wrong. Alert user - retry?
                            cont.cancel()
                        }
                    }
                } else {
                    // Something went wrong. Alert user - retry?
                    cont.cancel()
                }
            }.onFailure {
                cont.resumeWithException(IllegalStateException())
            }
        }
    }

    private suspend fun connectDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }
        val channel = channel ?: return
        manager?.connect(channel, config).also {
            println("Connection success $it")
        }
    }

    private fun serverReceives() = scope.launch(Dispatchers.IO) {
        val serverSocket = ServerSocket(listenPort)
        serverSocket.use {
            /* Wait for client connection. Blocks until a connection is accepted from a client */
            println("waiting for client connect..")
            val client = serverSocket.accept()

            /* The client is assumed to write into a DataOutputStream */
            println("Client connected!")
            val inputStream = DataInputStream(client.getInputStream())

            val mapName = inputStream.readUTF()
            println("Recieving $mapName from server")
            val size = inputStream.readLong()
            println("Size: $size")

            var c = 0L
            var x = 0
            val myOutput = object: OutputStream() {
                override fun write(b: Int) {
                    x++
                    val percent = c++.toFloat() / size
                    if (x > DEFAULT_BUFFER_SIZE) {
                        println(percent)
                        x = 0
                    }
                }
            }

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytes = inputStream.read(buffer)
            while (bytes >= 0 && isActive) {
                myOutput.write(buffer, 0, bytes)
                try {
                    bytes = inputStream.read(buffer)
                } catch (e: SocketException) {
                    break
                }
            }
            inputStream.close()
            myOutput.close()
            serverSocket.close()
        }
        println("Closed server socket")
    }

    private fun serverSends() = scope.launch(Dispatchers.IO) {
        val serverSocket = ServerSocket(listenPort)
        serverSocket.use {
            /* Wait for client connection. Blocks until a connection is accepted from a client */
            println("waiting for client connect..")
            val client = serverSocket.accept()

            val outputStream = DataOutputStream(client.getOutputStream())
            val archivesDir = File(TrekMeContext.defaultAppDir, "archives")
            archivesDir.listFiles()?.firstOrNull()?.also {
                println("Sending ${it.name}")
                outputStream.writeUTF(it.name)
                val totalByteCount = it.length()
                outputStream.writeLong(totalByteCount)
                FileInputStream(it).use {
                    it.copyToWithProgress(outputStream, this, totalByteCount)
                }
            }
            outputStream.close()
            serverSocket.close()
        }
        println("Closed server socket")
    }

    private fun clientSends(socketAddress: InetSocketAddress) = scope.launch(Dispatchers.IO) {
        // TODO: catch SockectException: Connection reset (server stops)
        val socket = Socket()
        socket.bind(null)
        socket.connect(socketAddress)

        val outputStream = DataOutputStream(socket.getOutputStream())
        val archivesDir = File(TrekMeContext.defaultAppDir, "archives")
        archivesDir.listFiles()?.firstOrNull()?.also {
            println("Sending ${it.name}")
            try {
                val totalByteCount = it.length()
                outputStream.writeUTF(it.name)
                outputStream.writeLong(totalByteCount)

                FileInputStream(it).use {
                    it.copyToWithProgress(outputStream, this, totalByteCount)
                }
            } catch (e: SocketException) {
                // abort
            } finally {
                outputStream.close()
                socket.close()
            }
        }
    }

    private fun clientReceives(socketAddress: InetSocketAddress) = scope.launch(Dispatchers.IO) {
        val socket = Socket()
        socket.bind(null)
        socket.connect(socketAddress)

        val inputStream = DataInputStream(socket.getInputStream())
        val mapName = inputStream.readUTF()
        println("Recieving $mapName from client")
        val size = inputStream.readLong()
        println("Size: $size")
        var c = 0L
        var x = 0
        var percent = 0
        stateChannel.offer(Loading(0))
        val myOutput = object: OutputStream() {
            override fun write(b: Int) {
                x++
                val newPercent = (c++ * 100f / size).toInt()
                if (percent != newPercent) {
                    percent = newPercent
                    stateChannel.offer(Loading(percent))
                }
                if (x > DEFAULT_BUFFER_SIZE) {
                    println(percent)
                    x = 0
                }
            }
        }

        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytes = inputStream.read(buffer)
        while (bytes >= 0 && isActive) {
            myOutput.write(buffer, 0, bytes)
            try {
                bytes = inputStream.read(buffer)
            } catch (e:SocketException) {
                break
            }
        }

        inputStream.close()
        myOutput.close()
        socket.close()
    }

    private suspend fun resetWifiP2p() {
        val manager = manager ?: return
        val channel = channel ?: return

        /* We don't care about the success or failure of this call, this service is going to
         * shutdown anyway. */
        manager.cancelConnect(channel).also { println("Cancel connect $it") }
        manager.clearLocalServices(channel).also { println("ClearLocalServices $it") }
        manager.clearServiceRequests(channel).also { println("ClearServiceRequests $it") }
        manager.removeGroup(channel).also { println("Removegroup $it") }
        manager.stopPeerDiscovery(channel).also { println("Stop peer discovery $it") }
        peerListChannel.poll()

        wifiP2pState = Stopped
    }

    private fun InputStream.copyToWithProgress(outputStream: OutputStream, scope: CoroutineScope, totalByteCount: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytes = read(buffer)
        var bytesCopied: Long = 0
        var percent = 0
        stateChannel.offer(Loading(0))
        while (bytes >= 0 && scope.isActive) {
            outputStream.write(buffer, 0, bytes)
            try {
                bytes = read(buffer)
                bytesCopied += bytes
                val newPercent = (bytesCopied * 100f / totalByteCount).toInt()
                if (newPercent != percent) {
                    percent = newPercent
                    stateChannel.offer(Loading(percent))
                }
            } catch (e:SocketException) {
                break
            }
        }
    }
}

sealed class WifiP2pState : Comparable<WifiP2pState> {
    abstract val index: Int
    override fun compareTo(other: WifiP2pState): Int {
        if (this == other) return 0
        return if (index < other.index) -1 else 1
    }
}

object Started : WifiP2pState() {
    override val index: Int = 0
}

data class Loading(val progress: Int) : WifiP2pState() {
    override val index: Int = 2
}

object Stopping : WifiP2pState() {
    override val index: Int = 9
}

object Stopped : WifiP2pState() {
    override val index: Int = 10
}

private val TAG = WifiP2pService::class.java.name