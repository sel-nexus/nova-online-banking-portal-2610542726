CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_number VARCHAR(20) NOT NULL UNIQUE CHECK (length(account_number) >= 4),
    masked_number VARCHAR(20) NOT NULL,
    account_type VARCHAR(30) NOT NULL CHECK (account_type IN ('DEPOSIT','CREDIT_CARD','LOAN')),
    account_name VARCHAR(100) NOT NULL,
    available_balance NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    current_balance NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    credit_limit NUMERIC(15,2) CHECK (credit_limit >= 0),
    interest_rate NUMERIC(5,2) CHECK (interest_rate >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','CLOSED')),
    last_statement_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT card_requires_limit CHECK (account_type <> 'CREDIT_CARD' OR credit_limit IS NOT NULL)
);
CREATE INDEX idx_accounts_user ON accounts(user_id);
