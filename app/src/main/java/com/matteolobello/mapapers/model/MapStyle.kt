package com.matteolobello.mapapers.model

import android.annotation.SuppressLint
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@SuppressLint("ParcelCreator")
@Parcelize
data class MapStyle(val imagePreviewUrl: String?, val jsonUrl: String?, val mainColor: Int?) : Parcelable