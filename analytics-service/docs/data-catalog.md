# Analytics data catalog

The reporting database copies approved non-secret fields from operational services. It never
queries across operational databases at answer time.

| Reporting object | Source owner | Source objects | Tenant derivation |
|---|---|---|---|
| `fact_orders` | lead-service | `commandes JOIN leads` | `leads.enterprise_id` |
| `fact_order_lines` | lead-service | `lignes_commande JOIN commandes JOIN leads` | `leads.enterprise_id` |
| `dim_products` | stock-service | `produits` | `produits.enterprise_id` |
| `dim_stores` | stock-service | `boutiques` | `boutiques.enterprise_id` |
| `fact_inventory` | stock-service | inventory, product, store, replenishment rule | Product and store tenants must match |

Customer names, emails, phones, addresses, API keys and encrypted provider credentials are
excluded. Product IDs are logical cross-service references. The order source has no update
timestamp; `created_at` is temporarily the incremental checkpoint. Changes to an older order need
a reconciliation job until the source adds `updated_at` or publishes analytics events.
