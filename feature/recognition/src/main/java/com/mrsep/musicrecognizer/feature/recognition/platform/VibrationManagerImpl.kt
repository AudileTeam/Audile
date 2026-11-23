package com.mrsep.musicrecognizer.feature.recognition.platform

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import com.mrsep.musicrecognizer.core.common.util.getDefaultVibrator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class VibrationManagerImpl @Inject constructor(
    @ApplicationContext private val appContext: Context
) : VibrationManager {

    private val vibrator: Vibrator = appContext.getDefaultVibrator()

    override fun vibrateSuccess() {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (trySuccessComposition()) return
//            if (tryPredefined(VibrationEffect.EFFECT_HEAVY_CLICK, HapticUsage.RecognitionResult)) return
        }

        if (vibrator.hasAmplitudeControl()) {
            vibrateWaveform(
                usage = HapticUsage.RecognitionResult,
                timings = longArrayOf(0, 13, 13, 13, 13, 13, 13, 13),
                amplitudes = intArrayOf(0, 30, 70, 105, 140, 175, 220, 255)
            )
        } else {
            vibrateWaveform(
                usage = HapticUsage.RecognitionResult,
                timings = longArrayOf(0, 90)
            )
        }
    }

    override fun vibrateFailure() {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (tryFailureComposition()) return
//            if (tryPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK, HapticUsage.RecognitionResult)) return
        }

        if (vibrator.hasAmplitudeControl()) {
            vibrateWaveform(
                usage = HapticUsage.RecognitionResult,
                timings = longArrayOf(0, 15, 15, 15, 15, 90, 15, 15, 15, 15),
                amplitudes = intArrayOf(0, 50, 150, 250, 50, 0, 50, 150, 100, 50)
            )
        } else {
            vibrateWaveform(
                usage = HapticUsage.RecognitionResult,
                timings = longArrayOf(0, 55, 90, 55)
            )
        }
    }

    override fun vibrateOnTap() {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (tryPredefined(VibrationEffect.EFFECT_CLICK, HapticUsage.Tap)) return
        }

        if (vibrator.hasAmplitudeControl()) {
            vibrateWaveform(
                usage = HapticUsage.Tap,
                timings = longArrayOf(0, 15, 15, 15),
                amplitudes = intArrayOf(0, 40, 80, 40),
            )
        } else {
            vibrateWaveform(
                usage = HapticUsage.Tap,
                timings = longArrayOf(0, 45),
            )
        }
    }

    override fun vibrateOnNowPlaying() {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (tryPredefined(VibrationEffect.EFFECT_TICK, HapticUsage.RecognitionResult)) return
        }

        if (vibrator.hasAmplitudeControl()) {
            vibrateWaveform(
                usage = HapticUsage.RecognitionResult,
                timings = longArrayOf(0, 12),
                amplitudes = intArrayOf(0, 40),
            )
        } else {
            vibrateWaveform(
                usage = HapticUsage.RecognitionResult,
                timings = longArrayOf(0, 25),
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun trySuccessComposition(): Boolean = when {
        vibrator.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            VibrationEffect.Composition.PRIMITIVE_TICK,
        ) -> {
            vibrate(
                effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 1f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.60f, 70)
                    .compose(),
                usage = HapticUsage.RecognitionResult
            )
            true
        }

        else -> false
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun tryFailureComposition(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
            VibrationEffect.Composition.PRIMITIVE_THUD,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK
        ) -> {
            vibrate(
                effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.80f, 55)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.55f, 55)
                    .compose(),
                usage = HapticUsage.RecognitionResult
            )
            true
        }

        vibrator.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
            VibrationEffect.Composition.PRIMITIVE_TICK
        ) -> {
            vibrate(
                effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 55)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 55)
                    .compose(),
                usage = HapticUsage.RecognitionResult
            )
            true
        }

        else -> false
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun tryPredefined(effectId: Int, usage: HapticUsage): Boolean {
        if (vibrator.areAllEffectsSupported(effectId) != Vibrator.VIBRATION_EFFECT_SUPPORT_YES) return false
        vibrate(VibrationEffect.createPredefined(effectId), usage)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun vibrate(effect: VibrationEffect, usage: HapticUsage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.Builder()
                    .setUsage(usage.asVibrationAttribute)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun vibrateWaveform(
        usage: HapticUsage,
        timings: LongArray,
        amplitudes: IntArray? = null,
    ) {
        val effect = if (amplitudes != null) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        vibrate(effect, usage)
    }
}

private enum class HapticUsage {
    Tap,
    RecognitionResult;

    val asVibrationAttribute @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    get() = when (this) {
        Tap -> VibrationAttributes.USAGE_TOUCH
        RecognitionResult -> VibrationAttributes.USAGE_MEDIA
    }
}
