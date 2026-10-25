CREATE TABLE dlq_messages (
  id             BIGSERIAL PRIMARY KEY,
  original_topic TEXT      NOT NULL,
  message_key    TEXT,
  reason         TEXT      NOT NULL CHECK (reason IN ('exhausted-retries','schema-mismatch','business-rejected','unknown')),
  payload        TEXT      NOT NULL,
  replayed_at    TIMESTAMP WITH TIME ZONE,
  created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX ix_dlq_messages_reason
  ON dlq_messages (reason);

CREATE INDEX ix_dlq_messages_replayed
  ON dlq_messages (replayed_at);