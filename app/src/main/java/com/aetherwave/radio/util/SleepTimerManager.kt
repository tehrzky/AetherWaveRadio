package com.aetherwave.radio.util

import android.os.CountDownTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Long = 0,
    val totalSeconds: Long = 0,
    val fadeOut: Boolean = true
)

class SleepTimerManager(
    private val onTickVolumeFade: (volumeRatio: Float) -> Unit,
    private val onTimerFinished: () -> Unit
) {
    private var countDownTimer: CountDownTimer? = null
    private val _timerState = MutableStateFlow(SleepTimerState())
    val timerState: StateFlow<SleepTimerState> = _timerState.asStateFlow()

    fun startTimer(minutes: Int, fadeOut: Boolean) {
        cancelTimer()
        val totalMs = minutes * 60 * 1000L

        _timerState.value = SleepTimerState(
            isActive = true,
            remainingSeconds = totalMs / 1000,
            totalSeconds = totalMs / 1000,
            fadeOut = fadeOut
        )

        countDownTimer = object : CountDownTimer(totalMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val remainingSec = millisUntilFinished / 1000
                _timerState.value = _timerState.value.copy(remainingSeconds = remainingSec)

                if (fadeOut && remainingSec <= 60) {
                    val ratio = (remainingSec.toFloat() / 60f).coerceIn(0f, 1f)
                    onTickVolumeFade(ratio)
                }
            }

            override fun onFinish() {
                _timerState.value = SleepTimerState()
                onTimerFinished()
            }
        }.start()
    }

    fun cancelTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        _timerState.value = SleepTimerState()
    }
}
