-- V1__init_schema.sql
-- Initial schema: groups, members, group_members

CREATE TABLE IF NOT EXISTS groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS members (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL UNIQUE,
    firstname VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_members_user_id ON members (user_id);

CREATE TABLE IF NOT EXISTS group_members (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES groups(id),
    member_id BIGINT NOT NULL REFERENCES members(id),
    joined_at TIMESTAMP NOT NULL
);