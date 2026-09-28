package com.ifthen.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.ifthen.app.R

/** 完成打卡的「叮」声，滴答清单式短促确认音 */
object SuccessChime {
    private var soundPool: SoundPool? = null
    private var soundId = 0

    fun init(context: Context) {
        if (soundPool != null) return
        val sp = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        soundId = sp.load(context.applicationContext, R.raw.ding, 1)
        soundPool = sp
    }

    fun play() {
        soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
    }
}
