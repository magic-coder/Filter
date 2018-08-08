package com.github.gnastnosaj.filter.kaleidoscope.ui.view

import android.content.Context
import android.view.ViewManager
import com.facebook.drawee.view.SimpleDraweeView
import org.jetbrains.anko.custom.ankoView


class RatioImageView(context: Context?) : SimpleDraweeView(context) {

    private var originalWidth: Int = 0
    private var originalHeight: Int = 0

    fun setOriginalSize(originalWidth: Int, originalHeight: Int) {
        this.originalWidth = originalWidth
        this.originalHeight = originalHeight

        val layoutParams = layoutParams
        if (layoutParams != null) {
            val ratio = originalWidth.toFloat() / originalHeight.toFloat()

            val width = layoutParams.width

            if (width > 0) {
                layoutParams.height = (width.toFloat() / ratio).toInt()
            }

            setLayoutParams(layoutParams)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (originalWidth > 0 && originalHeight > 0) {
            val ratio = originalWidth.toFloat() / originalHeight.toFloat()

            val width = MeasureSpec.getSize(widthMeasureSpec)
            var height = MeasureSpec.getSize(heightMeasureSpec)

            if (width > 0) {
                height = (width.toFloat() / ratio).toInt()
            }

            setMeasuredDimension(width, height)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)

            setMeasuredDimension(measuredWidth, measuredWidth)
        }
    }

}

inline fun ViewManager.ratioImageView() = ratioImageView {}

inline fun ViewManager.ratioImageView(init: RatioImageView.() -> Unit): RatioImageView {
    return ankoView({ RatioImageView(it) }, theme = 0, init = init)
}