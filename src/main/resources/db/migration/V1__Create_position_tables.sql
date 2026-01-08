-- Position Service Database Schema
-- V1: Initial position tables

-- Position Table
CREATE TABLE position (
    position_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    account_id BIGINT NOT NULL,
    account_code VARCHAR(50) NOT NULL,
    instrument_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    quantity DECIMAL(18, 8) NOT NULL DEFAULT 0,
    avg_cost DECIMAL(18, 8) NOT NULL DEFAULT 0,
    cost_basis DECIMAL(18, 4) NOT NULL DEFAULT 0,
    realized_pnl DECIMAL(18, 4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    as_of_date DATE NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
    updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_position_account_instrument_date
        UNIQUE (account_id, instrument_id, as_of_date)
);

-- Indexes for position queries
CREATE INDEX idx_position_account_code ON position(account_code);
CREATE INDEX idx_position_symbol ON position(symbol);
CREATE INDEX idx_position_as_of_date ON position(as_of_date);
CREATE INDEX idx_position_account_date ON position(account_code, as_of_date);

-- Position History Table (for audit trail)
CREATE TABLE position_history (
    history_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    position_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    account_code VARCHAR(50) NOT NULL,
    instrument_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    previous_quantity DECIMAL(18, 8) NOT NULL,
    new_quantity DECIMAL(18, 8) NOT NULL,
    quantity_change DECIMAL(18, 8) NOT NULL,
    previous_avg_cost DECIMAL(18, 8) NOT NULL,
    new_avg_cost DECIMAL(18, 8) NOT NULL,
    realized_pnl DECIMAL(18, 4) NOT NULL DEFAULT 0,
    triggering_trade_id VARCHAR(50) NOT NULL,
    change_reason VARCHAR(50) NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT GETDATE(),

    CONSTRAINT fk_position_history_position
        FOREIGN KEY (position_id) REFERENCES position(position_id)
);

-- Index for position history queries
CREATE INDEX idx_position_history_position_id ON position_history(position_id);
CREATE INDEX idx_position_history_trade_id ON position_history(triggering_trade_id);
CREATE INDEX idx_position_history_created_at ON position_history(created_at);

-- Trigger to update updated_at timestamp
CREATE TRIGGER trg_position_updated_at
ON position
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    UPDATE position
    SET updated_at = GETDATE()
    FROM position p
    INNER JOIN inserted i ON p.position_id = i.position_id;
END;
GO
