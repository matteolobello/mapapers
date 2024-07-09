package com.matteolobello.mapapers.activity

import android.Manifest
import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.support.design.widget.Snackbar
import android.support.graphics.drawable.ArgbEvaluator
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.view.animation.Animation
import android.widget.Toast
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.location.places.ui.PlaceAutocomplete
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.matteolobello.mapapers.R
import com.matteolobello.mapapers.adapter.StylesRecyclerViewAdapter
import com.matteolobello.mapapers.extension.*
import com.matteolobello.mapapers.model.MapStyle
import com.matteolobello.mapapers.view.ItemOffsetDecoration
import com.soundcloud.android.crop.Crop
import khttp.get
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.bottom_panel.*
import kotlinx.android.synthetic.main.reveal_layout.*
import kotlinx.android.synthetic.main.search_bar.*
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback, ColorPickerDialogListener {

    companion object {
        const val MAP_STYLES_EXTRA = "com.matteolobello.mapapers.activity.MAP_STYLES_EXTRA"
        const val IM_FEELING_LUCKY_COORDINATES_EXTRA = "com.matteolobello.mapapers.activity.IM_FEELING_LUCKY_COORDINATES_EXTRA"
        const val IM_FEELING_LUCKY_SHORTCUT = "com.matteolobello.mapapers.activity.IM_FEELING_LUCKY_COORDINATES_SHORTCUT"

        const val COLOR_PICKER_DIALOG_ID = 122

        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 505
        private const val PERMISSIONS_REQUEST_CODE = 131
    }

    private var stylesRecyclerViewAdapter: StylesRecyclerViewAdapter? = null

    private var googleMap: GoogleMap? = null

    private var currentStyleJsonArray: JSONArray? = null

    private var imFeelingLuckyCoordinates: ArrayList<LatLng>? = null

    private var latestColorPickerCallback: ColorPickerDialogListener? = null

    private var latestOutputPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())

        supportActionBar?.hide()

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.light_system_bars_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.colorPrimaryDark)

            stylesTextView.letterSpacing = 0.15f
            customizeTextView.letterSpacing = 0.15f

            swipeRefreshLayout.setProgressViewOffset(false,
                    getStatusBarHeight() + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics).toInt(),
                    getStatusBarHeight() + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics).toInt())
        }

        val mapStyles = intent.extras.getParcelableArrayList<MapStyle>(MAP_STYLES_EXTRA)
        attachMapStyles(mapStyles)

        imFeelingLuckyCoordinates = intent.extras.getParcelableArrayList<LatLng>(IM_FEELING_LUCKY_COORDINATES_EXTRA)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        swipeRefreshLayout.isEnabled = false

        bottomPanelNestedScrollView.isNestedScrollingEnabled = false
        styleSelectorRecyclerView.isNestedScrollingEnabled = false

        imFeelingLuckyButton.setOnClickListener {
            Collections.shuffle(imFeelingLuckyCoordinates)

            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    imFeelingLuckyCoordinates!![0], 14.0f))

            swipeRefreshLayout.isRefreshing = false
        }

        searchBarLayout.setOnClickListener {
            startSearchPlaceActivity()
        }

        waterColorImageView.setOnClickListener {
            startColorPicker(object : ColorPickerDialogListener {
                override fun onDialogDismissed(dialogId: Int) {
                }

                override fun onColorSelected(dialogId: Int, color: Int) {
                    val newColor = String.format("#%06X", 0xFFFFFF and color)

                    currentStyleJsonArray = injectNewColorInMapStyle(currentStyleJsonArray, "water", newColor)

                    applyMapStyle(MapStyle(null, null, fetchMainColorFromMapStyle(currentStyleJsonArray!!)), currentStyleJsonArray.toString())
                    stylesRecyclerViewAdapter?.markItemAsSelected(stylesRecyclerViewAdapter?.previousSelectedStyleView!!, false)
                }
            })
        }

        landColorImageView.setOnClickListener {
            startColorPicker(object : ColorPickerDialogListener {
                override fun onDialogDismissed(dialogId: Int) {
                }

                override fun onColorSelected(dialogId: Int, color: Int) {
                    val newColor = String.format("#%06X", 0xFFFFFF and color)

                    currentStyleJsonArray = injectNewColorInMapStyle(currentStyleJsonArray, "landscape.natural", newColor)
                    currentStyleJsonArray = injectNewColorInMapStyle(currentStyleJsonArray, "poi", newColor)

                    applyMapStyle(MapStyle(null, null, fetchMainColorFromMapStyle(currentStyleJsonArray!!)), currentStyleJsonArray.toString())
                    stylesRecyclerViewAdapter?.markItemAsSelected(stylesRecyclerViewAdapter?.previousSelectedStyleView!!, false)
                }
            })
        }

        cityColorImageView.setOnClickListener {
            startColorPicker(object : ColorPickerDialogListener {
                override fun onDialogDismissed(dialogId: Int) {
                }

                override fun onColorSelected(dialogId: Int, color: Int) {
                    val newColor = String.format("#%06X", 0xFFFFFF and color)

                    currentStyleJsonArray = injectNewColorInMapStyle(currentStyleJsonArray, "landscape.man_made", newColor)

                    applyMapStyle(MapStyle(null, null, fetchMainColorFromMapStyle(currentStyleJsonArray!!)), currentStyleJsonArray.toString())
                    stylesRecyclerViewAdapter?.markItemAsSelected(stylesRecyclerViewAdapter?.previousSelectedStyleView!!, false)
                }
            })
        }

        roadsColorImageView.setOnClickListener {
            startColorPicker(object : ColorPickerDialogListener {
                override fun onDialogDismissed(dialogId: Int) {
                }

                override fun onColorSelected(dialogId: Int, color: Int) {
                    val newColor = String.format("#%06X", 0xFFFFFF and color)

                    currentStyleJsonArray = injectNewColorInMapStyle(currentStyleJsonArray, "road", newColor)

                    applyMapStyle(MapStyle(null, null, fetchMainColorFromMapStyle(currentStyleJsonArray!!)), currentStyleJsonArray.toString())
                    stylesRecyclerViewAdapter?.markItemAsSelected(stylesRecyclerViewAdapter?.previousSelectedStyleView!!, false)
                }
            })
        }

        saveButton.setOnClickListener {
            startSaveProcess()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onBackPressed() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                val place = PlaceAutocomplete.getPlace(this, data)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(place.latLng, 14.0f))
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                val status = PlaceAutocomplete.getStatus(this, data)
                Toast.makeText(applicationContext, status.statusMessage, Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == Crop.REQUEST_CROP) {
            if (data != null) {
                val outputUri = Crop.getOutput(data)
                if (outputUri != null) {
                    WallpaperApplyAsyncTask(WeakReference(this), outputUri.path).execute()
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        this.googleMap = googleMap

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            googleMap?.setPadding(0, getStatusBarHeight(), 0, 0)
        }

        if (styleSelectorRecyclerView.adapter != null) {
            viewSwitcher.showNext()
        }

        val calledFromImFeelingLuckyShortcut = intent.getBooleanExtra(IM_FEELING_LUCKY_SHORTCUT, false)
        if (calledFromImFeelingLuckyShortcut) {
            imFeelingLuckyButton.callOnClick()
        }
    }

    override fun onDialogDismissed(dialogId: Int) {
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        latestColorPickerCallback?.onColorSelected(dialogId, color)
    }

    @SuppressLint("all")
    fun applyMapStyle(mapStyle: MapStyle, jsonString: String? = null) {
        swipeRefreshLayout.isRefreshing = true

        Thread({
            val json = jsonString ?: get(mapStyle.jsonUrl!!).text

            currentStyleJsonArray = JSONArray(json)

            runOnUiThread {
                googleMap!!.setMapStyle(MapStyleOptions(json))
                swipeRefreshLayout.isRefreshing = false

                onNewStyleApplied()
            }
        }).start()

        val colorFrom = (saveButton.background as ColorDrawable).color
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, mapStyle.mainColor)
        colorAnimation.duration = 250
        colorAnimation.addUpdateListener { animator ->
            run {
                val color = animator.animatedValue as Int
                saveButton.background = ColorDrawable(color)

                saveButton.setTextColor(ContextCompat.getColor(this@MapActivity,
                        if (color.isLight()) android.R.color.black else android.R.color.white))
            }
        }
        colorAnimation.start()

        setNavBarColorWithFade(mapStyle.mainColor!!.manipulateColor(0.8.toFloat()))
    }

    private fun startSearchPlaceActivity() {
        try {
            val intent = PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN)
                    .build(this)
            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE)
        } catch (e: GooglePlayServicesRepairableException) {
        } catch (e: GooglePlayServicesNotAvailableException) {
        }
    }

    private fun attachMapStyles(mapStyles: ArrayList<MapStyle>) {
        stylesRecyclerViewAdapter = StylesRecyclerViewAdapter(mapStyles)

        styleSelectorRecyclerView.layoutManager = GridLayoutManager(applicationContext, calculateNoOfColumns())
        styleSelectorRecyclerView.addItemDecoration(ItemOffsetDecoration(applicationContext, R.dimen.map_styles_offset))
        styleSelectorRecyclerView.adapter = stylesRecyclerViewAdapter

        bottomPanelNestedScrollView.fullScroll(View.FOCUS_UP)

        if (googleMap != null) {
            viewSwitcher.showNext()
        }
    }

    fun calculateNoOfColumns(): Int {
        val displayMetrics = applicationContext.resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        return (dpWidth / 72).toInt()
    }

    private fun injectNewColorInMapStyle(styleJsonArray: JSONArray?, key: String, newColor: String): JSONArray? {
        (0..(styleJsonArray!!.length() - 1))
                .map { styleJsonArray.getJSONObject(it) }
                .forEach {
                    val stylersJsonObject = it.getJSONArray("stylers").getJSONObject(0)
                    if (stylersJsonObject.has("color")) {
                        when (it.getString("featureType")) {
                            key -> {
                                stylersJsonObject.put("color", newColor)
                            }
                        }
                    }
                }

        return styleJsonArray
    }

    private fun onNewStyleApplied() {
        if (currentStyleJsonArray == null) {
            return
        }

        (0..(currentStyleJsonArray!!.length() - 1))
                .map { currentStyleJsonArray!!.getJSONObject(it) }
                .forEach {
                    val stylersJsonObject = it.getJSONArray("stylers").getJSONObject(0)
                    if (stylersJsonObject.has("color")) {
                        val color = Color.parseColor(stylersJsonObject.getString("color"))

                        when (it.getString("featureType")) {
                            "water" -> {
                                waterColorImageView.setImageDrawable(ColorDrawable(color))
                            }
                            "landscape.natural" -> {
                                landColorImageView.setImageDrawable(ColorDrawable(color))
                            }
                            "landscape.man_made" -> {
                                cityColorImageView.setImageDrawable(ColorDrawable(color))
                            }
                            "road" -> {
                                roadsColorImageView.setImageDrawable(ColorDrawable(color))
                            }
                        }
                    }
                }
    }

    private fun fetchMainColorFromMapStyle(jsonArray: JSONArray): Int {
        (0..(jsonArray.length() - 1))
                .map { jsonArray.getJSONObject(it) }
                .forEach {
                    val stylersJsonObject = it.getJSONArray("stylers").getJSONObject(0)
                    if (stylersJsonObject.has("color")) {
                        when (it.getString("featureType")) {
                            "landscape.natural" -> {
                                return Color.parseColor(stylersJsonObject.getString("color"))
                            }
                        }
                    }
                }

        return Color.BLACK
    }

    private fun startColorPicker(colorPickerCallback: ColorPickerDialogListener) {
        latestColorPickerCallback = colorPickerCallback

        ColorPickerDialog.newBuilder()
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setAllowPresets(false)
                .setDialogId(COLOR_PICKER_DIALOG_ID)
                .setShowAlphaSlider(false)
                .show(this)
    }

    private fun startSaveProcess() {
        if (ActivityCompat.checkSelfPermission(this@MapActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MapActivity, Array(1) { Manifest.permission.WRITE_EXTERNAL_STORAGE }, PERMISSIONS_REQUEST_CODE)
            return
        }

        setDarkStatusBarIcons(true)
        setLightNavBar(true)

        val xy = IntArray(2)
        saveButton.getLocationInWindow(xy)

        val centerXOfButtonOnScreen = xy[0] + resources.displayMetrics.widthPixels / 2
        val centerYOfButtonOnScreen = xy[1] + saveButton.height / 2

        revealLayout.visibility = View.VISIBLE
        revealLayout.show(centerXOfButtonOnScreen, centerYOfButtonOnScreen, 500, object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {
            }

            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
                viewSwitcher.visibility = View.GONE

                revealTargetLayout.animate()
                        .alpha(1.0f)
                        .setDuration(300)
                        .setListener(null)
                        .start()

                googleMap?.setOnMapLoadedCallback {
                    screenshotMapView()

                    Handler().postDelayed({
                        viewSwitcher.visibility = View.VISIBLE

                        Handler().postDelayed({
                            revealTargetLayout.animate()
                                    .alpha(0.0f)
                                    .setDuration(300)
                                    .setListener(object : Animator.AnimatorListener {
                                        override fun onAnimationRepeat(animator: Animator?) {
                                        }

                                        override fun onAnimationEnd(animator: Animator?, isReverse: Boolean) {
                                            revealLayout.hide(centerXOfButtonOnScreen, centerYOfButtonOnScreen, 500, object : Animation.AnimationListener {
                                                override fun onAnimationRepeat(animation: Animation?) {
                                                }

                                                override fun onAnimationStart(animation: Animation?) {
                                                    setDarkStatusBarIcons(false)
                                                    setLightNavBar(false)
                                                    setNavBarColorWithFade((saveButton.background as ColorDrawable).color.manipulateColor(0.8.toFloat()))
                                                }

                                                override fun onAnimationEnd(animation: Animation?) {
                                                    revealLayout.visibility = View.INVISIBLE

                                                    if (latestOutputPath != null) {
                                                        val snackBar = Snackbar.make(findViewById<View>(Window.ID_ANDROID_CONTENT), R.string.done, Snackbar.LENGTH_LONG)
                                                        snackBar.setAction(R.string.set, {
                                                            snackBar.dismiss()

                                                            Crop.of(Uri.fromFile(File(latestOutputPath)),
                                                                    Uri.fromFile(File(latestOutputPath + "_cropped.jpg"))).start(this@MapActivity)
                                                        })
                                                        snackBar.setActionTextColor(Color.WHITE)
                                                        snackBar.addCallback(object : Snackbar.Callback() {
                                                            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                                                                super.onDismissed(transientBottomBar, event)

                                                                setNavBarColorWithFade((saveButton.background as ColorDrawable).color.manipulateColor(0.8.toFloat()))
                                                            }
                                                        })

                                                        setNavBarColorWithFade(ContextCompat.getColor(this@MapActivity, R.color.snackbar_nav_bar_color))
                                                        snackBar.show()
                                                    }
                                                }
                                            })
                                        }

                                        override fun onAnimationEnd(animator: Animator?) {
                                            onAnimationEnd(null, false)
                                        }

                                        override fun onAnimationCancel(animator: Animator?) {
                                        }

                                        override fun onAnimationStart(animator: Animator?) {
                                        }
                                    })
                                    .start()
                        }, 800)
                    }, 800)
                }
            }
        })
    }

    private fun screenshotMapView() {
        googleMap!!.snapshot { bitmap ->
            run {
                val now = System.currentTimeMillis()

                try {
                    val outputFolder = Environment.getExternalStorageDirectory().toString() + "/" + getString(R.string.app_name)
                    val folderFile = File(outputFolder)
                    if (!folderFile.exists()) {
                        folderFile.mkdir()
                    }

                    latestOutputPath = "$outputFolder/$now.jpg"

                    val imageFile = File(latestOutputPath)
                    val outputStream = FileOutputStream(imageFile)
                    val quality = 100

                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

                    outputStream.flush()
                    outputStream.close()

                    val contentValues = ContentValues()
                    contentValues.put(MediaStore.Images.Media.TITLE, "" + now)
                    contentValues.put(MediaStore.Images.Media.DESCRIPTION, getString(R.string.made_with_mapapers))
                    contentValues.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                    contentValues.put(MediaStore.Images.Media.BUCKET_ID, imageFile.toString().toLowerCase().hashCode())
                    contentValues.put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, imageFile.name.toLowerCase())
                    contentValues.put("_data", imageFile.absolutePath)

                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                } catch (e: Throwable) {
                    e.printStackTrace()

                    Toast.makeText(this, "Error: " + e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    class WallpaperApplyAsyncTask(private val weakContext: WeakReference<Context>, private val wallpaperPath: String) : AsyncTask<String, Void, Void>() {

        override fun doInBackground(vararg wallpaperPath: String?): Void? {
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            WallpaperManager.getInstance(weakContext.get())
                    .setBitmap(BitmapFactory.decodeFile(this.wallpaperPath, options))

            return null
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)

            Toast.makeText(weakContext.get(), R.string.done, Toast.LENGTH_SHORT).show()
        }
    }
}
