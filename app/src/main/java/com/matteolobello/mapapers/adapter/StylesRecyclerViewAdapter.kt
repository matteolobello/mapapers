package com.matteolobello.mapapers.adapter;

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.matteolobello.mapapers.R
import com.matteolobello.mapapers.activity.MapActivity
import com.matteolobello.mapapers.model.MapStyle
import com.squareup.picasso.Picasso

class StylesRecyclerViewAdapter(val mapStyles: ArrayList<MapStyle>)
    : RecyclerView.Adapter<StylesRecyclerViewAdapter.ViewHolder>() {

    var previousSelectedStyleView: View? = null

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) = holder!!.bind()

    override fun getItemCount() = mapStyles.size

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val viewHolder = ViewHolder(LayoutInflater.from(parent!!.context)
                .inflate(R.layout.item_map_style, parent, false))
        viewHolder.setIsRecyclable(false)
        return viewHolder
    }

    fun markItemAsSelected(rootView: View, selected: Boolean) {
        rootView.findViewById<View>(R.id.mapStyleIndicatorCheck)
                .animate()
                .alpha(if (selected) 1.toFloat() else 0.toFloat())
                .start()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind() {
            setIsRecyclable(false)

            val mapStyle = mapStyles[adapterPosition]
            val mapStyleIndicatorImageView = itemView.findViewById<ImageView>(R.id.mapStyleIndicatorImageView)

            Picasso.with(itemView.context)
                    .load(mapStyle.imagePreviewUrl)
                    .into(mapStyleIndicatorImageView)

            itemView.setOnClickListener {
                val context = itemView.context
                if (context is MapActivity) {
                    context.applyMapStyle(mapStyle)

                    if (previousSelectedStyleView != null) {
                        markItemAsSelected(previousSelectedStyleView!!, false)
                    }

                    markItemAsSelected(itemView, true)

                    previousSelectedStyleView = itemView
                }
            }

            if (adapterPosition == 0) {
                itemView.callOnClick()
            }
        }
    }
}
