CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_name    VARCHAR(255) NOT NULL,
    quantity        INT NOT NULL,
    state           VARCHAR(50)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(255) NOT NULL,
    CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key)
);