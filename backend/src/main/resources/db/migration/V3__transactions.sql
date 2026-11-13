CREATE TABLE transactions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 reference_code VARCHAR(50) NOT NULL UNIQUE,
 posted_at TIMESTAMPTZ NOT NULL,
 description VARCHAR(255) NOT NULL,
 category VARCHAR(50) NOT NULL CHECK (category IN ('INCOME','GROCERIES','SAVINGS','DINING','TRANSPORT','GENERAL')),
 direction VARCHAR(10) NOT NULL CHECK (direction IN ('CREDIT','DEBIT')),
 status VARCHAR(20) NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED','PENDING','DECLINED')),
 amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_transactions_account_date ON transactions(account_id, posted_at DESC, id DESC);
