
INSERT INTO customers (email, display_name, password_hash, role)
SELECT 'admin@minishop.local', 'Admin', '$2a$10$PLACEHOLDER_REPLACE_IN_MODULE_9', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE email = 'admin@minishop.local');


INSERT INTO products (id, name, price_cents, stock)
SELECT 1, 'Espresso beans 250g', 1200, 100
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 1);

INSERT INTO products (id, name, price_cents, stock)
SELECT 2, 'Filter paper 100pcs', 400, 100
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 2);

INSERT INTO products (id, name, price_cents, stock)
SELECT 3, 'Coffee grinder', 3500, 20
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 3);

ALTER TABLE products ALTER COLUMN id RESTART WITH 4;