-- Preserve catalogue fixtures separately from schema evolution.
CREATE TABLE promotions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 offer_key VARCHAR(50) NOT NULL UNIQUE,
 title VARCHAR(150) NOT NULL,
 category VARCHAR(50) NOT NULL CHECK (category IN ('SAVINGS','CARDS','LOANS')),
 target_segment VARCHAR(50) NOT NULL CHECK (target_segment IN ('MIGUEL','ROSA','ALL')),
 benefit_summary TEXT NOT NULL,
 terms_and_conditions TEXT NOT NULL,
 eligibility TEXT NOT NULL,
 valid_until DATE NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_promotions_segment_valid ON promotions(target_segment,valid_until) WHERE active;
CREATE TABLE user_offer_interests (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 promotion_id UUID NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
 registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(user_id,promotion_id)
);
CREATE INDEX idx_offer_interests_promotion ON user_offer_interests(promotion_id);
