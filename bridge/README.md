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
