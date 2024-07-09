package com.matteolobello.mapapers.activity

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Toast
import com.github.javiersantos.piracychecker.PiracyChecker
import com.github.javiersantos.piracychecker.PiracyCheckerUtils
import com.github.javiersantos.piracychecker.enums.PiracyCheckerCallback
import com.github.javiersantos.piracychecker.enums.PiracyCheckerError
import com.github.javiersantos.piracychecker.enums.PirateApp
import com.google.android.gms.maps.model.LatLng
import com.matteolobello.mapapers.R
import com.matteolobello.mapapers.activity.MapActivity.Companion.IM_FEELING_LUCKY_COORDINATES_EXTRA
import com.matteolobello.mapapers.activity.MapActivity.Companion.IM_FEELING_LUCKY_SHORTCUT
import com.matteolobello.mapapers.activity.MapActivity.Companion.MAP_STYLES_EXTRA
import com.matteolobello.mapapers.extension.setDarkStatusBarIcons
import com.matteolobello.mapapers.extension.setLightNavBar
import com.matteolobello.mapapers.extension.setNavBarColorWithFade
import com.matteolobello.mapapers.extension.setStatusBarColorWithFade
import com.matteolobello.mapapers.model.MapStyle
import khttp.get
import java.util.*
import kotlin.reflect.KFunction1

class LaunchScreenActivity : AppCompatActivity() {

    companion object {
        private val LICENSE_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkIjr12uj3BxLXzQeNLhQ/asKjGmgAb7YCK+fCRlkr7NgtHOkHAqvUvlz4qrCvHyXO2RmSZ1uk+m3deQaYT2axW6mx75IFrxGfGAdQYNO+SN/8MmIUri9QGCKTTdMiRH1RHbiiu7o3xlqcNwgmKCvAQ/ABzVxGLThn8RfaNL9W0S5dpAt7tJYW3Sd7sO+xVKOZV8PsDAAzg0AXU8NR51xoh/cUAAt6Og1S/FEhv1wMNZKKLvn7lf+BQ3SVXAhwYLZRXzZgDhQ6TQKU0C10e5rmO/7G4NQ08VzUgFFBLIqgG1zGX28p7vuNHmnLm5GyQeRqUdFV8oVTEeLasvKyTgU1wIDAQAB"
    }

    private var piracyChecker: PiracyChecker? = null

    private var hasLoadedStyles = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setLightNavBar(true)
        setDarkStatusBarIcons(true)
        setStatusBarColorWithFade(ContextCompat.getColor(this, R.color.light_system_bars_color))

        piracyChecker = PiracyChecker(this)
                .enableGooglePlayLicensing(LICENSE_KEY)
                .saveResultToSharedPreferences(PreferenceManager.getDefaultSharedPreferences(this), "valid_license")
                .callback(object : PiracyCheckerCallback() {
                    override fun dontAllow(piracyCheckerError: PiracyCheckerError, pirateApp: PirateApp?) {
                        Toast.makeText(this@LaunchScreenActivity, piracyCheckerError.name, Toast.LENGTH_LONG).show()
                        finish()
                    }

                    override fun allow() {
                        onPurchaseVerified()
                    }

                    override fun onError(error: PiracyCheckerError) {
                        Toast.makeText(this@LaunchScreenActivity, "Error: " + error.name, Toast.LENGTH_LONG).show()
                        onPurchaseVerified()
                    }
                })
                .enableSigningCertificate(PiracyCheckerUtils.getAPKSignature(this))
        piracyChecker!!.start()
    }

    override fun onDestroy() {
        piracyChecker!!.destroy()
        super.onDestroy()
    }

    private fun onPurchaseVerified() {
        val isOnline = isOnline()
        if (isOnline) {
            MapStylesFetcher(this::startMapActivity).execute()
        }

        Handler().postDelayed({
            if (hasLoadedStyles) {
                return@postDelayed
            }

            setLightNavBar(false)
            setNavBarColorWithFade(ContextCompat.getColor(this, R.color.snackbar_nav_bar_color))
            Snackbar.make(findViewById<View>(Window.ID_ANDROID_CONTENT), R.string.loading_error, Snackbar.LENGTH_INDEFINITE)
                    .setActionTextColor(ContextCompat.getColor(this, android.R.color.white))
                    .setAction(R.string.restart, {
                        finish()
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_out, android.R.anim.fade_out)
                    })
                    .show()
        }, if (isOnline) 5000L else 0)
    }

    private fun startMapActivity(stylesAndImFeelingLuckyCoordinates: Array<Any?>?) {
        hasLoadedStyles = true

        val mapStyles: ArrayList<MapStyle> = stylesAndImFeelingLuckyCoordinates!![0] as ArrayList<MapStyle>
        val imFeelingLuckyCoordinates = stylesAndImFeelingLuckyCoordinates[1] as ArrayList<LatLng>

        startActivity(Intent(applicationContext, MapActivity::class.java)
                .putExtra(MAP_STYLES_EXTRA, mapStyles)
                .putExtra(IM_FEELING_LUCKY_COORDINATES_EXTRA, imFeelingLuckyCoordinates)
                .putExtra(IM_FEELING_LUCKY_SHORTCUT, intent.getBooleanExtra(IM_FEELING_LUCKY_SHORTCUT, false)))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnectedOrConnecting
    }

    private class MapStylesFetcher(private var callback: KFunction1<@ParameterName(name = "stylesAndImFeelingLuckyCoordinates") Array<Any?>?, Unit>) : AsyncTask<Void, Void, Array<Any?>>() {

        override fun doInBackground(vararg aVoid: Void?): Array<Any?> {
            val resultArray = arrayOfNulls<Any>(2)

            resultArray[0] = fetchStyles()
            resultArray[1] = fetchImFeelingLuckyCoordinates()

            return resultArray
        }

        override fun onPostExecute(result: Array<Any?>?) {
            super.onPostExecute(result)

            callback.invoke(result)
        }

        private fun fetchStyles(): ArrayList<MapStyle> {
            val mapStyles = ArrayList<MapStyle>()

            val jsonObject = get("https://raw.githubusercontent.com/OhMyLob/MapsWallpaper-Data/master/all.json").jsonObject

            val stylesJsonArray = jsonObject.getJSONArray("styles")
            (0 until stylesJsonArray.length())
                    .map { stylesJsonArray.getJSONObject(it) }
                    .mapTo(mapStyles) {
                        MapStyle(it.getString("img"),
                                it.getString("src"),
                                Color.parseColor(it.getString("predominant_color")))
                    }

            return mapStyles
        }

        private fun fetchImFeelingLuckyCoordinates(): ArrayList<LatLng> {
            val imFeelingLuckyCoordinates = ArrayList<LatLng>()

            val jsonObject = get("https://raw.githubusercontent.com/OhMyLob/MapsWallpaper-Data/master/im_feeling_lucky.json").jsonObject
            val imFeelingLuckyCoordinatesJsonArray = jsonObject.getJSONArray("coord")
            (0 until imFeelingLuckyCoordinatesJsonArray.length())
                    .map { imFeelingLuckyCoordinatesJsonArray.getJSONObject(it) }
                    .mapTo(imFeelingLuckyCoordinates) {
                        LatLng(it.getDouble("lat"), it.getDouble("lon"))
                    }

            return imFeelingLuckyCoordinates
        }
    }
}