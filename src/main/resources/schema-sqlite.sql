CREATE TABLE IF NOT EXISTS tickets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id BIGINT NOT NULL,
    channel_id BIGINT UNIQUE,
    opener_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    game_name VARCHAR(100) NOT NULL,
    steam_id VARCHAR(100),
    subject VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    accused_user_id BIGINT,
    evidence TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    claimed_by BIGINT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TEXT,
    closed_by BIGINT,
    close_reason TEXT,
    transcript_path TEXT
);

CREATE INDEX IF NOT EXISTS idx_tickets_channel ON tickets(channel_id);
CREATE INDEX IF NOT EXISTS idx_tickets_opener_status ON tickets(guild_id, opener_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tickets_open_per_type
    ON tickets(guild_id, opener_id, type) WHERE status='OPEN';

CREATE TABLE IF NOT EXISTS ticket_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    message_id BIGINT NOT NULL UNIQUE,
    author_id BIGINT NOT NULL,
    author_name VARCHAR(150) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ticket_attachments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    message_id BIGINT,
    author_id BIGINT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    file_name TEXT,
    url TEXT NOT NULL,
    content_type VARCHAR(150),
    size_bytes BIGINT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ticket_messages_ticket ON ticket_messages(ticket_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ticket_attachments_ticket ON ticket_attachments(ticket_id, message_id, created_at);

CREATE TABLE IF NOT EXISTS strikes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    points INTEGER NOT NULL CHECK (points BETWEEN 1 AND 10),
    reason TEXT NOT NULL,
    evidence_url TEXT,
    source_ticket_id BIGINT REFERENCES tickets(id) ON DELETE SET NULL,
    moderator_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TEXT,
    revoked_by BIGINT,
    revoke_reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_strikes_user_active ON strikes(guild_id, user_id, active);

CREATE TABLE IF NOT EXISTS moderation_actions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    moderator_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    reason TEXT NOT NULL,
    duration_seconds BIGINT,
    source_ticket_id BIGINT REFERENCES tickets(id) ON DELETE SET NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ticket_feedback (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id BIGINT NOT NULL UNIQUE REFERENCES tickets(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    attended_by BIGINT,
    rating INTEGER CHECK (rating BETWEEN 1 AND 5),
    attention_rating INTEGER CHECK (attention_rating BETWEEN 1 AND 10),
    solution_rating INTEGER CHECK (solution_rating BETWEEN 1 AND 10),
    comment TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

