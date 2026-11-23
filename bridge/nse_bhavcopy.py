#!/usr/bin/env python3
"""Connect to NSE's official Bhavcopy MCP server and inspect/call its tools.

This server is end-of-day historical CM data, not a live index or F&O option-chain feed.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
from typing import Any

from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client

NSE_BHAVCOPY_URL = "https://mcp.nseindia.in/bhavcopy/cm/mcp"


def jsonable(value: Any) -> Any:
    if hasattr(value, "model_dump"):
        return value.model_dump(mode="json")
    if hasattr(value, "__dict__"):
        return value.__dict__
    return str(value)


async def run(args: argparse.Namespace) -> int:
    try:
        async with streamablehttp_client(NSE_BHAVCOPY_URL) as (read_stream, write_stream, _):
            async with ClientSession(read_stream, write_stream) as session:
                await session.initialize()
                if args.list_tools:
                    result = await session.list_tools()
                    print(json.dumps([jsonable(tool) for tool in result.tools], indent=2, ensure_ascii=False))
                    return 0

                if not args.tool:
                    print("Choose --list-tools or provide --tool TOOL_NAME --args-json '{}'.", file=sys.stderr)
                    return 2

                try:
                    tool_args = json.loads(args.args_json)
                except json.JSONDecodeError as exc:
                    print(f"Invalid --args-json: {exc}", file=sys.stderr)
                    return 2
                if not isinstance(tool_args, dict):
                    print("--args-json must be a JSON object.", file=sys.stderr)
                    return 2

                result = await session.call_tool(args.tool, arguments=tool_args)
                payload = {
                    "isError": getattr(result, "isError", False),
                    "structuredContent": getattr(result, "structuredContent", None),
                    "content": [jsonable(part) for part in getattr(result, "content", [])],
                }
                print(json.dumps(payload, indent=2, ensure_ascii=False, default=str))
                return 1 if payload["isError"] else 0
    except Exception as exc:
        print(f"NSE MCP connection failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 1


def main() -> int:
    parser = argparse.ArgumentParser(description="NSE Bhavcopy MCP client (historical CM data)")
    parser.add_argument("--list-tools", action="store_true", help="discover available NSE Bhavcopy tools and schemas")
    parser.add_argument("--tool", help="exact tool name returned by --list-tools")
    parser.add_argument("--args-json", default="{}", help="JSON object matching the selected tool's input schema")
    return asyncio.run(run(parser.parse_args()))


if __name__ == "__main__":
    raise SystemExit(main())
