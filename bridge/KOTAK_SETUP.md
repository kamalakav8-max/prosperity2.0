# Prosperity 2.0 — Kotak Neo signal-only connection

This adapter connects the official Kotak Neo Python SDK to the existing authenticated Prosperity LAN bridge. It subscribes to NIFTY 50 and SENSEX index updates and nearest-expiry option chains, then publishes normalized quotes and completed 5-minute candles to the Windows bridge.

**No order-placement, modification or cancellation function is called.**

## Requirements
- Windows PC with Python 3.10 or later
- Kotak Neo Trade API enabled
- Kotak Consumer Key and registered TOTP
- Prosperity receiver.py running on the same PC
- Android phone and Windows PC on the same private Wi-Fi

Kotak currently lists the Trade API product as free of subscription charges. Brokerage, taxes, statutory charges and data entitlements are separate and can change. This project uses the official kotakneoapi Python SDK v3.0.13 or later.

## Install
Open PowerShell in the bridge folder:
~~~powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
~~~

## Configure levels privately
Copy the example file. The real levels.json is ignored by Git and must remain on your PC:
~~~powershell
Copy-Item .\levels.example.json .\levels.json
notepad .\levels.json
~~~

Enter verified previous-day and previous-week levels. If PDH, PDL and PDC are provided, the adapter calculates classic floor pivots P, R1–R5 and S1–S5. For option contracts, use the exact trading symbol returned by Kotak and enter that contract's own premium levels. Never copy index levels onto option premiums.

## Start the local bridge
PowerShell window 1:
~~~powershell
$env:PROSPERITY_BRIDGE_TOKEN = "use-a-long-random-private-token"
python .\receiver.py
~~~

Keep this window open. Restrict Windows Firewall port 8765 to your Private network. Do not port-forward the bridge to the public internet.

## Start Kotak live market data
PowerShell window 2, using the same bridge token:
~~~powershell
$env:PROSPERITY_BRIDGE_TOKEN = "use-a-long-random-private-token"
python .\kotak_neo_adapter.py
~~~

The adapter prompts locally for Consumer Key, registered mobile, UCC, current TOTP and MPIN. Credentials are not written to GitHub or sent to Android. Never paste credentials into ChatGPT, GitHub, screenshots or the dashboard.

The adapter fetches the nearest available NIFTY and SENSEX option chains and subscribes to index and option market feeds. KOTAK_CHAIN_COUNT controls the number of strikes returned per side; it must be a positive multiple of 10 (default 10).
~~~powershell
$env:KOTAK_CHAIN_COUNT = "20"
~~~

## Connect Android
1. Find the Windows PC Wi-Fi IPv4 address with ipconfig.
2. On Android, open Prosperity and enter http://WINDOWS-PC-IP:8765.
3. Enter the same bridge token.
4. Tap Connect.
5. Tap Start monitoring in the Android app to enable notifications.
6. Keep both Windows processes running.

The adapter sends live index/option prices and completed 5-minute candles. Android alerts require verified levels in the local levels.json. Restart the adapter after editing that file.

## Data and limitations
- The adapter uses Kotak's modern SFeed WebSocket, not the deprecated HS feed.
- Option chain selection is nearest available expiry with a configurable strike count.
- Previous-day/week option premium levels are not inferred from the current chain. Enter verified values locally.
- The initial adapter aggregates 5-minute candles locally; it does not fabricate missing ticks.
- This is a signal-only decision-support tool with no order-placement path.
- The GitHub repository is public. Do not commit credentials, levels.json or proprietary strategy rules.

## Regulatory note
The NSE circular dated 20 November 2026 clarifies requirements for retail algorithmic trading and tech-savvy retail clients. This implementation is deliberately limited to market-data display and alerts and sends no orders. Before adding order automation or relying on a particular API hosting arrangement, confirm applicability and current requirements with Kotak Neo and the relevant exchange.