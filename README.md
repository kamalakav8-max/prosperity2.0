# Prosperity Signal Desk

Browser-based market monitoring app. This repository hosts the standalone Prosperity Market Signal Desk web app.

## Features
- NIFTY and SENSEX selector, 1m/5m/15m monitoring.
- Classic floor pivots P, R1-R5 and S1-S5 from previous-day H/L/C.
- PDH, PDL, PWH and PWL reference levels.
- Exact CE/PE contract symbol watchlist and separate option premium PDH/PDL inputs.
- Completed-candle high-sweep and low-sweep rejection detection.
- Browser notification support and demo signal.
- Authorized secure WebSocket feed adapter; no order execution.

## Feed format
Send JSON like:
`{"type":"candle","symbol":"NIFTY","timeframe":"5m","high":22518,"low":22470,"close":22488,"timestamp":"2026-11-21T10:05:00+05:30","levels":{"PDH":22500,"PWH":22600}}`

This is a generic adapter, not a live Groww integration. Do not scrape private endpoints, extract session cookies or bypass access controls. Use only a data source you are authorized to access.
