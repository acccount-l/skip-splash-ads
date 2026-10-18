package com.splashskip.engine.engine

import com.splashskip.engine.model.RectI

object Bounds {
    /**
     * allowed 为归一化矩形 [left, top, right, bottom]；x/y 为屏幕像素坐标。
     */
    fun contains(
        allowed: List<Float>,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Boolean {
        if (width <= 0 || height <= 0 || allowed.size != 4) return false
        val nx = x.toFloat() / width
        val ny = y.toFloat() / height
        return nx >= allowed[0] && nx <= allowed[2] &&
            ny >= allowed[1] && ny <= allowed[3]
    }

    fun normalizedCenter(bounds: RectI, width: Int, height: Int): Pair<Float, Float> {
        require(width > 0 && height > 0) { "屏幕尺寸必须大于 0" }
        return bounds.centerX.toFloat() / width to bounds.centerY.toFloat() / height
    }
}
