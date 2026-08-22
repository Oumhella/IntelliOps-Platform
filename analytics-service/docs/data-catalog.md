# Analytics data catalog

The reporting database copies approved non-secret fields from operational services. It never
queries across operational databases at answer time.

| Reporting object | Source owner | Source objects | Tenant derivation |
|---|---|---|---|
| `fact_orders` | lead-service | `commandes JOIN leads` | `leads.enterprise_id` |
| `fact_order_lines` | lead-service | `lignes_commande JOIN commandes JOIN leads` | `leads.enterprise_id` |
| `dim_leads` | lead-service | `leads` (no customer PII) | `leads.enterprise_id` |
| `dim_products` | stock-service | `produits` | `produits.enterprise_id` |
| `dim_stores` | stock-service | `boutiques` | `boutiques.enterprise_id` |
| `fact_inventory` | stock-service | inventory, product, store, replenishment rule | Product and store tenants must match |
| `fact_deliveries` | delivery-service | `livraisons` (no recipient PII) | `livraisons.enterprise_id` |

Customer names, emails, phones, addresses, API keys and encrypted provider credentials are
excluded. Product IDs are logical cross-service references. The order source has no update
timestamp; `created_at` is temporarily the incremental checkpoint. Changes to an older order need
a reconciliation job until the source adds `updated_at` or publishes analytics events.

`assigned_csm_id` is retained on lead and order facts solely to enforce CSM ownership. Courier IDs
are retained for delivery ownership and future personal courier metrics. They are internal user
identifiers, not customer data.
