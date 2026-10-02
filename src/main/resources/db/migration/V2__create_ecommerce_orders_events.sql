CREATE TABLE IF NOT EXISTS pipeline_ecommerce_orders_events (
    order_id BIGINT,
    customer VARCHAR(255),
    amount NUMERIC(14,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);