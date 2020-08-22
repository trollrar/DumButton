package com.farikamo.dumbutton

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.View
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import android.view.View.GONE
import android.view.View.VISIBLE
import kotlinx.android.synthetic.main.activity_button.*
import java.lang.Exception
import java.io.*


class ButtonActivity : AppCompatActivity() {
    private lateinit var connectionsClient: ConnectionsClient

    private lateinit var code: String
    private lateinit var name: String
    private lateinit var mode: Mode
    private var players: MutableMap<String, Player> = mutableMapOf()
    private var round = 1
    private var hostId: String? = null
    private var currentTimestamp: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_button)

        code = intent.getStringExtra("code")!!
        name = intent.getStringExtra("name")!!
        mode = Mode.valueOf(intent.getStringExtra("mode")!!)

        setConnecting(true)

        connectionsClient = Nearby.getConnectionsClient(this)

        txt_code.text = "Room name: " + code
        txt_ready.text = "Nickname: " + name
        txt_status.text = "Waiting for players to join"

        when (mode) {
            Mode.HOST -> {
                setConnecting(false)
                players["HOST"] = Player(name) //TODO: set hosts original id
                startAdvertising()
            }
            Mode.JOIN -> startDiscovery()
        }
    }

    private fun setConnecting(connecting: Boolean) {
        if (connecting) {
            txt_code.visibility = GONE
            txt_ready.visibility = GONE
            txt_status.text = "Connecting..."
            btn_main.visibility = GONE
            bar_progress.visibility = VISIBLE
        } else {
            txt_code.visibility = VISIBLE
            txt_ready.visibility = VISIBLE
            txt_status.text = ""
            btn_main.visibility = VISIBLE
            bar_progress.visibility = GONE
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val deSerialized = deserialize(payload.asBytes()!!)
            when (deSerialized!!.type) {
                PackageType.PLAYERS -> {
                    players = deSerialized.value as MutableMap<String, Player>
                    updatePlayers()
                }
                PackageType.READY -> {
                    players[endpointId]!!.ready = !players[endpointId]!!.ready
                    var everyoneReady = true
                    players.values.forEach {player ->
                        if (!player.ready) {
                            everyoneReady = false
                            return
                        }
                    }
                    if (everyoneReady) {
                        sendTimestamp()
                    } else {
                        sendPlayers()
                        updatePlayers()
                    }
                }
                PackageType.TIMER -> {
                    val timestamp = deSerialized.value as Long
                    val delay: Long = timestamp!! - System.currentTimeMillis()
                    val handler = Handler()
                    handler.postDelayed({
                        showButton()
                    }, delay)
                    startCounting()
                    currentTimestamp = timestamp

                }
                PackageType.SCORE -> {
                    // Update player scores and then send back players so other clients can evaluate their score
                    val score = deSerialized.value as Long
                    players[endpointId]!!.scores[round] = score

                    var everyoneClicked = true
                    players.values.forEach {player ->
                        if (player.scores[round] == null) {
                            everyoneClicked = false
                            return
                        }
                    }
                    if (everyoneClicked) {
                        players.values.forEach {player ->
                            player.ready = false
                        }
                        sendPlayers()
                        updatePlayers()
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
        }
    }

    private fun showButton() {
        btn_press.visibility = VISIBLE
    }

    private fun sendTimestamp() {
        val timestamp: Long = System.currentTimeMillis() + (1000..5000).random()
        connectionsClient.sendPayload(hostId!!, Payload.fromBytes(serialize(BytePackage(PackageType.TIMER, timestamp))!!))
        val delay: Long = timestamp - System.currentTimeMillis()
        val handler = Handler()
        handler.postDelayed({
            showButton()
        }, delay)
        startCounting()
        currentTimestamp = timestamp
    }

    private fun startCounting() {
        btn_main.visibility = GONE
        txt_status.text = "Button will show up, be quick!"
    }

    @SuppressLint("SetTextI18n")
    private fun updatePlayers() {
        btn_main.visibility = VISIBLE
        txt_status.text = ""
        players.forEach { player ->
            txt_status.text = txt_status.text.toString() + player.value.name + " is " + (if (player.value.ready)  "ready" else "not ready") + "\n"
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Automatically accept the connection on both sides.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            if (mode == Mode.HOST) {
                var player = Player(connectionInfo.endpointName)
                players[endpointId] = player
            } else {
                hostId = endpointId
                setConnecting(false)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    if (mode == Mode.HOST) {
                        sendPlayers()
                        updatePlayers()
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                }
            }// We're connected! Can now start sending and receiving data.
            // The connection was rejected by one or both sides.
            // The connection broke before it was able to be accepted.
            // Unknown status code
        }

        override fun onDisconnected(endpointId: String) {
            // We've been disconnected from this endpoint. No more data can be
            // sent or received.
        }
    }

    private fun sendPlayers() {
        players.keys.forEach {playerId ->
            connectionsClient.sendPayload(playerId, Payload.fromBytes(serialize(BytePackage(PackageType.PLAYERS, players as Serializable))!!))
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // An endpoint was found. We request a connection to it.
            connectionsClient
                .requestConnection(name, endpointId, connectionLifecycleCallback)
                .addOnSuccessListener { unused ->
                    // We successfully requested a connection. Now both sides
                    // must accept before the connection is established.
                }
                .addOnFailureListener { e ->
                    // Nearby Connections failed to request the connection.
                }
        }

        override fun onEndpointLost(endpointId: String) {
            // A previously discovered endpoint has gone away.
        }
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient
            .startAdvertising(
                name, packageName + "." + code.toLowerCase(), connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener { unused ->
                // We're advertising!
            }
            .addOnFailureListener { e ->
                // We were unable to start advertising.
            }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient
            .startDiscovery(packageName + "." + code.toLowerCase(), endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { unused ->
                // We're discovering!
            }
            .addOnFailureListener { e ->
                // We're unable to start discovering.
            }
    }

    private fun serialize(obj: BytePackage): ByteArray? {
        val bos = ByteArrayOutputStream()
        var out: ObjectOutputStream? = null
        var toReturn: ByteArray? = null
        try {
            out = ObjectOutputStream(bos)
            out.writeObject(obj)
            out.flush()
            toReturn = bos.toByteArray()
        } finally {
            try {
                bos.close();
            } catch (ex: Exception) {
                // ignore close exception
            }
        }
        return toReturn
    }

    private fun deserialize(bytes: ByteArray): BytePackage? {
        var e: BytePackage? = null
        return try {
            val bytesIn = ByteArrayInputStream(bytes)
            val inp = ObjectInputStream(bytesIn)
            e = inp.readObject() as BytePackage //Any!
            inp.close()
            bytesIn.close()
            e
        } catch (i: IOException) {
            i.printStackTrace()
            null
        } catch (c: ClassNotFoundException) {
            c.printStackTrace()
            null
        }

    }

    fun mainButton(view: View) {
        txt_ready.text = "Nickname: " + name
        when (mode) {
            Mode.HOST -> {
                players["HOST"]!!.ready = !players["HOST"]!!.ready
                sendPlayers()
                updatePlayers()
            }
            Mode.JOIN -> connectionsClient.sendPayload(hostId!!, Payload.fromBytes(serialize(BytePackage(PackageType.READY, null))!!))
        }
    }

    fun pressButton(view: View) {
        btn_press.visibility = GONE
        val score = System.currentTimeMillis() - currentTimestamp!!
        when (mode) {
            Mode.HOST -> {
                players["HOST"]!!.scores[round] = score
            }
            Mode.JOIN -> {
                connectionsClient.sendPayload(hostId!!, Payload.fromBytes(serialize(BytePackage(PackageType.SCORE, score))!!))
            }
        }
        setScoreView(score)
    }

    private fun setScoreView(score: Long) {
        txt_status.text = "Waiting for other players..."
        txt_ready.text = "Your score: " + score + " millis"
    }

    data class Player(var name: String): Serializable {
        var ready: Boolean = false
        var scores: MutableMap<Int, Long> = mutableMapOf()
    }

    data class BytePackage(var type: PackageType, var value: Serializable?): Serializable

    enum class Mode {
        HOST, JOIN
    }

    enum class PackageType {
        PLAYERS, READY, TIMER, SCORE
    }

    companion object {
        private val STRATEGY = Strategy.P2P_STAR
    }
}
