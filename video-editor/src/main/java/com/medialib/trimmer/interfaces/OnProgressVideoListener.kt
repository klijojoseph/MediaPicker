package com.medialib.trimmer.interfaces

interface OnProgressVideoListener {
    fun updateProgress(time: Float, max: Float, scale: Float)
}