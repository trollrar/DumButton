package com.farikamo.dumbutton

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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

    private var hostId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_button)

        code = intent.getStringExtra("code")!!
        name = intent.getStringExtra("name")!!
        mode = Mode.valueOf(intent.getStringExtra("mode")!!)

        setConnecting(true)

        connectionsClient = Nearby.getConnectionsClient(this)

        when (mode) {
            Mode.HOST -> {
                setConnecting(false)
                players["HOST"] = Player(name)
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
                "players" -> {
                    players = deSerialized.value as MutableMap<String, Player>
                    updatePlayers()
                }
                "ready" -> {
                    players[endpointId]!!.ready = !players[endpointId]!!.ready
                    updatePlayers()
                    sendPlayers()
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
        }
    }

    private fun updatePlayers() {
        txt_status.text = ""
        players.forEach { player ->
            txt_status.text = txt_status.text.toString() + player.value.name + " is " + player.value.ready + "\n"
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
            connectionsClient.sendPayload(playerId, Payload.fromBytes(serialize(BytePackage("players", players as Serializable))!!))
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
                name, packageName, connectionLifecycleCallback, advertisingOptions
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
            .startDiscovery(packageName, endpointDiscoveryCallback, discoveryOptions)
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
        when (mode) {
            Mode.HOST -> {
                players["HOST"]!!.ready = !players["HOST"]!!.ready
                sendPlayers()
            }
            Mode.JOIN -> connectionsClient.sendPayload(hostId!!, Payload.fromBytes(serialize(BytePackage("ready", null))!!))
        }
    }

    data class Player(var name: String): Serializable {
        var ready: Boolean = false
        var scores: MutableMap<Int, Long> = mutableMapOf()
    }

    data class BytePackage(var type: String, var value: Serializable?): Serializable

    enum class Mode {
        HOST, JOIN
    }

    companion object {
        private val STRATEGY = Strategy.P2P_STAR
    }
}
