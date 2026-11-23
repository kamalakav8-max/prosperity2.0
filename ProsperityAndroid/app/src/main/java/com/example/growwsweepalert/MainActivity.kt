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
import androidx.compose.ui.unit.dp

data class ContractChoice(val strike: Int, val side: String, val symbol: String)

data class ContractLevels(
    val pdh: Double?,
    val pdl: Double?,
    val pwh: Double?,
    val pwl: Double?
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
    )

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
    var underlying by remember { mutableStateOf("NIFTY") }
    var expiry by remember { mutableStateOf("") }
    val strikes = listOf(25900, 25950, 26000, 26050, 26100, 26150, 26200)
    val selected = remember { mutableStateListOf<ContractChoice>() }
    var monitoring by remember { mutableStateOf(false) }
    var bridgeUrl by remember { mutableStateOf("http://192.168.1.100:8765") }
    var bridgeToken by remember { mutableStateOf("") }
    var bridgeStatus by remember { mutableStateOf("Disconnected") }
    var bridgeData by remember { mutableStateOf("No live data received yet") }
    var bridgeClient by remember { mutableStateOf<LanBridgeClient?>(null) }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("GROWW SWEEP ALERT", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Companion monitor • No order execution",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                Text("Underlying", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("NIFTY", "SENSEX").forEach { name ->
                        FilterChip(
                            selected = underlying == name,
                            onClick = { underlying = name },
                            label = { Text(name) }
                        )
                    }
                }
            }
            item {
                Text("Expiry", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = expiry,
                    onValueChange = { expiry = it },
                    label = { Text("Expiry from connected option chain") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "Live expiries will be populated by the authorized option-chain connection.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item { Text("Select strikes", style = MaterialTheme.typography.titleMedium) }
            items(strikes) { strike ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(strike.toString(), style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf("CE", "PE").forEach { side ->
                                val isSelected = selected.any {
                                    it.strike == strike && it.side == side
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selected.removeAll {
                                                it.strike == strike && it.side == side
                                            }
                                            if (checked) {
                                                selected.add(
                                                    ContractChoice(
                                                        strike, side,
                                                        "$underlying-$expiry-$strike-$side"
                                                    )
                                                )
                                            }
                                        }
                                    )
                                    Text(side)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("Selected contracts: ${selected.size}")
                Button(
                    onClick = { monitoring = !monitoring },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selected.isNotEmpty()
                ) {
                    Text(if (monitoring) "Pause monitoring" else "Start monitoring")
                }
                Text(
                    if (monitoring)
                        "Monitoring is armed in the interface. Live data connection is still required."
                    else "Select contracts, then start monitoring.",
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
                    "Watched levels: 915 Previous Day High, Previous Day Low, Previous Week High and Previous Week Low.",
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
                Text("No alerts yet. The data adapter and Android notification delivery are not connected.")
            }
        }
    }
}