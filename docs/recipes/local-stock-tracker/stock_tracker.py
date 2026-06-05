#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import math
import sqlite3
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import pandas as pd
import yfinance as yf


BASE_DIR = Path(__file__).resolve().parent
DEFAULT_CONFIG = BASE_DIR / "config.json"
SCHEMA_FILE = BASE_DIR / "schema.sql"


@dataclass
class TickerSnapshot:
    ticker: str
    fetched_at: str
    source_bar_time: str | None
    market_price: float | None
    open: float | None
    high: float | None
    low: float | None
    close: float | None
    adj_close: float | None
    volume: float | None
    prev_close: float | None
    change: float | None
    change_pct: float | None
    day_low: float | None
    day_high: float | None
    sma_20: float | None
    sma_50: float | None
    ema_12: float | None
    ema_26: float | None
    macd: float | None
    macd_signal: float | None
    macd_hist: float | None
    rsi_14: float | None
    atr_14: float | None
    vol_20d: float | None
    avg_volume_20d: float | None
    market_cap: float | None
    pe_ratio: float | None


def now_utc() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def as_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        if isinstance(value, float) and math.isnan(value):
            return None
        return float(value)
    except (TypeError, ValueError):
        return None


def load_config(path: Path) -> dict[str, Any]:
    data = json.loads(path.read_text())
    tickers = data.get("tickers") or []
    if not tickers:
        raise SystemExit("config must define a non-empty 'tickers' list")
    database = data.get("database")
    if not database:
        raise SystemExit("config must define a 'database' path")
    return data


def connect_db(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db(conn: sqlite3.Connection) -> None:
    conn.executescript(SCHEMA_FILE.read_text())
    conn.commit()


def rsi(close: pd.Series, period: int = 14) -> pd.Series:
    delta = close.diff()
    gain = delta.where(delta > 0, 0.0)
    loss = -delta.where(delta < 0, 0.0)
    avg_gain = gain.ewm(alpha=1 / period, adjust=False, min_periods=period).mean()
    avg_loss = loss.ewm(alpha=1 / period, adjust=False, min_periods=period).mean()
    rs = avg_gain / avg_loss
    return 100 - (100 / (1 + rs))


def atr(frame: pd.DataFrame, period: int = 14) -> pd.Series:
    high_low = frame["High"] - frame["Low"]
    high_close = (frame["High"] - frame["Close"].shift()).abs()
    low_close = (frame["Low"] - frame["Close"].shift()).abs()
    true_range = pd.concat([high_low, high_close, low_close], axis=1).max(axis=1)
    return true_range.ewm(alpha=1 / period, adjust=False, min_periods=period).mean()


def market_price_from_fast_info(ticker: yf.Ticker) -> float | None:
    fast = getattr(ticker, "fast_info", None)
    if not fast:
        return None
    for key in ("lastPrice", "last_price", "regularMarketPrice", "regular_market_price"):
        try:
            value = fast.get(key)
        except Exception:
            value = None
        if value is not None:
            return as_float(value)
    return None


def first_present(mapping: Any, keys: list[str]) -> float | None:
    if mapping is None:
        return None
    for key in keys:
        value = None
        try:
            value = mapping.get(key)
        except Exception:
            pass
        if value is not None:
            return as_float(value)
    return None


def latest_timestamp(frame: pd.DataFrame) -> str | None:
    if frame.empty:
        return None
    ts = frame.index[-1]
    if getattr(ts, "tzinfo", None) is not None:
        return ts.tz_convert(timezone.utc).isoformat()
    return pd.Timestamp(ts, tz=timezone.utc).isoformat()


def snapshot_ticker(symbol: str, cfg: dict[str, Any]) -> TickerSnapshot:
    ticker = yf.Ticker(symbol)
    history_period = cfg.get("intraday_period", "5d")
    history_interval = cfg.get("intraday_interval", "5m")
    daily_period = cfg.get("history_period", "1y")
    daily_interval = cfg.get("history_interval", "1d")

    intraday = ticker.history(
        period=history_period,
        interval=history_interval,
        auto_adjust=False,
        actions=False,
    )
    daily = ticker.history(
        period=daily_period,
        interval=daily_interval,
        auto_adjust=False,
        actions=False,
    )

    if intraday.empty and daily.empty:
        raise RuntimeError(f"no price history returned for {symbol}")

    latest_frame = intraday if not intraday.empty else daily
    latest = latest_frame.iloc[-1]
    source_time = latest_timestamp(latest_frame)
    fetched_at = now_utc()

    technical_frame = daily if len(daily) >= 60 else latest_frame
    close = technical_frame["Close"].dropna()
    volume = technical_frame["Volume"].dropna()

    sma_20 = as_float(close.tail(20).mean()) if len(close) >= 20 else None
    sma_50 = as_float(close.tail(50).mean()) if len(close) >= 50 else None

    ema_12_series = close.ewm(span=12, adjust=False).mean() if len(close) else pd.Series(dtype=float)
    ema_26_series = close.ewm(span=26, adjust=False).mean() if len(close) else pd.Series(dtype=float)
    macd_series = ema_12_series - ema_26_series if len(close) else pd.Series(dtype=float)
    macd_signal_series = macd_series.ewm(span=9, adjust=False).mean() if len(macd_series) else pd.Series(dtype=float)
    rsi_series = rsi(close) if len(close) >= 15 else pd.Series(dtype=float)
    atr_series = atr(technical_frame) if len(technical_frame) >= 15 else pd.Series(dtype=float)
    returns = close.pct_change().tail(20)

    fast_info = {}
    try:
        fast_info = dict(getattr(ticker, "fast_info", {}) or {})
    except Exception:
        fast_info = {}

    market_price = market_price_from_fast_info(ticker) or as_float(latest.get("Close"))
    prev_close = first_present(fast_info, ["previousClose", "previous_close"])
    if prev_close is None and len(close) >= 2:
        prev_close = as_float(close.iloc[-2])

    day_low = first_present(fast_info, ["dayLow", "day_low"]) or as_float(latest.get("Low"))
    day_high = first_present(fast_info, ["dayHigh", "day_high"]) or as_float(latest.get("High"))
    change = market_price - prev_close if market_price is not None and prev_close is not None else None
    change_pct = (change / prev_close * 100) if change is not None and prev_close not in (None, 0) else None

    return TickerSnapshot(
        ticker=symbol,
        fetched_at=fetched_at,
        source_bar_time=source_time,
        market_price=market_price,
        open=as_float(latest.get("Open")),
        high=as_float(latest.get("High")),
        low=as_float(latest.get("Low")),
        close=as_float(latest.get("Close")),
        adj_close=as_float(latest.get("Adj Close")) if "Adj Close" in latest else None,
        volume=as_float(latest.get("Volume")),
        prev_close=prev_close,
        change=change,
        change_pct=change_pct,
        day_low=day_low,
        day_high=day_high,
        sma_20=sma_20,
        sma_50=sma_50,
        ema_12=as_float(ema_12_series.iloc[-1]) if len(ema_12_series) else None,
        ema_26=as_float(ema_26_series.iloc[-1]) if len(ema_26_series) else None,
        macd=as_float(macd_series.iloc[-1]) if len(macd_series) else None,
        macd_signal=as_float(macd_signal_series.iloc[-1]) if len(macd_signal_series) else None,
        macd_hist=as_float(macd_series.iloc[-1] - macd_signal_series.iloc[-1]) if len(macd_series) else None,
        rsi_14=as_float(rsi_series.iloc[-1]) if len(rsi_series) else None,
        atr_14=as_float(atr_series.iloc[-1]) if len(atr_series) else None,
        vol_20d=as_float(returns.std() * math.sqrt(252)) if len(returns.dropna()) else None,
        avg_volume_20d=as_float(volume.tail(20).mean()) if len(volume) >= 20 else None,
        market_cap=first_present(fast_info, ["marketCap", "market_cap"]),
        pe_ratio=first_present(fast_info, ["trailingPE", "trailing_pe"]),
    )


def insert_run(conn: sqlite3.Connection, started_at: str) -> int:
    cur = conn.execute(
        "INSERT INTO fetch_runs(started_at, status) VALUES (?, ?)",
        (started_at, "running"),
    )
    conn.commit()
    return int(cur.lastrowid)


def complete_run(conn: sqlite3.Connection, run_id: int, finished_at: str, status: str, note: str | None = None) -> None:
    conn.execute(
        "UPDATE fetch_runs SET finished_at = ?, status = ?, note = ? WHERE id = ?",
        (finished_at, status, note, run_id),
    )
    conn.commit()


def insert_snapshot(conn: sqlite3.Connection, run_id: int, snap: TickerSnapshot) -> None:
    conn.execute(
        """
        INSERT INTO ticker_observations(
            run_id, ticker, fetched_at, source_bar_time, source, market_price,
            open, high, low, close, adj_close, volume, prev_close, change, change_pct,
            day_low, day_high, sma_20, sma_50, ema_12, ema_26, macd, macd_signal, macd_hist,
            rsi_14, atr_14, vol_20d, avg_volume_20d, market_cap, pe_ratio
        ) VALUES (
            :run_id, :ticker, :fetched_at, :source_bar_time, 'yfinance', :market_price,
            :open, :high, :low, :close, :adj_close, :volume, :prev_close, :change, :change_pct,
            :day_low, :day_high, :sma_20, :sma_50, :ema_12, :ema_26, :macd, :macd_signal, :macd_hist,
            :rsi_14, :atr_14, :vol_20d, :avg_volume_20d, :market_cap, :pe_ratio
        )
        """,
        {"run_id": run_id, **snap.__dict__},
    )


def run_collection(config_path: Path) -> int:
    cfg = load_config(config_path)
    db_path = Path(cfg["database"]).expanduser()

    with connect_db(db_path) as conn:
        init_db(conn)
        started_at = now_utc()
        run_id = insert_run(conn, started_at)
        inserted = 0
        failures: list[str] = []
        try:
            for symbol in cfg["tickers"]:
                symbol = str(symbol).strip().upper()
                if not symbol:
                    continue
                try:
                    snapshot = snapshot_ticker(symbol, cfg)
                    insert_snapshot(conn, run_id, snapshot)
                    inserted += 1
                except Exception as exc:
                    failures.append(f"{symbol}: {exc}")
            status = "success" if not failures else "partial"
            note = f"inserted {inserted} ticker snapshot(s)"
            if failures:
                note += "; failures: " + " | ".join(failures)
            complete_run(conn, run_id, now_utc(), status, note)
            print(note)
            return 0 if not failures else 2
        except Exception as exc:
            complete_run(conn, run_id, now_utc(), "failed", str(exc))
            raise


def main() -> int:
    parser = argparse.ArgumentParser(description="Collect ticker data and store it in SQLite.")
    parser.add_argument(
        "--config",
        type=Path,
        default=DEFAULT_CONFIG,
        help="Path to config.json (default: docs/recipes/local-stock-tracker/config.json)",
    )
    args = parser.parse_args()
    return run_collection(args.config)


if __name__ == "__main__":
    raise SystemExit(main())
