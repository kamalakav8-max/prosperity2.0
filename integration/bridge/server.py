from __future__ import annotations

import asyncio
import json
import secrets
import threading
from datetime import datetime, timezone
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

from websockets.asyncio.server import serve

ROOT = Path(__file__).resolve().parent.parent / "dashboard"
HOST = "0.0.0.0"
HTTP_PORT = 8765
WS_PORT = 8766
TOKEN = secrets.token_urlsafe(24)
latest_snapshot: dict | None = None
clients: set = set()
state_lock = asyncio.Lock()


class DashboardHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def log_message(self, fmt, *args):
        # Keep request logs minimal; never log captured market-page content.
        print("[http]", self.address_string(), fmt % args)


async def ws_handler(websocket):
    global latest_snapshot
    try:
        first = await asyncio.wait_for(websocket.recv(), timeout=15)
        auth = json.loads(first)
        if auth.get("type") != "auth" or not secrets.compare_digest(str(auth.get("token", "")), TOKEN):
            await websocket.close(code=4401, reason="Pairing token rejected")
            return

        role = auth.get("role", "dashboard")
        async with state_lock:
            clients.add(websocket)
            snapshot = latest_snapshot

        await websocket.send(json.dumps({"type": "status", "connected": True, "role": role}))
        if snapshot:
            await websocket.send(json.dumps({"type": "snapshot", "data": snapshot}))

        async for raw in websocket:
            if role != "capture":
                continue
            try:
                message = json.loads(raw)
            except json.JSONDecodeError:
                continue
            if message.get("type") != "visible_snapshot":
                continue
            data = message.get("data", {})
            # Bound payload size and fields; this is a visible-text relay, not a data archive.
            text = str(data.get("text", ""))[:120_000]
            latest_snapshot = {
                "type": "snapshot",
                "captured_at": datetime.now(timezone.utc).isoformat(),
                "source_url": str(data.get("source_url", ""))[:500],
                "page_title": str(data.get("page_title", ""))[:300],
                "text": text,
                "character_count": len(text),
            }
            outgoing = json.dumps({"type": "snapshot", "data": latest_snapshot})
            async with state_lock:
                peers = list(clients)
            for peer in peers:
                if peer is not websocket:
                    try:
                        await peer.send(outgoing)
                    except Exception:
                        pass
    except (asyncio.TimeoutError, Exception) as exc:
        # Connection errors are expected when a tab or phone disconnects.
        if exc.__class__.__name__ not in {"ConnectionClosedOK", "ConnectionClosedError"}:
            print("[ws] client disconnected")
    finally:
        async with state_lock:
            clients.discard(websocket)


def main():
    print("\nProsperity visible-data bridge")
    print(f"Pairing token: {TOKEN}")
    print(f"Dashboard: http://<THIS-PC-LAN-IP>:{HTTP_PORT}")
    print(f"WebSocket relay: ws://<THIS-PC-LAN-IP>:{WS_PORT}")
    print("Use on a trusted private LAN only. Do not port-forward these ports.\n")

    httpd = ThreadingHTTPServer((HOST, HTTP_PORT), DashboardHandler)
    threading.Thread(target=httpd.serve_forever, daemon=True).start()

    async def run_ws():
        async with serve(ws_handler, HOST, WS_PORT, max_size=140_000, ping_interval=20):
            await asyncio.Future()

    try:
        asyncio.run(run_ws())
    except KeyboardInterrupt:
        print("\nBridge stopped.")
        httpd.shutdown()


if __name__ == "__main__":
    main()
