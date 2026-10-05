CREATE TABLE market_data_candles (
    id UUID PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    timeframe VARCHAR(10) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    open NUMERIC(20,4) NOT NULL,
    high NUMERIC(20,4) NOT NULL,
    low NUMERIC(20,4) NOT NULL,
    close NUMERIC(20,4) NOT NULL,
    volume NUMERIC(24,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_market_data_candles_symbol_timeframe_timestamp
        UNIQUE (symbol, timeframe, timestamp)
);
