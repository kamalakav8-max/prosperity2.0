package com.example.growwsweepalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class ContractChoice(val strike: Int, val side: String, val symbol: String)

data class ContractLevels(
    val pdh: Double?,
    val pdl: Double?,
    val pwh: Double?,
    val pwl: Double?,
    val pivots: Map<String, Double> = emptyMap()
)

enum class SweepType { LIVE_PRICE, CANDLE_WICK }

data class SweepEvent(
    val symbol: String,
    val levelName: String,
    val level: Double,
    val observedPrice: Double,
    val direction: String,
    val type: SweepType,
    val timestampMillis: Long
)

/**
 * Sweep engine for both detection modes.
 *
 * LIVE_PRICE:
 *   Emits when consecutive live prices cross a level.
 *
 * CANDLE_WICK:
 *   Emits when a candle's high/low extends across a level from the
 *   previous candle close. This is separate from the live-price alert.
 *
 * Levels must be supplied by a verified, authorized data adapter.
 * This class does not scrape private endpoints or invent indicator values.
 */
class SweepDetector {
    private val previousPrices = mutableMapOf<String, Double>()
    private val fired = mutableSetOf<String>()
    private val previousCandleClose = mutableMapOf<String, Double>()

    private fun levelPairs(levels: ContractLevels) = listOf(
        "Previous Day High" to levels.pdh,
        "Previous Day Low" to levels.pdl,
        "Previous Week High" to levels.pwh,
        "Previous Week Low" to levels.pwl
    ) + levels.pivots.map { it.key to it.value }

    fun onLivePrice(
        symbol: String,
        price: Double,
        levels: ContractLevels
    ): List<SweepEvent> {
        val previous = previousPrices.put(symbol, price) ?: return emptyList()
        return levelPairs(levels).mapNotNull { (name, level) ->
            if (level == null) return@mapNotNull null
            val direction = when {
                previous <= level && price > level -> "UP"
                previous >= level && price < level -> "DOWN"
                else -> return@mapNotNull null
            }
            val key = "$symbol|LIVE|$name|$direction"
            if (!fired.add(key)) return@mapNotNull null
            SweepEvent(
                symbol, name, level, price, direction,
                SweepType.LIVE_PRICE, System.currentTimeMillis()
            )
        }
    }

    fun onCandle(
        symbol: String,
        candleTimeMillis: Long,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        levels: ContractLevels
    ): List<SweepEvent> {
        val previousClose = previousCandleClose[symbol]
        previousCandleClose[symbol] = close
        if (previousClose == null) return emptyList()

        return levelPairs(levels).mapNotNull { (name, level) ->
            if (level == null) return@mapNotNull null
            val direction = when {
                previousClose <= level && high > level -> "UP"
                previousClose >= level && low < level -> "DOWN"
                else -> return@mapNotNull null
            }
            val key = "$symbol|WICK|$candleTimeMillis|$name|$direction"
            if (!fired.add(key)) return@mapNotNull null
            val extreme = if (direction == "UP") high else low
            SweepEvent(
                symbol, name, level, extreme, direction,
                SweepType.CANDLE_WICK, candleTimeMillis
            )
        }
    }

    fun resetSession() {
        previousPrices.clear()
        previousCandleClose.clear()
        fired.clear()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SweepApp() } }
    }
}

@Composable
fun SweepApp() {
    var monitoring by remember { mutableStateOf(false) }
    val currentMonitoring = rememberUpdatedState(monitoring)
    var bridgeUrl by remember { mutableStateOf("http://192.168.1.100:8765") }
    var bridgeToken by remember { mutableStateOf("") }
    var bridgeStatus by remember { mutableStateOf("Disconnected") }
    var bridgeData by remember { mutableStateOf("No live data received yet") }
    var bridgeClient by remember { mutableStateOf<LanBridgeClient?>(null) }
    val context = LocalContext.current
    val sweepDetector = remember { SweepDetector() }
    val alertNotifications = remember(context) { AlertNotifications(context) }
    LaunchedEffect(alertNotifications) { alertNotifications.createChannel() }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("PROSPERITY 2.0", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Kotak Neo market monitor • Signal alerts only • No order execution",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                Text("Markets and contracts", style = MaterialTheme.typography.titleMedium)
                Text("NIFTY 50 + SENSEX")
                Text(
                    "The Windows Kotak adapter discovers the nearest-expiry CE/PE option chains and streams the subscribed contracts automatically. No manual strike selection is required.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Button(
                    onClick = { monitoring = !monitoring },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (monitoring) "Pause alerts" else "Start monitoring")
                }
                Text(
                    if (monitoring)
                        "Alerts are enabled for incoming NIFTY, SENSEX and subscribed option updates."
                    else "Monitoring is paused. Connect the Windows bridge, then tap Start monitoring.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                HorizontalDivider()
                Text("Sweep detection", style = MaterialTheme.typography.titleMedium)
                Text("Both detection modes are enabled:")
                Text("• Live-price cross: alert when consecutive traded prices cross a level.")
                Text("• Candle-wick sweep: alert when a candle high/low crosses a level from the previous candle close.")
                Text("Each alert records contract, level, direction, observed price, detection type and time.")
                Text(
                    "Watched levels: verified PDH, PDL, PWH, PWL and classic pivots loaded from your private Windows levels.json file.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                HorizontalDivider()
                Text("Windows LAN bridge", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = bridgeUrl,
                    onValueChange = { bridgeUrl = it },
                    label = { Text("Windows PC address (e.g. http://192.168.1.10:8765)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = bridgeToken,
                    onValueChange = { bridgeToken = it },
                    label = { Text("Shared bridge token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        bridgeClient?.close()
                        val client = LanBridgeClient()
                        bridgeClient = client
                        client.connect(bridgeUrl, bridgeToken, object : LanBridgeClient.Listener {
                            override fun onState(state: String) { bridgeStatus = state }
                            override fun onData(payload: org.json.JSONObject) {
                                val items = payload.optJSONArray("instruments")
                                val count = items?.length() ?: 0
                                if (!currentMonitoring.value) {
                                    bridgeData = "$count instrument update(s) received. Alerts are paused."
                                    return
                                }
                                if (items != null) {
                                    for (index in 0 until items.length()) {
                                        val item = items.optJSONObject(index) ?: continue
                                        val levelsJson = item.optJSONObject("levels") ?: org.json.JSONObject()
                                        fun level(key: String): Double? {
                                            val value = levelsJson.optDouble(key, Double.NaN)
                                            return value.takeIf { it.isFinite() && it > 0.0 }
                                        }
                                        val symbol = item.optString("symbol")
                                        val ltp = item.optDouble("ltp", Double.NaN)
                                        if (symbol.isBlank() || !ltp.isFinite() || ltp <= 0.0) continue
                                        val pivotsJson = levelsJson.optJSONObject("pivots")
                                        val pivotValues = mutableMapOf<String, Double>()
                                        pivotsJson?.let { pivots ->
                                            val keys = pivots.keys()
                                            while (keys.hasNext()) {
                                                val key = keys.next()
                                                val value = pivots.optDouble(key, Double.NaN)
                                                if (key in setOf("P", "R1", "R2", "R3", "R4", "R5", "S1", "S2", "S3", "S4", "S5") &&
                                                    value.isFinite() && value > 0.0) {
                                                    pivotValues[key] = value
                                                }
                                            }
                                        }
                                        val levels = ContractLevels(
                                            pdh = level("pdh"),
                                            pdl = level("pdl"),
                                            pwh = level("pwh"),
                                            pwl = level("pwl"),
                                            pivots = pivotValues
                                        )
                                        val instrumentType = if (item.optString("type") == "SPOT_INDEX") {
                                            InstrumentType.SPOT_INDEX
                                        } else {
                                            InstrumentType.OPTION
                                        }
                                        fun notifyEvents(events: List<SweepEvent>, detection: DetectionKind) {
                                            events.forEach { event ->
                                                alertNotifications.show(
                                                    SweepAlert(
                                                        symbol = event.symbol,
                                                        instrumentType = instrumentType,
                                                        levelName = event.levelName,
                                                        level = event.level,
                                                        observedPrice = event.observedPrice,
                                                        direction = if (event.direction == "UP") SweepDirection.UP else SweepDirection.DOWN,
                                                        detectionKind = detection,
                                                        timestampMillis = event.timestampMillis
                                                    )
                                                )
                                            }
                                        }
                                        notifyEvents(
                                            sweepDetector.onLivePrice(symbol, ltp, levels),
                                            DetectionKind.LIVE_PRICE_CROSS
                                        )
                                        val candle = item.optJSONObject("candle")
                                        if (candle != null) {
                                            val open = candle.optDouble("open", Double.NaN)
                                            val high = candle.optDouble("high", Double.NaN)
                                            val low = candle.optDouble("low", Double.NaN)
                                            val close = candle.optDouble("close", Double.NaN)
                                            if (open.isFinite() && high.isFinite() && low.isFinite() && close.isFinite()) {
                                                notifyEvents(
                                                    sweepDetector.onCandle(
                                                        symbol,
                                                        candle.optLong("timestamp", item.optLong("timestamp", System.currentTimeMillis())),
                                                        open, high, low, close, levels
                                                    ),
                                                    DetectionKind.CANDLE_WICK_SWEEP
                                                )
                                            }
                                        }
                                    }
                                }
                                bridgeData = count.toString() + " instrument update(s): " + payload.toString().take(360)
                            }
                        })
                    }) { Text("Connect") }
                    OutlinedButton(onClick = {
                        bridgeClient?.close()
                        bridgeClient = null
                        bridgeStatus = "Disconnected"
                    }) { Text("Disconnect") }
                }
                Text("Status: " + bridgeStatus, style = MaterialTheme.typography.bodyMedium)
                Text(bridgeData, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Connect your Windows PC and phone to the same private Wi-Fi. A separate authorized provider must send data to the Windows receiver.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                HorizontalDivider()
                Text("Alert history", style = MaterialTheme.typography.titleMedium)
                Text("Alerts appear as Android notifications when incoming prices or completed 5-minute candles cross configured reference levels.")
            }
        }
    }
}