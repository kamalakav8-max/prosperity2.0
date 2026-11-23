package com.example.growwsweepalert

/**
 * Shared detector for option contracts AND spot indices.
 * Supply the exact PDH/PDL/PWH/PWL values from the authorized data provider.
 */
class SweepEngine {
    private val lastTick = mutableMapOf<String, Double>()
    private val lastCandleClose = mutableMapOf<String, Double>()
    private val emitted = mutableSetOf<String>()

    @Synchronized
    fun onTick(
        tick: MarketTick,
        levels: IndicatorLevels,
        instrumentType: InstrumentType
    ): List<SweepAlert> {
        val price = tick.lastTradedPrice
        if (!price.isFinite() || price <= 0.0) return emptyList()
        val previous = lastTick.put(tick.symbol, price) ?: return emptyList()

        return levels.asNamedLevels().mapNotNull { (name, level) ->
            if (level == null || !level.isFinite()) return@mapNotNull null
            val direction = when {
                previous <= level && price > level -> SweepDirection.UP
                previous >= level && price < level -> SweepDirection.DOWN
                else -> return@mapNotNull null
            }
            val key = "${tick.symbol}|TICK|$name|$direction|${tick.exchangeTimestampMillis}"
            if (!emitted.add(key)) return@mapNotNull null
            SweepAlert(tick.symbol, instrumentType, name, level, price, direction,
                DetectionKind.LIVE_PRICE_CROSS, tick.exchangeTimestampMillis)
        }
    }

    @Synchronized
    fun onCandle(
        candle: Candle,
        levels: IndicatorLevels,
        instrumentType: InstrumentType
    ): List<SweepAlert> {
        if (!candle.high.isFinite() || !candle.low.isFinite() ||
            !candle.close.isFinite() || candle.high < candle.low) return emptyList()
        val previousClose = lastCandleClose.put(candle.symbol, candle.close) ?: return emptyList()

        return levels.asNamedLevels().mapNotNull { (name, level) ->
            if (level == null || !level.isFinite()) return@mapNotNull null
            val direction = when {
                previousClose <= level && candle.high > level -> SweepDirection.UP
                previousClose >= level && candle.low < level -> SweepDirection.DOWN
                else -> return@mapNotNull null
            }
            val observed = if (direction == SweepDirection.UP) candle.high else candle.low
            val key = "${candle.symbol}|WICK|${candle.startTimeMillis}|$name|$direction"
            if (!emitted.add(key)) return@mapNotNull null
            SweepAlert(candle.symbol, instrumentType, name, level, observed, direction,
                DetectionKind.CANDLE_WICK_SWEEP, candle.startTimeMillis)
        }
    }

    @Synchronized
    fun reset() {
        lastTick.clear()
        lastCandleClose.clear()
        emitted.clear()
    }
}