PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS fetch_runs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  started_at TEXT NOT NULL,
  finished_at TEXT,
  status TEXT NOT NULL,
  note TEXT
);

CREATE TABLE IF NOT EXISTS ticker_observations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  run_id INTEGER NOT NULL REFERENCES fetch_runs(id) ON DELETE CASCADE,
  ticker TEXT NOT NULL,
  fetched_at TEXT NOT NULL,
  source_bar_time TEXT,
  source TEXT NOT NULL DEFAULT 'yfinance',
  market_price REAL,
  open REAL,
  high REAL,
  low REAL,
  close REAL,
  adj_close REAL,
  volume REAL,
  prev_close REAL,
  change REAL,
  change_pct REAL,
  day_low REAL,
  day_high REAL,
  sma_20 REAL,
  sma_50 REAL,
  ema_12 REAL,
  ema_26 REAL,
  macd REAL,
  macd_signal REAL,
  macd_hist REAL,
  rsi_14 REAL,
  atr_14 REAL,
  vol_20d REAL,
  avg_volume_20d REAL,
  market_cap REAL,
  pe_ratio REAL
);

CREATE INDEX IF NOT EXISTS idx_ticker_observations_ticker_time
  ON ticker_observations(ticker, fetched_at DESC);

CREATE INDEX IF NOT EXISTS idx_ticker_observations_run_id
  ON ticker_observations(run_id);
