#!/usr/bin/env python3
"""Kotak Neo -> Prosperity LAN bridge. Market data and alerts only; no order calls."""
from __future__ import annotations

import asyncio
import getpass
import json
import os
import sys
import time
import urllib.request
from pathlib import Path

CANDLE_MS = 5 * 60 * 1000
ROOT = Path(__file__).resolve().parent
LEVELS_FILE = ROOT / "levels.json"
BRIDGE_URL = os.getenv("PROSPERITY_BRIDGE_URL", "http://127.0.0.1:8765").rstrip("/")
BRIDGE_TOKEN = os.getenv("PROSPERITY_BRIDGE_TOKEN", "")
CHAIN_COUNT = int(os.getenv("KOTAK_CHAIN_COUNT", "10"))
FLUSH_SECONDS = float(os.getenv("PROSPERITY_FLUSH_SECONDS", "1.0"))


def prompt_value(env_name: str, label: str, secret: bool = False) -> str:
    value = os.getenv(env_name, "").strip()
    if value:
        return value
    return (getpass.getpass if secret else input)(f"{label}: ").strip()


def load_levels() -> dict:
    if not LEVELS_FILE.exists():
        print("No local levels.json found. Copy levels.example.json to levels.json and enter verified levels.")
        return {"underlyings": {}, "options": {}}
    try:
        data = json.loads(LEVELS_FILE.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {"underlyings": {}, "options": {}}
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"Cannot read {LEVELS_FILE}: {exc}")


def positive_number(value):
    try:
        number = float(value)
        return number if number > 0 else None
    except (TypeError, ValueError):
        return None


def classic_pivots(high, low, close) -> dict:
    h, l, c = map(float, (high, low, close))
    if h <= 0 or l <= 0 or c <= 0 or h < l:
        return {}
    p, d = (h + l + c) / 3.0, h - l
    return {
        "P": p, "R1": 2 * p - l, "S1": 2 * p - h,
        "R2": p + d, "S2": p - d,
        "R3": h + 2 * (p - l), "S3": l - 2 * (h - p),
        "R4": p + 2 * d, "S4": p - 2 * d,
        "R5": p + 3 * d, "S5": p - 3 * d,
    }


def normalize_levels(raw: dict) -> dict:
    raw = raw if isinstance(raw, dict) else {}
    result = {}
    for key in ("pdh", "pdl", "pwh", "pwl"):
        value = positive_number(raw.get(key))
        if value is not None:
            result[key] = value
    pivots = raw.get("pivots")
    if not isinstance(pivots, dict):
        h, l, c = (positive_number(raw.get(k)) for k in ("pdh", "pdl", "pdc"))
        pivots = classic_pivots(h, l, c) if h and l and c else {}
    allowed = {"P", "R1", "R2", "R3", "R4", "R5", "S1", "S2", "S3", "S4", "S5"}
    result["pivots"] = {
        key: float(value) for key, value in pivots.items()
        if key in allowed and positive_number(value) is not None
    }
    return result


def chain_rows(client, underlying: str, exchange: str):
    response = client.option_chain(
        exchange=exchange, underlying=underlying,
        instrument_type="option", count=CHAIN_COUNT,
    )
    data = response.get("data", response) if isinstance(response, dict) else {}
    rows = []
    for side_key, side in (("call", "CE"), ("put", "PE")):
        for row in data.get(side_key, []) or []:
            instrument = row.get("instrument") or row.get("inst") or {}
            neo_symbol = instrument.get("neoSymbol", "")
            if "|" not in neo_symbol:
                continue
            segment, token = neo_symbol.split("|", 1)
            symbol = instrument.get("symbol") or neo_symbol
            try:
                strike = int(float(instrument.get("strikePrice")))
            except (TypeError, ValueError):
                strike = None
            rows.append({
                "segment": segment, "token": str(token), "symbol": str(symbol),
                "underlying": underlying, "side": side, "strike": strike,
                "expiry": data.get("common_data", {}).get("expiryDt"),
            })
    if not rows:
        raise RuntimeError(f"Kotak returned no option contracts for {underlying}: {response}")
    return rows


class CandleBuilder:
    """Build completed 5-minute candles from live ticks; never emits partial candles."""
    def __init__(self):
        self.current = {}

    def update(self, symbol: str, ts_ms: int, price: float):
        bucket = (ts_ms // CANDLE_MS) * CANDLE_MS
        old = self.current.get(symbol)
        completed = None
        if old and old["bucket"] != bucket:
            completed = {
                "open": old["open"], "high": old["high"], "low": old["low"],
                "close": old["close"], "timestamp": old["bucket"],
            }
            old = None
        if old is None:
            self.current[symbol] = {
                "bucket": bucket, "open": price, "high": price,
                "low": price, "close": price,
            }
        else:
            old["high"] = max(old["high"], price)
            old["low"] = min(old["low"], price)
            old["close"] = price
        return completed


def post_batch(items: list[dict]):
    if not items:
        return
    body = json.dumps({"instruments": items}, separators=(",", ":")).encode("utf-8")
    request = urllib.request.Request(
        BRIDGE_URL + "/v1/ingest", data=body,
        headers={
            "Authorization": "Bearer " + BRIDGE_TOKEN,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=8) as response:
        if response.status not in (200, 202):
            raise RuntimeError(f"Bridge returned HTTP {response.status}")


async def stream_market(client, subscriptions, levels_config):
    from neo_api_client.websocket.feed import WsToken

    index_tokens = [WsToken("nse_cm", "Nifty 50"), WsToken("bse_cm", "SENSEX")]
    option_tokens = [WsToken(x["segment"], x["token"]) for x in subscriptions]
    metadata = {(x["segment"], x["token"]): x for x in subscriptions}
    latest: dict[str, dict] = {}
    pending_candles: list[dict] = []
    candle_builder = CandleBuilder()

    def levels_for(symbol: str, underlying: str) -> dict:
        # Never apply index-price levels to an option premium.
        if symbol in ("NIFTY", "SENSEX"):
            raw = levels_config.get("underlyings", {}).get(underlying, {})
        else:
            raw = levels_config.get("options", {}).get(symbol, {})
        return normalize_levels(raw)

    async with client.create_websocket() as ws:
        await ws.subscribe_index(index_tokens)
        await ws.subscribe_scrips(option_tokens)
        print(f"Live feed connected: 2 indices + {len(option_tokens)} option contracts.")
        print("Completed 5-minute candles are built locally. No order endpoints are called.")

        async def flush_loop():
            while True:
                await asyncio.sleep(FLUSH_SECONDS)
                batch = [dict(item) for item in latest.values()]
                batch.extend(pending_candles)
                pending_candles.clear()
                if batch:
                    try:
                        await asyncio.to_thread(post_batch, batch)
                    except Exception as exc:
                        print(f"Bridge publish warning: {exc}", file=sys.stderr)

        flusher = asyncio.create_task(flush_loop())
        try:
            async for message in ws:
                kind = getattr(message, "type", "")
                segment = getattr(message, "exchange_segment", "")
                token = str(getattr(message, "instrument_token", ""))
                meta = metadata.get((segment, token))
                if kind == "index":
                    if segment == "nse_cm":
                        underlying, symbol = "NIFTY", "NIFTY"
                    elif segment == "bse_cm":
                        underlying, symbol = "SENSEX", "SENSEX"
                    else:
                        continue
                    price = float(message.last_traded_price)
                    instrument_type, side, strike = "SPOT_INDEX", None, None
                elif kind == "scrip" and meta:
                    underlying, symbol = meta["underlying"], meta["symbol"]
                    price = float(message.last_traded_price)
                    instrument_type, side, strike = "OPTION", meta["side"], meta["strike"]
                else:
                    continue

                if price <= 0:
                    continue
                stamp = int(time.time() * 1000)
                record = {
                    "type": instrument_type, "underlying": underlying,
                    "symbol": symbol, "ltp": price, "timestamp": stamp,
                    "levels": levels_for(symbol, underlying),
                }
                if side:
                    record["side"] = side
                    if strike is not None:
                        record["strike"] = strike
                latest[symbol] = record

                completed = candle_builder.update(symbol, stamp, price)
                if completed:
                    candle_record = dict(record)
                    candle_record["timeframe"] = "5m"
                    candle_record["candle"] = completed
                    pending_candles.append(candle_record)
        finally:
            flusher.cancel()
            try:
                await flusher
            except asyncio.CancelledError:
                pass


def main():
    if not BRIDGE_TOKEN:
        raise SystemExit("Set PROSPERITY_BRIDGE_TOKEN before starting the adapter.")
    if CHAIN_COUNT <= 0 or CHAIN_COUNT % 10:
        raise SystemExit("KOTAK_CHAIN_COUNT must be a positive multiple of 10.")
    try:
        from neo_api_client import NeoAPI
    except ImportError:
        raise SystemExit("Install dependencies first: python -m pip install -r requirements.txt")

    consumer_key = prompt_value("NEO_CONSUMER_KEY", "Kotak Consumer Key", True)
    mobile = prompt_value("NEO_MOBILE_NUMBER", "Registered mobile (+91...)")
    ucc = prompt_value("NEO_UCC", "Kotak UCC")
    totp = prompt_value("NEO_TOTP", "Current 6-digit TOTP", True)
    mpin = prompt_value("NEO_MPIN", "Kotak MPIN", True)

    client = NeoAPI(consumer_key=consumer_key, environment="prod")
    print("Authenticating with Kotak Neo...")
    client.totp_login(mobile_number=mobile, ucc=ucc, totp=totp)
    client.totp_validate(mpin=mpin)
    print("Authentication completed.")

    chains = []
    for underlying, exchange in (("NIFTY", "nse_fo"), ("SENSEX", "bse_fo")):
        rows = chain_rows(client, underlying, exchange)
        chains.extend(rows)
        print(f"{underlying}: {len(rows)} contracts · expiry {rows[0].get('expiry') or 'nearest available'}")
        for row in rows:
            print(f"  {row['symbol']} · {row['side']} · strike {row['strike']}")

    levels_config = load_levels()
    asyncio.run(stream_market(client, chains, levels_config))


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nProsperity Kotak adapter stopped.")
