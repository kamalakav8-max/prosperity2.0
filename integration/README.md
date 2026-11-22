# Prosperity 915 visible-data bridge (Windows → Android)

This integration is a **user-controlled visible-page capture prototype**. It does not call Groww private APIs, inspect network traffic, read cookies/session storage, or bypass access controls.

## Layout

- Windows Chrome: open the 915 Groww page and install the unpacked extension from `chrome-extension/`.
- Windows: run the local bridge in `bridge/`.
- Android: connect to the Windows PC's LAN address and open the Prosperity dashboard served by the bridge.

The Android phone and Windows PC must be on the same trusted Wi-Fi/LAN. Keep Windows awake while using the bridge.

## 1. Start the Windows bridge

Install Python 3.11+ and run:

```powershell
cd integration/bridge
py -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
py server.py
```

The terminal prints a one-time pairing token and the dashboard URL. Keep the terminal open. The server binds to the local network so your Android phone can connect. Use this only on a trusted private network; do not port-forward it to the internet.

## 2. Install the Chrome extension

1. Open `chrome://extensions` in Windows Chrome.
2. Enable **Developer mode**.
3. Choose **Load unpacked** and select this repository's `integration/chrome-extension` folder.
4. Open `config.js` and replace `PASTE_TOKEN_FROM_BRIDGE_TERMINAL` with the token printed by the bridge. Reload the extension.
5. Open the 915 Groww market page. The small Prosperity capture panel appears on the page.
6. Press **Start visible capture**. It sends only the visible page text, while the tab is active. Press **Stop** to stop.

Only capture pages/data you are authorized to access and that Groww permits you to use this way. Avoid pages containing personal account details. The capture is intentionally visible-page text, not hidden browser/network data.

## 3. Connect Android

Find the Windows PC's IPv4 address (for example, run `ipconfig` and look for the active Wi-Fi adapter's IPv4 address). On Android Chrome, open:

`http://WINDOWS_PC_IP:8765`

Enter the pairing token printed by the bridge and connect. You can add the page to your home screen.

## Market fields adopted from the supplied APK

Prosperity now has an independent normalized market-data contract in `bridge/market_models.py`. It covers:

- Live price fields: timestamp, OHLC, LTP, volume, value, bid/offer quantities, average price, price ranges and open interest.
- Market depth: up to five buy and sell price/quantity levels.
- Index and instrument identity through symbol, exchange and segment.
- Option Greeks: Delta, Gamma, Theta, Vega, Rho, IV and the additional sensitivity fields represented in the APK.
- Straddle candles: CE, PE, strike price, spot price and synthetic future for open/high/low/close.
- Chart-pattern metadata: pattern, trend, entry, stop-loss, target and analysis text.

The bridge now accepts a structured `market_update` message from an authorized adapter and forwards normalized values to the Android dashboard. Example message shape:

```json
{
  "type": "market_update",
  "data": {
    "symbol": "NIFTY",
    "exchange": "NSE",
    "segment": "FNO",
    "price": {"ltp": 22450, "open": 22400, "high": 22500, "low": 22380, "volume": 1000, "openInterest": 1234},
    "depth": {"buy": [{"price": 22449, "qty": 10}], "sell": [{"price": 22451, "qty": 12}]},
    "greeks": {"delta": 0.52, "gamma": 0.01, "theta": -4.2, "vega": 8.1, "iv": 14.2},
    "straddle": {"close": {"ce": 120, "pe": 110, "strikePrice": 22450, "spotPrice": 22450, "syntheticFuture": 22452}}
  }
}
```

This is a Prosperity application data contract based on fields found in the uploaded APK. It is not Groww's private socket protocol, does not include Groww endpoints or credentials, and does not itself provide live data access. The existing Chrome extension still captures rendered visible text only; a structured adapter must be connected using a data source and access method you are authorized to use before these cards show live values.

Run the model tests from `integration/bridge` with `py -m unittest -v`.

## What this prototype does / does not do

- Streams captured visible text snapshots from Windows Chrome to the Android dashboard.
- Keeps the latest snapshot and shows capture time/source URL.
- Does **not yet infer a reliable full option-chain schema** from Groww's changing UI. The exact selectors/field mapping need to be validated against a sample capture from your 915 page.
- Does not execute trades. No order placement is implemented.
- Android Chrome cannot load this desktop Chrome extension; Windows Chrome is the capture source and Android is the Prosperity display.

## Troubleshooting

- Windows Firewall: allow Python on **Private networks** only.
- If Android cannot connect, verify both devices are on the same Wi-Fi, use the PC's current IPv4 address, and check that port 8765/8766 is not blocked.
- Do not expose these ports to the public internet.
