package com.example.growwsweepalert

/**
 * Authorized provider boundary for both spot indices and option contracts.
 * Provider callbacks must deliver per-symbol PDH/PDL/PWH/PWL and live ticks/candles.
 */
interface MarketDataProvider {
    suspend fun loadOptionChain(underlying: String): OptionChainSnapshot
    suspend fun loadSpotIndices(): List<SpotInstrument> // NIFTY 50 and SENSEX only
    suspend fun subscribeOptions(contracts: List<OptionContract>, listener: MarketDataListener)
    suspend fun subscribeSpot(instruments: List<SpotInstrument>, listener: MarketDataListener)
    suspend fun unsubscribe()
}

interface MarketDataListener {
    fun onLevels(symbol: String, levels: IndicatorLevels)
    fun onTick(tick: MarketTick, instrumentType: InstrumentType)
    fun onCandle(candle: Candle, instrumentType: InstrumentType)
    fun onConnectionState(state: ConnectionState, detail: String? = null)
}

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

class NotConnectedProvider : MarketDataProvider {
    override suspend fun loadOptionChain(underlying: String): OptionChainSnapshot =
        throw IllegalStateException("Authorized 915/Groww option-chain provider is not configured.")
    override suspend fun loadSpotIndices(): List<SpotInstrument> // NIFTY 50 and SENSEX only =
        throw IllegalStateException("Authorized spot-index provider is not configured.")
    override suspend fun subscribeOptions(contracts: List<OptionContract>, listener: MarketDataListener) {
        listener.onConnectionState(ConnectionState.ERROR, "Live option data adapter is not configured.")
    }
    override suspend fun subscribeSpot(instruments: List<SpotInstrument>, listener: MarketDataListener) {
        listener.onConnectionState(ConnectionState.ERROR, "Live spot data adapter is not configured.")
    }
    override suspend fun unsubscribe() = Unit
}