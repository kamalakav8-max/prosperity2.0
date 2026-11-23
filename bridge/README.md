# Prosperity LAN data bridge

This Windows-side bridge is a **transport layer**, not a Groww/915 login client. It accepts normalized market data from a provider or adapter that the account holder is authorized to use, then exposes a local snapshot and Server-Sent Events stream to Prosperity on the same private Wi-Fi network.

It does not scrape private app traffic, reuse session cookies/tokens, bypass authentication, place orders, or fabricate prices.

## Run on Windows

1. Install Python 3.10+.
2. Open PowerShell in this folder.
3. Set a strong local-only shared token:

```powershell
$env:PROSPERITY_BRIDGE_TOKEN = "replace-with-a-long-random-secret"
python .\receiver.py
```

4. Find the Windows PC's Wi-Fi IPv4 address with `ipconfig`.
5. Allow inbound TCP port 8765 on **Private** Windows networks only. Do not expose it to public networks.

## API

All endpoints except `/health` require `Authorization: Bearer <token>`.

- `GET /health` — receiver health
- `GET /v1/snapshot` — latest values
- `GET /v1/stream` — live Server-Sent Events stream
- `POST /v1/ingest` — ingest normalized data from an authorized source

Example ingest body:

```json
{
  "instruments": [
    {
      "type": "SPOT_INDEX",
      "underlying": "NIFTY",
      "symbol": "NIFTY",
      "ltp": 25000.5,
      "timestamp": 1795435200000,
      "levels": {
        "pdh": 25100, "pdl": 24850, "pwh": 25200, "pwl": 24700,
        "pivots": { "P": 24980, "R1": 25110, "R2": 25200, "R3": 25330, "R4": 25420, "R5": 25550, "S1": 24890, "S2": 24760, "S3": 24670, "S4": 24580, "S5": 24450 }
      },
      "candle": { "open": 24990, "high": 25030, "low": 24970, "close": 25010, "timestamp": 1795435200000 }
    },
    {
      "type": "OPTION",
      "underlying": "NIFTY",
      "symbol": "NIFTY-2026-11-26-25000-CE",
      "side": "CE",
      "strike": 25000,
      "ltp": 125.4,
      "timestamp": 1795435200000,
      "levels": {
        "pdh": 140, "pdl": 98, "pwh": 155, "pwl": 90,
        "pivots": { "P": 120, "R1": 135, "R2": 145, "R3": 160, "R4": 170, "R5": 185, "S1": 105, "S2": 95, "S3": 80, "S4": 70, "S5": 55 }
      }
    }
  ]
}
```

The Android app still needs its LAN client wired to these endpoints. A separate authorized source adapter is also required before any real 915/Groww data will arrive. The 915 APK inspection identified socket/NATS and Protobuf components, but does not grant permission to reuse private endpoints or authentication material.

## NSE Bhavcopy MCP connection

Prosperity now includes `nse_bhavcopy.py`, a client for NSE's official public Streamable HTTP MCP endpoint:
`https://mcp.nseindia.in/bhavcopy/cm/mcp`.

Install its dependency and discover the exact tools and input schemas published by NSE:

```powershell
python -m pip install -r .\\requirements.txt
python .\\nse_bhavcopy.py --list-tools
```

Then call a discovered tool using its exact name and schema:

```powershell
python .\\nse_bhavcopy.py --tool TOOL_NAME --args-json '{}'
```

Replace `TOOL_NAME` and the JSON object with the tool name and required arguments shown by `--list-tools`. The client deliberately does not guess NSE tool names or parameters.

**Data limitation:** this Bhavcopy MCP server provides historical end-of-day Capital Market data (up to five years, updated with the prior trading day's final values). It is useful for historical OHLCV, previous-session references and research. It is not a live NIFTY/SENSEX feed and does not provide F&O option-chain fields such as CE/PE LTP, OI, change in OI or IV. For delayed intraday CM snapshots, NSE publishes a separate endpoint at `https://mcp.nseindia.in/cmmkt/mcp`; that is also not a tick-by-tick F&O feed.

The MCP client is a first integration step. It discovers and retrieves NSE Bhavcopy results; a schema-specific mapper is still needed to convert a selected tool's returned result into Prosperity's normalized `/v1/ingest` format. Do not treat EOD Bhavcopy values as current live prices.

## NSE CM Market MCP connection (delayed intraday cash-market data)

Prosperity now also includes `nse_cm_market.py`, a client for NSE's official CM Market MCP endpoint:
`https://mcp.nseindia.in/cmmkt/mcp`.

Discover the live CM Market tool names and exact input schemas:

```powershell
python -m pip install -r requirements.txt
python nse_cm_market.py --list-tools
```

Call a tool only with the exact name and JSON schema returned by discovery:

```powershell
python nse_cm_market.py --tool TOOL_NAME --args-json '{}'
```

This server provides cash-market/index information with an approximately 1-3 minute delay. It is not tick-by-tick data, order-book data or a documented F&O option-chain feed. Do not use it as a substitute for CE/PE LTP, OI, change in OI or IV. NSE states its MCP data is for informational/educational use and not for real-time trading, investment advice or commercial products.

### How this fits the Prosperity architecture

- `nse_bhavcopy.py`: historical end-of-day cash-market data for prior-session research and reference levels.
- `nse_cm_market.py`: delayed intraday cash-market/index queries.
- `receiver.py`: authenticated private-LAN ingestion and browser stream for normalized provider data.
- A mapper from each discovered NSE tool's response schema into Prosperity's `/v1/ingest` format is the next integration step. It must be written against actual returned schemas; this repo does not guess undocumented tool names or data fields.

The clients currently perform MCP discovery/calls and print the official server response. They do not yet run a continuous poller or automatically populate the dashboard. Keep option-chain data on a separate authorized derivatives adapter.
