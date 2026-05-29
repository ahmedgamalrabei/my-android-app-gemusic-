package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainScreen
import com.example.ui.viewmodel.MusicViewModel
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isGmsAvailable = try {
            val availability = GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(this)
            resultCode == ConnectionResult.SUCCESS
        } catch (e: Throwable) {
            false
        }

        if (isGmsAvailable) {
            try {
                MobileAds.initialize(this) {
                    try {
                        AdManager.initializeAndLoad(applicationContext)
                        runOnUiThread {
                            try {
                                // Show App Open Ad as soon as it is available on launch
                                AdManager.showAppOpenAdIfAvailable(this)
                            } catch (e: Throwable) {
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        setContent {
            val viewModel: MusicViewModel = viewModel()
            MainScreen(viewModel = viewModel)
        }
    }
}
