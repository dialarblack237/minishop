CREATE TABLE reservations (
  id           BIGSERIAL PRIMARY KEY,
  order_id     BIGINT NOT NULL REFERENCES orders(id),
  product_id   BIGINT NOT NULL REFERENCES products(id),
  quantity     INTEGER NOT NULL CHECK (quantity > 0),
  reserved_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT uq_reservations_order_product UNIQUE (order_id, product_id)
);

CREATE INDEX ix_reservations_order
  ON reservations (order_id);