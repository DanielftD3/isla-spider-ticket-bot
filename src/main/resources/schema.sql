CREATE TABLE IF NOT EXISTS tickets (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    channel_id BIGINT UNIQUE,
    opener_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    game_name VARCHAR(100) NOT NULL,
    steam_id VARCHAR(100) NOT NULL,
    subject VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    accused_user_id BIGINT,
    evidence TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    claimed_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    closed_by BIGINT,
    close_reason TEXT,
    transcript_path TEXT
);

-- Migración compatible con instalaciones creadas antes de solicitar SteamID64.
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS steam_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_tickets_channel ON tickets(channel_id);
CREATE INDEX IF NOT EXISTS idx_tickets_opener_status ON tickets(guild_id, opener_id, status);

CREATE TABLE IF NOT EXISTS ticket_messages (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    message_id BIGINT NOT NULL UNIQUE,
    author_id BIGINT NOT NULL,
    author_name VARCHAR(150) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS ticket_attachments (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    message_id BIGINT,
    author_id BIGINT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    file_name TEXT,
    url TEXT NOT NULL,
    content_type VARCHAR(150),
    size_bytes BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS strikes (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    points INTEGER NOT NULL CHECK (points BETWEEN 1 AND 10),
    reason TEXT NOT NULL,
    evidence_url TEXT,
    source_ticket_id BIGINT REFERENCES tickets(id) ON DELETE SET NULL,
    moderator_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    revoked_by BIGINT,
    revoke_reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_strikes_user_active ON strikes(guild_id, user_id, active);

CREATE TABLE IF NOT EXISTS moderation_actions (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    moderator_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    reason TEXT NOT NULL,
    duration_seconds BIGINT,
    source_ticket_id BIGINT REFERENCES tickets(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ticket_feedback (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL UNIQUE REFERENCES tickets(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
