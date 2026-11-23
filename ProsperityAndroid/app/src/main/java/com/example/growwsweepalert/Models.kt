package com.example.growwsweepalert

enum class SupportedUnderlying { NIFTY, SENSEX }\nenum class OptionType { CE, PE }
enum class InstrumentType { SPOT_INDEX, OPTION }
enum class DetectionKind { LIVE_PRICE_CROSS, CANDLE_WICK_SWEEP }
enum class SweepDirection { UP, DOWN }

data class OptionContract(
    val underlying: String,
    val expiry: String,
    val strike: Int,
    val optionType: OptionType,
    val tradingSymbol: String
) {
    val displayName: String get() = "$underlying $expiry $strike ${optionType.name}"
}

data class SpotInstrument(
    val indexName: String,
    val tradingSymbol: String
)

data class OptionChainRow(
    val strike: Int,
    val callSymbol: String?,
    val putSymbol: String?
)

data class OptionChainSnapshot(
    val underlying: String,
    val expiries: List<String>,
    val selectedExpiry: String,
    val rows: List<OptionChainRow>
)

data class IndicatorLevels(
    val previousDayHigh: Double?,
    val previousDayLow: Double?,
    val previousWeekHigh: Double?,
    val previousWeekLow: Double?
) {
    fun asNamedLevels(): List<Pair<String, Double?>> = listOf(
        "Previous Day High" to previousDayHigh,
        "Previous Day Low" to previousDayLow,
        "Previous Week High" to previousWeekHigh,
        "Previous Week Low" to previousWeekLow
    )
}

data class MarketTick(
    val symbol: String,
    val lastTradedPrice: Double,
    val exchangeTimestampMillis: Long
)

data class Candle(
    val symbol: String,
    val startTimeMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

data class SweepAlert(
    val symbol: String,
    val instrumentType: InstrumentType,
    val levelName: String,
    val level: Double,
    val observedPrice: Double,
    val direction: SweepDirection,
    val detectionKind: DetectionKind,
    val timestampMillis: Long
)