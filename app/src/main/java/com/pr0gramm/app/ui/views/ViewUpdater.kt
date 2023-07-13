package com.pr0gramm.app.ui.views

import android.widget.TextView
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.ui.base.MainScope
import com.pr0gramm.app.ui.base.onAttachedScope
import com.pr0gramm.app.util.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

object ViewUpdater {
    private val tickerSeconds = sharedTickerFlow(Duration.seconds(1))
    private val tickerMinute = sharedTickerFlow(Duration.minutes(1))

    private fun ofInstant(instant: Instant): Flow<Unit>? {
        val delta = Duration.between(Instant.now(), instant)
            .convertTo(TimeUnit.SECONDS)
            .absoluteValue

        return when {
            delta > 3600 -> null
            delta > 60 -> tickerMinute
            else -> tickerSeconds
        }
    }

    fun replaceText(view: TextView, instant: Instant, text: () -> CharSequence) {
        view.text = text()

        ofInstant(instant)?.let { ticker ->
            view.onAttachedScope {
                ticker.collect { view.text = text() }
            }
        }
    }
}

fun sharedTickerFlow(interval: Duration): Flow<Unit> {
    val flow = MutableStateFlow<Long>(0)

    var subscriptionCount = 0
    var jobTicker: Job? = null

    return flow {
        if (jobTicker == null) {
            jobTicker = MainScope.launch {
                while (true) {
                    delay(Duration.seconds(1))
                    flow.value = System.currentTimeMillis()
                }
            }
        }

        subscriptionCount++

        try {
            emitAll(flow.drop(1).map { })

        } finally {
            withContext(NonCancellable) {
                if (--subscriptionCount == 0) {
                    jobTicker?.cancel()
                    jobTicker = null
                }
            }
        }
    }
}
