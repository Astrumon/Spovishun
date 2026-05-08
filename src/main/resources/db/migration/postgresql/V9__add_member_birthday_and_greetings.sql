-- Add birthday column to members (MMDD encoded: month * 100 + day, e.g., 1225 = December 25)
ALTER TABLE members
    ADD COLUMN birth_md SMALLINT,
    ADD CONSTRAINT chk_members_birth_md_range
        CHECK (birth_md IS NULL OR (birth_md BETWEEN 101 AND 1231));

-- Index for daily scheduler scan
CREATE INDEX IF NOT EXISTS idx_members_birth_md
    ON members (birth_md)
    WHERE birth_md IS NOT NULL;

-- Idempotency log for sent greetings (per member, per calendar year)
CREATE TABLE IF NOT EXISTS birthday_greetings (
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    year      INTEGER NOT NULL,
    sent_at   TIMESTAMP NOT NULL,
    PRIMARY KEY (member_id, year)
);
