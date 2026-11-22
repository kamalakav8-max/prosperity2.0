"""Independent Prosperity market-data contract informed by the user-supplied 915 APK.
This is not a Groww transport client and does not connect to private endpoints.
"""
from __future__ import annotations
from datetime import datetime, timezone
from typing import Any

def num(value: Any):
    if value is None or value == "": return None
    try:
        result = float(value)
        return result if result == result and abs(result) != float("inf") else None
    except (TypeError, ValueError): return None

def book(rows):
    result = []
    if isinstance(rows, list):
        for row in rows[:5]:
            if isinstance(row, dict):
                price = num(row.get("price")); qty = num(row.get("qty", row.get("quantity")))
                if price is not None and qty is not None: result.append({"price": price, "quantity": qty})
    return result

def normalize_market_update(payload: dict) -> dict:
    symbol = str(payload.get("symbol", "")).strip().upper()[:80]
    if not symbol: raise ValueError("market update requires a symbol")
    raw = payload.get("price") if isinstance(payload.get("price"), dict) else {}
    aliases = {
        "open":"open","high":"high","low":"low","close":"close","ltp":"ltp",
        "volume":"volume","value":"value","bid_qty":"bidQty","offer_qty":"offerQty",
        "avg_price":"avgPrice","open_interest":"openInterest",
        "high_price_range":"highPriceRange","low_price_range":"lowPriceRange",
        "low_trade_range":"lowTradeRange","high_trade_range":"highTradeRange",
        "timestamp_ms":"tsInMillis"
    }
    price = {key:num(raw.get(source, raw.get(key))) for key,source in aliases.items()}
    depth = payload.get("depth") if isinstance(payload.get("depth"), dict) else {}
    raw_g = payload.get("greeks") if isinstance(payload.get("greeks"), dict) else {}
    greek_names = ("delta","gamma","theta","vega","rho","iv","ivVwap","charm","color","dtr","speed","tgr","tv","vanna","veta","volga","zomma","pop")
    greeks = {key:num(raw_g.get(key)) for key in greek_names}
    raw_s = payload.get("straddle") if isinstance(payload.get("straddle"), dict) else {}
    straddle = {}
    for leg in ("open","high","low","close"):
        candle = raw_s.get(leg) if isinstance(raw_s.get(leg), dict) else {}
        straddle[leg] = {
            "ce":num(candle.get("ce")), "pe":num(candle.get("pe")),
            "strike_price":num(candle.get("strikePrice",candle.get("strike_price"))),
            "spot_price":num(candle.get("spotPrice",candle.get("spot_price"))),
            "synthetic_future":num(candle.get("syntheticFuture",candle.get("synthetic_future")))
        }
    raw_p = payload.get("pattern") if isinstance(payload.get("pattern"), dict) else {}
    return {
        "symbol":symbol,"exchange":str(payload.get("exchange","NSE")).upper(),
        "segment":str(payload.get("segment","FNO")).upper(),
        "captured_at":datetime.now(timezone.utc).isoformat(),"index_value":num(payload.get("index_value", payload.get("value"))),"price":price,
        "depth":{"buy":book(depth.get("buy",depth.get("buyBook"))),"sell":book(depth.get("sell",depth.get("sellBook")))},
        "greeks":greeks,"straddle":straddle,
        "pattern":{"name":str(raw_p.get("name",raw_p.get("pattern","")))[:80],
          "trend":str(raw_p.get("trend","")).upper()[:24],
          "entry":num(raw_p.get("entry_level",raw_p.get("entry"))),
          "stop_loss":num(raw_p.get("stop_loss")),
          "target":num(raw_p.get("target_level",raw_p.get("target"))),
          "analysis":str(raw_p.get("analysis_text",raw_p.get("analysis","")))[:500]}
    }

def classic_pivots(high: float, low: float, close: float) -> dict:
    p=(high+low+close)/3; width=high-low
    r1=2*p-low; s1=2*p-high; r2=p+width; s2=p-width
    r3=high+2*(p-low); s3=low-2*(high-p)
    return {"P":p,"R1":r1,"R2":r2,"R3":r3,"R4":r3+width,"R5":r3+2*width,
            "S1":s1,"S2":s2,"S3":s3,"S4":s3-width,"S5":s3-2*width}

def detect_sweep(previous_high: float, previous_low: float, candle: dict) -> list[str]:
    high=num(candle.get("high")); low=num(candle.get("low")); close=num(candle.get("close"))
    if high is None or low is None or close is None: return []
    signals=[]
    if high>previous_high and close<previous_high: signals.append("HIGH_SWEEP_REJECTION")
    if low<previous_low and close>previous_low: signals.append("LOW_SWEEP_RECLAIM")
    return signals
