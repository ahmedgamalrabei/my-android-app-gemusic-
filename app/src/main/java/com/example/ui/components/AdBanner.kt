package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(8.dp)
            .testTag("admob_banner_container"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Aesthetic label to make it integrate elegantly in the Arabic app context
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "إعلان ترويجي مغذى",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            )
            Text(
                text = "راعي التطبيق الرائع",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontSize = 9.sp
                )
            )
        }

        // Host the Google Play Services AdView inside the Compose hierarchy
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            factory = { context ->
                val isGmsAvailable = try {
                    val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                    val resultCode = availability.isGooglePlayServicesAvailable(context)
                    resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS
                } catch (e: Throwable) {
                    false
                }

                if (isGmsAvailable) {
                    try {
                        AdView(context).apply {
                            // Production AdMob Banner ID
                            setAdSize(AdSize.BANNER)
                            adUnitId = "ca-app-pub-6143915770846381/2025780147"
                            adListener = object : com.google.android.gms.ads.AdListener() {
                                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                                    super.onAdFailedToLoad(error)
                                    // Log or handle failed ad loading gracefully
                                }
                            }
                            loadAd(AdRequest.Builder().build())
                        }
                    } catch (e: Throwable) {
                        // Fallback component to prevent emulator or environment crash
                        createFallbackTextView(context)
                    }
                } else {
                    createFallbackTextView(context)
                }
            },
            update = { adView ->
                // Reload or refresh state if required
            }
        )
    }
}

private fun createFallbackTextView(context: android.content.Context): android.widget.TextView {
    return android.widget.TextView(context).apply {
        text = "إعلان ترويجي مغذى (Google Mobile Ads)"
        gravity = android.view.Gravity.CENTER
        setTextColor(android.graphics.Color.parseColor("#808080")) // Gray of MaterialTheme
        textSize = 11f
    }
}
