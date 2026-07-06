package com.gamekun.mergeblocks

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Handles GDPR consent, interstitial and rewarded ads.
 *
 * The unit IDs below are Google's official PUBLIC TEST IDs.
 * Replace them with your own AdMob unit IDs before publishing
 * (see README.md, step 4).
 */
class AdManager(private val activity: Activity) {

    companion object {
        private const val TAG = "AdManager"
        const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        const val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
        const val BANNER_ID = "ca-app-pub-3940256099942544/6300978111"

        /** Show an interstitial every N game-overs. */
        const val INTERSTITIAL_FREQUENCY = 3
    }

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var gameOverCount = 0
    private var adsInitialized = false

    /** Gathers UMP consent (required in EEA/UK), then starts the Mobile Ads SDK. */
    fun initialize(onReady: () -> Unit) {
        val consentInfo: ConsentInformation =
            UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        consentInfo.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                if (formError != null) {
                    Log.w(TAG, "Consent form error: ${formError.message}")
                }
                if (consentInfo.canRequestAds()) startAds(onReady)
            }
        }, { requestError ->
            Log.w(TAG, "Consent update error: ${requestError.message}")
            // Consent unavailable (e.g. offline) — SDK may still serve limited ads.
            if (consentInfo.canRequestAds()) startAds(onReady)
        })

        // Consent may already be granted from a previous session.
        if (consentInfo.canRequestAds()) startAds(onReady)
    }

    private fun startAds(onReady: () -> Unit) {
        if (adsInitialized) return
        adsInitialized = true
        MobileAds.initialize(activity) {
            loadInterstitial()
            loadRewarded()
            onReady()
        }
    }

    private fun loadInterstitial() {
        InterstitialAd.load(
            activity, INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial failed: ${error.message}")
                    interstitialAd = null
                }
            })
    }

    private fun loadRewarded() {
        RewardedAd.load(
            activity, REWARDED_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded failed: ${error.message}")
                    rewardedAd = null
                }
            })
    }

    /** Call on every game over; shows an interstitial every [INTERSTITIAL_FREQUENCY] times. */
    fun onGameOver() {
        gameOverCount++
        if (gameOverCount % INTERSTITIAL_FREQUENCY != 0) return
        val ad = interstitialAd ?: run { loadInterstitial(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                loadInterstitial()
            }
        }
        ad.show(activity)
    }

    val isRewardedReady: Boolean get() = rewardedAd != null

    /** Shows the rewarded ad; [onReward] runs only if the user earned the reward. */
    fun showRewarded(onReward: () -> Unit) {
        val ad = rewardedAd ?: run { loadRewarded(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                loadRewarded()
            }
        }
        ad.show(activity) { onReward() }
    }
}
