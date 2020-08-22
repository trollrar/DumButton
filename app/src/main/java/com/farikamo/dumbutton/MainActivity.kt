package com.farikamo.dumbutton

import android.Manifest
import android.annotation.SuppressLint
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
import kotlinx.android.synthetic.main.activity_button.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.text.Charsets.UTF_8
import android.content.Intent




class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onStart() {
        super.onStart()
        if (!hasPermissions(this, *REQUIRED_PERMISSIONS)) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        btn_advrt.isEnabled = true
        btn_discover.isEnabled = true
    }

    private fun hasPermissions(context: Context, vararg permissions: String): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun discover(view: View) {
        startDiscovery()
        btn_advrt.isEnabled = false
        btn_discover.isEnabled = false
    }

    private fun startDiscovery() {
        val myIntent = Intent(this, ButtonActivity::class.java)
        myIntent.putExtra("code", txt_room_name.text.toString())
        myIntent.putExtra("name", txt_nickname.text.toString())
        myIntent.putExtra("mode", "JOIN")
        startActivity(myIntent)
    }

    fun advertise(view: View) {
        startAdvertising()
        btn_advrt.isEnabled = false
        btn_discover.isEnabled = false
    }

    private fun startAdvertising() {
        val myIntent = Intent(this, ButtonActivity::class.java)
        myIntent.putExtra("code", txt_room_name.text.toString())
        myIntent.putExtra("name", txt_nickname.text.toString())
        myIntent.putExtra("mode", "HOST")
        startActivity(myIntent)
    }

    companion object {
        private const val REQUEST_CODE_REQUIRED_PERMISSIONS = 1
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
