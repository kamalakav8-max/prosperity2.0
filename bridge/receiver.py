#!/usr/bin/env python3
"""Prosperity LAN bridge. Accepts normalized data from an authorized provider."""
from __future__ import annotations

import json
import os
import queue
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

HOST = "0.0.0.0"
PORT = int(os.getenv("PROSPERITY_BRIDGE_PORT", "8765"))
TOKEN = os.getenv("PROSPERITY_BRIDGE_TOKEN", "")
MAX_BODY = 1_000_000
_lock = threading.Lock()
_latest: dict[str, dict] = {}
_market_snapshot: dict = {"updatedAt": None, "status": None, "movers": None, "source": "NSE CM Market MCP"}
_subscribers: set[queue.Queue] = set()


def valid_instrument(item: dict) -> bool:
    if not isinstance(item, dict):
        return False
    if item.get("type") not in ("SPOT_INDEX", "OPTION"):
        return False
    if not isinstance(item.get("symbol"), str) or not item["symbol"].strip():
        return False
    if item.get("underlying", "NIFTY") not in ("NIFTY", "SENSEX"):
        return False
    if item["type"] == "OPTION" and item.get("side") not in ("CE", "PE"):
        return False
    price = item.get("ltp")
    if price is not None and (not isinstance(price, (int, float)) or price <= 0):
        return False
    levels = item.get("levels", {})
    if not isinstance(levels, dict):
        return False
    for key in ("pdh", "pdl", "pwh", "pwl"):
        value = levels.get(key)
        if value is not None and (not isinstance(value, (int, float)) or value <= 0):
            return False
    pivots = levels.get("pivots", {})
    allowed_pivots = {"P", "R1", "R2", "R3", "R4", "R5", "S1", "S2", "S3", "S4", "S5"}
    if not isinstance(pivots, dict) or any(
        key not in allowed_pivots or not isinstance(value, (int, float)) or value <= 0
        for key, value in pivots.items()
    ):
        return False
    candle = item.get("candle")
    if candle is not None:
        if not isinstance(candle, dict):
            return False
        values = [candle.get(key) for key in ("open", "high", "low", "close")]
        if any(not isinstance(value, (int, float)) or value <= 0 for value in values):
            return False
        if candle["high"] < candle["low"]:
            return False
    return True


class Handler(BaseHTTPRequestHandler):
    server_version = "ProsperityBridge/1.0"

    def log_message(self, fmt, *args):
        print("%s - %s" % (self.client_address[0], fmt % args), flush=True)

    def _json(self, status: int, payload: dict):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()
        self.wfile.write(data)

    def _authorized(self) -> bool:
        if not TOKEN:
            self._json(503, {"ok": False, "error": "Set PROSPERITY_BRIDGE_TOKEN before starting."})
            return False
        supplied = self.headers.get("Authorization", "")
        if supplied != "Bearer " + TOKEN:
            self._json(401, {"ok": False, "error": "Unauthorized"})
            return False
        return True

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Max-Age", "600")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            return self._json(200, {"ok": True, "service": "Prosperity LAN bridge"})
        if not self._authorized():
            return
        if parsed.path == "/v1/market-snapshot":
            with _lock:
                snapshot = dict(_market_snapshot)
            return self._json(200, {"ok": True, **snapshot})
        if parsed.path == "/v1/snapshot":
            with _lock:
                items = list(_latest.values())
            symbols = set(parse_qs(parsed.query).get("symbol", []))
            if symbols:
                items = [x for x in items if x["symbol"] in symbols]
            return self._json(200, {"ok": True, "received_at": int(time.time() * 1000), "instruments": items})
        if parsed.path == "/v1/stream":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            q: queue.Queue = queue.Queue(maxsize=100)
            with _lock:
                _subscribers.add(q)
            try:
                self.wfile.write(b": connected\n\n")
                self.wfile.flush()
                while True:
                    try:
                        event = q.get(timeout=20)
                        data = json.dumps(event, separators=(",", ":")).encode()
                        self.wfile.write(b"event: market\ndata: " + data + b"\n\n")
                    except queue.Empty:
                        self.wfile.write(b": keepalive\n\n")
                    self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError):
                pass
            finally:
                with _lock:
                    _subscribers.discard(q)
            return
        self._json(404, {"ok": False, "error": "Not found"})

    def do_POST(self):
        path = urlparse(self.path).path
        if path not in ("/v1/ingest", "/v1/market-ingest"):
            return self._json(404, {"ok": False, "error": "Not found"})
        if not self._authorized():
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if size <= 0 or size > MAX_BODY:
                return self._json(413, {"ok": False, "error": "Body must be 1 byte to 1 MB"})
            body = json.loads(self.rfile.read(size))
            if path == "/v1/market-ingest":
                if not isinstance(body, dict) or not any(key in body for key in ("status", "movers")):
                    return self._json(400, {"ok": False, "error": "Expected status and/or movers object"})
                stamp = int(time.time() * 1000)
                with _lock:
                    _market_snapshot.update({
                        "updatedAt": stamp,
                        "status": body.get("status", _market_snapshot.get("status")),
                        "movers": body.get("movers", _market_snapshot.get("movers")),
                        "nseUpdatedAt": body.get("nseUpdatedAt"),
                    })
                    event = {"type": "nse_market", **_market_snapshot}
                    for subscriber in tuple(_subscribers):
                        try:
                            subscriber.put_nowait(event)
                        except queue.Full:
                            pass
                return self._json(202, {"ok": True, "updatedAt": stamp})
            instruments = body.get("instruments") if isinstance(body, dict) else None
            if not isinstance(instruments, list) or not instruments or not all(valid_instrument(x) for x in instruments):
                return self._json(400, {"ok": False, "error": "Expected non-empty instruments array with valid symbols, type, ltp and levels"})
        except (ValueError, TypeError, json.JSONDecodeError):
            return self._json(400, {"ok": False, "error": "Invalid JSON"})
        stamp = int(time.time() * 1000)
        batch = []
        with _lock:
            for item in instruments:
                record = dict(item)
                record["receivedAt"] = stamp
                _latest[record["symbol"]] = record
                batch.append(record)
            event = {"receivedAt": stamp, "instruments": batch}
            for subscriber in tuple(_subscribers):
                try:
                    subscriber.put_nowait(event)
                except queue.Full:
                    pass
        self._json(202, {"ok": True, "accepted": len(batch), "receivedAt": stamp})


if __name__ == "__main__":
    if not TOKEN:
        raise SystemExit("Set PROSPERITY_BRIDGE_TOKEN to a long random value before running.")
    print(f"Prosperity LAN bridge listening on http://{HOST}:{PORT}", flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
