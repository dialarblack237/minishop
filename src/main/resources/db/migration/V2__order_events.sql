
CREATE TABLE outbox (
  id           BIGSERIAL PRIMARY KEY,
  event_id     UUID NOT NULL UNIQUE,
  topic        TEXT NOT NULL,
  payload      TEXT NOT NULL,
  published_at TIMESTAMP WITH TIME ZONE,
  attempts     INTEGER NOT NULL DEFAULT 0,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX ix_outbox_unsent
  ON outbox (created_at);

CREATE TABLE idempotency_keys (
  id            BIGSERIAL PRIMARY KEY,
  idem_key      TEXT NOT NULL UNIQUE,
  response_json TEXT NOT NULL,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);