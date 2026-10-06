package com.cephCoding.orynix.technicalanalysis.indicator

object Sma {
    fun calculate(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return emptyList()
        return (period..values.size).map { end -> values.subList(end - period, end).average() }
    }
}
