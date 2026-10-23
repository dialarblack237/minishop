
CREATE TABLE products (
  id           BIGSERIAL PRIMARY KEY,
  name         TEXT           NOT NULL,
  price_cents  INTEGER        NOT NULL CHECK (price_cents >= 0),
  stock        INTEGER        NOT NULL CHECK (stock >= 0),
  created_at   TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
  updated_at   TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
  version      INTEGER        NOT NULL DEFAULT 0
);


CREATE TABLE customers (
  id             BIGSERIAL PRIMARY KEY,
  email          TEXT        NOT NULL UNIQUE,
  display_name   TEXT        NOT NULL,
  password_hash  TEXT        NOT NULL,
  role           TEXT        NOT NULL CHECK (role IN ('CUSTOMER','ADMIN')),
  created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  version        INTEGER     NOT NULL DEFAULT 0
);


CREATE TABLE orders (
  id           BIGSERIAL PRIMARY KEY,
  customer_id  BIGINT      NOT NULL REFERENCES customers(id),
  state        TEXT        NOT NULL
               CHECK (state IN ('CREATED','RESERVED','PAID','CONFIRMED','CANCELLED','REFUNDED')),
  total_cents  INTEGER     NOT NULL CHECK (total_cents >= 0),
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  version      INTEGER     NOT NULL DEFAULT 0
);

CREATE TABLE order_items (
  id               BIGSERIAL PRIMARY KEY,
  order_id         BIGINT  NOT NULL REFERENCES orders(id),
  product_id       BIGINT  NOT NULL REFERENCES products(id),
  unit_price_cents INTEGER NOT NULL CHECK (unit_price_cents >= 0),
  quantity         INTEGER NOT NULL CHECK (quantity > 0)
);

CREATE TABLE order_history (
  id           BIGSERIAL PRIMARY KEY,
  order_id     BIGINT      NOT NULL REFERENCES orders(id),
  from_state   TEXT,                             
  to_state     TEXT        NOT NULL,
  event        TEXT        NOT NULL,
  occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);


CREATE TABLE payments (
  id           BIGSERIAL PRIMARY KEY,
  order_id     BIGINT      NOT NULL REFERENCES orders(id),
  amount_cents INTEGER     NOT NULL CHECK (amount_cents >= 0),
  result       TEXT        NOT NULL CHECK (result IN ('APPROVED','DECLINED','SLOW')),
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);


CREATE INDEX ix_products_name_price
  ON products (name, price_cents);

CREATE INDEX ix_orders_customer_created
  ON orders (customer_id, created_at DESC);

CREATE INDEX ix_order_history_order_time
  ON order_history (order_id, occurred_at);