# Business metrics

All periods use `Africa/Casablanca` until the platform stores a tenant-specific timezone.

## Paid revenue

Sum `fact_orders.total_amount` where payment status is `PAID` and order status is neither
`ANNULEE` nor `RETOURNEE`. Sprint 1 assigns the period using order creation time.

## Order count

Count orders created in the requested half-open interval `[start, end)`, optionally by status.

## Product revenue and units sold

Sum `quantity * unit_price`, or `quantity`, from lines joined to eligible paid orders and
tenant-matched products. Cancelled and returned orders are excluded.

## Available and low stock

Available stock is `fact_inventory.available_quantity`. Stock is low when an alert threshold exists
and available quantity is less than or equal to it.

Lead conversion and integration failures remain unsupported until their projections are added.
