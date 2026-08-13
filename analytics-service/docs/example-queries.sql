-- Set from authenticated context inside the transaction.
SELECT set_config('app.enterprise_id', :enterprise_id::text, true);

SELECT COALESCE(SUM(total_amount), 0) AS paid_revenue
FROM fact_orders
WHERE payment_status = 'PAID' AND status NOT IN ('ANNULEE', 'RETOURNEE')
  AND source_updated_at >= :start_date AND source_updated_at < :end_date;

SELECT p.name, SUM(l.quantity * l.unit_price) AS revenue
FROM fact_order_lines l
JOIN fact_orders o USING (enterprise_id, order_id)
JOIN dim_products p USING (enterprise_id, product_id)
WHERE o.payment_status = 'PAID' AND o.status NOT IN ('ANNULEE', 'RETOURNEE')
  AND o.source_updated_at >= :start_date AND o.source_updated_at < :end_date
GROUP BY p.product_id, p.name ORDER BY revenue DESC LIMIT 5;

SELECT s.name AS store, p.name AS product, i.available_quantity, i.alert_threshold
FROM fact_inventory i
JOIN dim_stores s USING (enterprise_id, store_id)
JOIN dim_products p USING (enterprise_id, product_id)
WHERE i.alert_threshold IS NOT NULL AND i.available_quantity <= i.alert_threshold
ORDER BY i.available_quantity, p.name LIMIT 50;
