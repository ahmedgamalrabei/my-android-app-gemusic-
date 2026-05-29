package com.example

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"

    // Ad Unit IDs provided by the user
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-6143915770846381/8567137763"
    private const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-6143915770846381/4939414170"

    private var interstitialAd: InterstitialAd? = null
    private var appOpenAd: AppOpenAd? = null
    private var isAppOpenAdLoading = false
    private var isInterstitialAdLoading = false

    private fun isGmsAvailable(context: Context): Boolean {
        return try {
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(context)
            resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Initializes and loads the first batch of ads.
     */
    fun initializeAndLoad(context: Context) {
        if (!isGmsAvailable(context)) return
        loadAppOpenAd(context)
        loadInterstitial(context)
    }

    /**
     * Load an App Open Ad
     */
    fun loadAppOpenAd(context: Context) {
        if (!isGmsAvailable(context)) return
        if (appOpenAd != null || isAppOpenAdLoading) return

        isAppOpenAdLoading = true
        val request = AdRequest.Builder().build()
        try {
            AppOpenAd.load(
                context,
                APP_OPEN_AD_UNIT_ID,
                request,
                AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd = ad
                        isAppOpenAdLoading = false
                        Log.d(TAG, "App Open Ad Loaded successfully.")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        appOpenAd = null
                        isAppOpenAdLoading = false
                        Log.e(TAG, "App Open Ad failed to load: ${loadAdError.message}")
                    }
                }
            )
        } catch (e: Throwable) {
            isAppOpenAdLoading = false
            Log.e(TAG, "Exception loading App Open Ad", e)
        }
    }

    /**
     * Shows the App Open Ad if available
     */
    fun showAppOpenAdIfAvailable(activity: Activity, onAdDismissed: () -> Unit = {}) {
        val ad = appOpenAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    onAdDismissed()
                    // Pre-load the next App Open Ad
                    loadAppOpenAd(activity.applicationContext)
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    appOpenAd = null
                    onAdDismissed()
                    loadAppOpenAd(activity.applicationContext)
                }
            }
            try {
                ad.show(activity)
            } catch (e: Throwable) {
                appOpenAd = null
                onAdDismissed()
            }
        } else {
            onAdDismissed()
            loadAppOpenAd(activity.applicationContext)
        }
    }

    /**
     * Load an Interstitial Ad
     */
    fun loadInterstitial(context: Context) {
        if (!isGmsAvailable(context)) return
        if (interstitialAd != null || isInterstitialAdLoading) return

        isInterstitialAdLoading = true
        val request = AdRequest.Builder().build()
        try {
            InterstitialAd.load(
                context,
                INTERSTITIAL_AD_UNIT_ID,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isInterstitialAdLoading = false
                        Log.d(TAG, "Interstitial Ad Loaded successfully.")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        interstitialAd = null
                        isInterstitialAdLoading = false
                        Log.e(TAG, "Interstitial Ad failed to load: ${loadAdError.message}")
                    }
                }
            )
        } catch (e: Throwable) {
            isInterstitialAdLoading = false
            Log.e(TAG, "Exception loading Interstitial Ad", e)
        }
    }

    /**
     * Shows Interstitial Ad
     */
    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit = {}) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    onAdDismissed()
                    // Pre-load the next interstitial
                    loadInterstitial(activity.applicationContext)
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    interstitialAd = null
                    onAdDismissed()
                    loadInterstitial(activity.applicationContext)
                }
            }
            try {
                ad.show(activity)
            } catch (e: Throwable) {
                interstitialAd = null
                onAdDismissed()
            }
        } else {
            onAdDismissed()
            loadInterstitial(activity.applicationContext)
        }
    }
}
