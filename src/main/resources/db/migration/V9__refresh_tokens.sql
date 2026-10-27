CREATE TABLE refresh_tokens (
  id           BIGSERIAL PRIMARY KEY,
  token_hash   VARCHAR(64) NOT NULL UNIQUE,
  family_id    UUID NOT NULL,
  customer_id  BIGINT NOT NULL REFERENCES customers(id),
  device_id    VARCHAR(64),
  expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  revoked_at   TIMESTAMP WITH TIME ZONE,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_tokens_family
  ON refresh_tokens (family_id);

CREATE INDEX ix_refresh_tokens_customer
  ON refresh_tokens (customer_id);

CREATE INDEX ix_refresh_tokens_expires
  ON refresh_tokens (expires_at);