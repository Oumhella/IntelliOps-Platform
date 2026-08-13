CREATE TABLE dim_products (
    enterprise_id BIGINT NOT NULL, product_id BIGINT NOT NULL, global_sku VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL, purchase_price NUMERIC(15,2) NOT NULL,
    sale_price NUMERIC(15,2) NOT NULL, synchronized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (enterprise_id, product_id), UNIQUE (enterprise_id, global_sku)
);
CREATE TABLE dim_stores (
    enterprise_id BIGINT NOT NULL, store_id BIGINT NOT NULL, name VARCHAR(255) NOT NULL,
    platform VARCHAR(50) NOT NULL, synchronized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (enterprise_id, store_id)
);
CREATE TABLE fact_orders (
    enterprise_id BIGINT NOT NULL, order_id BIGINT NOT NULL, reference VARCHAR(255) NOT NULL,
    store_id BIGINT, status VARCHAR(50) NOT NULL, payment_status VARCHAR(50) NOT NULL,
    total_amount NUMERIC(15,2) NOT NULL, source_updated_at TIMESTAMPTZ NOT NULL,
    synchronized_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (enterprise_id, order_id)
);
CREATE TABLE fact_order_lines (
    enterprise_id BIGINT NOT NULL, order_line_id BIGINT NOT NULL, order_id BIGINT NOT NULL,
    product_id BIGINT, quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(15,2) NOT NULL, synchronized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (enterprise_id, order_line_id),
    FOREIGN KEY (enterprise_id, order_id) REFERENCES fact_orders(enterprise_id, order_id) ON DELETE CASCADE
);
CREATE TABLE fact_inventory (
    enterprise_id BIGINT NOT NULL, inventory_id BIGINT NOT NULL, store_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL, available_quantity INTEGER NOT NULL, reserved_quantity INTEGER NOT NULL,
    alert_threshold INTEGER, synchronized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (enterprise_id, inventory_id),
    FOREIGN KEY (enterprise_id, store_id) REFERENCES dim_stores(enterprise_id, store_id),
    FOREIGN KEY (enterprise_id, product_id) REFERENCES dim_products(enterprise_id, product_id)
);
CREATE TABLE sync_checkpoints (
    source_name VARCHAR(100) PRIMARY KEY, last_source_updated_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_orders_tenant_date ON fact_orders(enterprise_id, source_updated_at);
CREATE INDEX idx_orders_tenant_payment ON fact_orders(enterprise_id, payment_status, status);
CREATE INDEX idx_lines_tenant_product ON fact_order_lines(enterprise_id, product_id);
CREATE INDEX idx_inventory_tenant_store ON fact_inventory(enterprise_id, store_id);
