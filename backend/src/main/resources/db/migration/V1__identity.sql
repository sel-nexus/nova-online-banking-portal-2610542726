CREATE TABLE users (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), username varchar(50) UNIQUE NOT NULL,
 password_hash varchar(255) NOT NULL, full_name varchar(100) NOT NULL,
 role varchar(30) NOT NULL DEFAULT 'ROLE_CUSTOMER' CHECK (role IN ('ROLE_CUSTOMER','ROLE_ADMIN')),
 failed_attempts integer NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 3),
 locked boolean NOT NULL DEFAULT false, last_login timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE token_revocations (jti uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, expires_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX idx_revocations_user ON token_revocations(user_id);
CREATE INDEX idx_revocations_expiry ON token_revocations(expires_at);
CREATE TABLE audit_events (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid REFERENCES users(id) ON DELETE SET NULL, action varchar(50) NOT NULL, details text NOT NULL DEFAULT '', created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX idx_audit_user ON audit_events(user_id);
CREATE INDEX idx_audit_date ON audit_events(created_at DESC);
