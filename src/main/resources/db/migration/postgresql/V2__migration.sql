ALTER TABLE members ADD joined_at TIMESTAMP NULL;
DROP INDEX IF EXISTS idx_group_members_member_id;
ALTER TABLE IF EXISTS group_members DROP CONSTRAINT IF EXISTS group_members_group_id_member_id_key;
DROP INDEX IF EXISTS idx_group_members_group_id;
