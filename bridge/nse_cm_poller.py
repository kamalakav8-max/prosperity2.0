#!/usr/bin/env python3
"""Poll NSE CM Market MCP and publish its delayed market pulse to Prosperity.

This adapter intentionally exposes NSE's published cash-market movers/status.
NSE's CM Market MCP does not expose NIFTY/SENSEX index levels or F&O option-chain
fields, so it never invents spot/index or CE/PE values.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from typing import Any

from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client

NSE_URL = "https://mcp.nseindia.in/cmmkt/mcp"


def unpack_result(result: Any) -> Any:
    """Extract structured content where possible; otherwise parse text blocks."""
    structured = getattr(result, "structuredContent", None)
    if structured is not None:
        return structured
    values = []
    for part in getattr(result, "content", []) or []:
        text = getattr(part, "text", None)
        if text is None:
            continue
        try:
            values.append(json.loads(text))
        except json.JSONDecodeError:
            values.append(text)
    if len(values) == 1:
        return values[0]
    return values


async def call(session: ClientSession, name: str, args: dict) -> Any:
    result = await session.call_tool(name, arguments=args)
    data = unpack_result(result)
    if getattr(result, "isError", False):
        raise RuntimeError(f"{name} returned an MCP error: {data}")
    if isinstance(data, dict) and data.get("error"):
        raise RuntimeError(f"{name} returned an NSE error: {data['error']}")
    return data


def publish(bridge_url: str, token: str, payload: dict) -> None:
    url = bridge_url.rstrip("/") + "/v1/market-ingest"
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": "Bearer " + token,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        if response.status not in (200, 202):
            raise RuntimeError(f"Bridge rejected market update: HTTP {response.status}")
        response.read()


async def run(args: argparse.Namespace) -> int:
    token = args.token or os.getenv("PROSPERITY_BRIDGE_TOKEN", "")
    bridge_url = args.bridge_url or os.getenv("PROSPERITY_BRIDGE_URL", "")
    if not token or not bridge_url:
        print("Set PROSPERITY_BRIDGE_TOKEN and PROSPERITY_BRIDGE_URL, or pass --token and --bridge-url.", file=sys.stderr)
        return 2

    async with streamablehttp_client(NSE_URL) as (read_stream, write_stream, _):
        async with ClientSession(read_stream, write_stream) as session:
            await session.initialize()
            listed = await session.list_tools()
            available = {tool.name for tool in listed.tools}
            required = {"cm_get_data_status", "nse_get_market_movers"}
            missing = required - available
            if missing:
                print("NSE server is missing expected tools: " + ", ".join(sorted(missing)), file=sys.stderr)
                return 1

            print("Connected to NSE CM Market MCP. Publishing status + top movers; no index/option-chain feed is exposed here.", flush=True)
            while True:
                try:
                    status, movers = await asyncio.gather(
                        call(session, "cm_get_data_status", {}),
                        call(session, "nse_get_market_movers", {"indexName": "NIFTY", "limit": 10}),
                    )
                    payload = {
                        "status": status,
                        "movers": movers,
                        "nseUpdatedAt": datetime.now(timezone.utc).isoformat(),
                    }
                    publish(bridge_url, token, payload)
                    print("Published NSE market pulse at " + payload["nseUpdatedAt"], flush=True)
                except (Exception) as exc:
                    print(f"NSE poll/publish error: {type(exc).__name__}: {exc}", file=sys.stderr, flush=True)
                if args.once:
                    break
                await asyncio.sleep(max(60, args.interval))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Poll delayed NSE CM Market data into Prosperity's LAN bridge")
    parser.add_argument("--bridge-url", default="", help="Bridge base URL, e.g. http://192.168.1.10:8765")
    parser.add_argument("--token", default="", help="Bridge bearer token; prefer PROSPERITY_BRIDGE_TOKEN env var")
    parser.add_argument("--interval", type=int, default=60, help="Polling interval in seconds (minimum 60)")
    parser.add_argument("--once", action="store_true", help="Poll once and exit")
    return asyncio.run(run(parser.parse_args()))


if __name__ == "__main__":
    raise SystemExit(main())
