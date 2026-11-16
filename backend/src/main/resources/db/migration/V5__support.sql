-- Keep reference fixtures in the idempotent application seeder, not Flyway history.
CREATE SEQUENCE inquiry_reference_seq START WITH 99214;
CREATE TABLE customer_inquiries (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 topic VARCHAR(50) NOT NULL CHECK (topic IN ('LOGIN','ACCOUNTS','TRANSACTIONS','CARDS','LOANS','OFFERS','GENERAL')),
 subject VARCHAR(150) NOT NULL CHECK (length(btrim(subject)) BETWEEN 1 AND 150),
 message TEXT NOT NULL CHECK (length(btrim(message)) BETWEEN 1 AND 5000),
 reference_number VARCHAR(50) NOT NULL UNIQUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_inquiries_user ON customer_inquiries(user_id);
CREATE TABLE reference_content (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 content_key VARCHAR(100) NOT NULL UNIQUE,
 content JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
