package com.jedl.videoprogen

import android.view.Choreographer

class FrameClock(private val fps: Int, private val onFrame: (frame: Long) -> Unit) : Choreographer.FrameCallback {
    private var startNanos = 0L
    @Volatile private var running = false

    fun start() {
        if (running) return
        startNanos = System.nanoTime(); running = true
        Choreographer.getInstance().postFrameCallback(this)
    }
    fun stop() { running = false }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val frame = (frameTimeNanos - startNanos) * fps / 1_000_000_000L
        onFrame(frame)
        if (running) Choreographer.getInstance().postFrameCallback(this)
    }
}
