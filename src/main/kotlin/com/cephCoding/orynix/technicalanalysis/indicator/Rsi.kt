package com.cephCoding.orynix.technicalanalysis.indicator

object Rsi {
    fun calculate(closes: List<Double>, period: Int = 14): List<Double> {
        if (closes.size < period + 1) return emptyList()

        val changes = (1 until closes.size).map { closes[it] - closes[it - 1] }
        val gains = changes.map { if (it > 0) it else 0.0 }
        val losses = changes.map { if (it < 0) -it else 0.0 }

        val avgGains = Ema.wilderSmooth(gains, period)
        val avgLosses = Ema.wilderSmooth(losses, period)

        return avgGains.indices.map { i ->
            val avgGain = avgGains[i]
            val avgLoss = avgLosses[i]
            when {
                avgGain == 0.0 && avgLoss == 0.0 -> 50.0
                avgLoss == 0.0 -> 100.0
                avgGain == 0.0 -> 0.0
                else -> 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
            }
        }
    }
}
