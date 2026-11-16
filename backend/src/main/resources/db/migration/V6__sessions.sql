-- Persist issued-session authority; legacy untracked bearers fail closed and require re-login.
CREATE TABLE active_sessions (
 jti UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 expires_at TIMESTAMPTZ NOT NULL,
 service_error BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sessions_user ON active_sessions(user_id);
CREATE INDEX idx_sessions_expiry ON active_sessions(expires_at);
