# Sprint 1 supported questions

| Question | Status | Result shape |
|---|---|---|
| Paid revenue in a bounded period | Supported | one decimal value |
| Orders in a bounded period | Supported | one integer |
| Orders grouped by status | Supported | status and count |
| Top products by paid revenue | Supported | product and revenue |
| Top products by units sold | Supported | product and units |
| Available stock by store | Supported | store, product, quantity |
| Products below their alert threshold | Supported | store, product, quantity, threshold |
| Revenue comparison with previous period | Supported through two bounded queries | two values |
| Lead conversion rate | Deferred | lead projection required |
| Failed webhooks in a period | Deferred | integration projection required |

Every query transaction must set its authenticated tenant. A missing tenant context returns no
rows, and callers must never supply an enterprise ID as a question parameter.
