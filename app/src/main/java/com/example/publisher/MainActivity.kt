package com.example.publisher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var etStudentID: TextView
    private lateinit var permissions: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val check = 1
    private var client: Mqtt5BlockingClient? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        permissions = findViewById(R.id.permissions)
        permissions.visibility = View.GONE

        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)
        etStudentID = findViewById(R.id.etStudentID)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission()
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        startBtn.setOnClickListener {
            val studentID: Int = etStudentID.text.toString().toIntOrNull() ?: 0
            if (checkID(studentID)) {
                Toast.makeText(this, "Valid ID", Toast.LENGTH_SHORT).show()
                connectAndSend()
            } else {
                etStudentID.text = ""
                Toast.makeText(this, "Invalid ID", Toast.LENGTH_SHORT).show()
            }
        }

        stopBtn.setOnClickListener {
            client?.disconnect()
            Toast.makeText(this, "Disconnected from broker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkID(studentID: Int): Boolean {
        return studentID in 816000000..816999999
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission()
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude
                    val message = """
                        {
                            "latitude": $lat,
                            "longitude": $lng,
                            "studentID": "${etStudentID.text}"
                        }
                    """.trimIndent()
                    sendMessage(message)
                    Log.d("MainActivity", "Lat: $lat, Lng: $lng")
                } else {
                    Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendMessage(message: String){
        try{
            client?.publishWith()?.topic("locationTracker")?.payload(message.toByteArray())?.send()
            Log.i("MainActivity", "Publishing")
        } catch (e:Exception){
            Toast.makeText(this,"An error occurred when sending a message to the broker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            check
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permission: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permission, grantResults)
        if (requestCode == check) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                permissions.visibility = View.GONE
                //getLocation()
            } else {
                Toast.makeText(this, "Enable location permission in settings", Toast.LENGTH_LONG).show()
                permissions.visibility = View.VISIBLE
            }
        }
    }

    private fun connectAndSend() {
        thread {
            try {
                client = Mqtt5Client.builder()
                    .identifier(UUID.randomUUID().toString())
                    .serverHost("broker-816038265.sundaebytestt.com")
                    .serverPort(1883)
                    .build()
                    .toBlocking()
                client?.connect()

                Log.i("MainActivity", "Client Connected, now getting location")

                runOnUiThread {
                    getLocation()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error connecting to broker", e)
                runOnUiThread {
                    Toast.makeText(this, "Error connecting to broker", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.disconnect()
    }
}