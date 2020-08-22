package com.farikamo.dumbutton

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.text.Charsets.UTF_8


class MainActivity : AppCompatActivity() {
    private lateinit var connectionsClient: ConnectionsClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectionsClient = Nearby.getConnectionsClient(this)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onStart() {
        super.onStart()
        if (!hasPermissions(this, *REQUIRED_PERMISSIONS)) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS)
        }
    }

    private fun hasPermissions(context: Context, vararg permissions: String): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // This always gets the full data of the payload. Will be null if it's not a BYTES
            // payload. You can check the payload type with payload.getType().
            setStatusText("received payload " + String(payload.asBytes()!!, UTF_8))
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
        }
    }

    private val endpointIds: MutableList<String> = mutableListOf()
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Automatically accept the connection on both sides.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            endpointIds.add(endpointId);
            setStatusText("onConnectionInitiated ${connectionInfo.endpointName}")
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    setStatusText("onConnectionResult ${result.status}")
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

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // An endpoint was found. We request a connection to it.
            connectionsClient
                .requestConnection(getUserNickname(), endpointId, connectionLifecycleCallback)
                .addOnSuccessListener { unused: Void? ->
                    // We successfully requested a connection. Now both sides
                    // must accept before the connection is established.
                }
                .addOnFailureListener { e: Exception ->
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
                getUserNickname(), packageName + getRoomName(), connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener { unused: Void? ->
                // We're advertising!
            }
            .addOnFailureListener { e: Exception ->
                // We were unable to start advertising.
            }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient
            .startDiscovery(packageName + getRoomName(), endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { unused: Void? ->
                // We're discovering!
            }
            .addOnFailureListener { e: Exception ->
                // We're unable to start discovering.
            }
    }

    private fun getUserNickname(): String {
        return txt_nickname.toString()
    }

    private fun getRoomName(): String {
        return txt_room_name.toString()
    }

    /** Finds an opponent to play the game with using Nearby Connections.  */
    fun findOpponent(view: View) {
        startAdvertising()
        startDiscovery()
        setStatusText("ZDAJ ISCEM")
        btn_advrt_discover.isEnabled = false
    }

    fun ready(view: View) {
        endpointIds.forEach { opponentEndpointId ->
            connectionsClient.sendPayload(
                opponentEndpointId, Payload.fromBytes("ready".toByteArray(UTF_8))
            )
        }
    }

    fun setStatusText(status: String) {
        txt_status.text = status
    }

    companion object {
        private const val REQUEST_CODE_REQUIRED_PERMISSIONS = 1
        private val STRATEGY = Strategy.P2P_STAR
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
