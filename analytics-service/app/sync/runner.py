import argparse
import logging
import time

from app.config import Settings, get_settings
from app.db import connection
from app.migrate import apply_migrations
from app.sync.extractors import (
    extract_deliveries,
    extract_leads,
    extract_order_lines,
    extract_orders,
    extract_stock,
)
from app.sync.repository import ReportingRepository

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
LOGGER = logging.getLogger(__name__)


def sync_once(settings: Settings) -> dict[str, int]:
    target_url = settings.sync_analytics_database_url or settings.analytics_database_url
    with connection(target_url) as conn:
        repository = ReportingRepository(conn)
        checkpoint = repository.checkpoint("lead-orders")
    orders = list(extract_orders(settings.lead_database_url, checkpoint, settings.sync_batch_size))
    lines = list(
        extract_order_lines(settings.lead_database_url, [row["order_id"] for row in orders])
    )
    leads = list(extract_leads(settings.lead_database_url))
    products, stores, inventory = extract_stock(settings.stock_database_url)
    deliveries = (
        list(extract_deliveries(settings.delivery_database_url))
        if settings.delivery_database_url
        else []
    )
    with connection(target_url) as conn:
        repository = ReportingRepository(conn)
        counts = {
            "leads": repository.upsert("dim_leads", leads, ("enterprise_id", "lead_id")),
            "orders": repository.upsert("fact_orders", orders, ("enterprise_id", "order_id")),
            "order_lines": repository.upsert(
                "fact_order_lines", lines, ("enterprise_id", "order_line_id")
            ),
            "products": repository.upsert(
                "dim_products", products, ("enterprise_id", "product_id")
            ),
            "stores": repository.upsert("dim_stores", stores, ("enterprise_id", "store_id")),
            "inventory": repository.upsert(
                "fact_inventory", inventory, ("enterprise_id", "inventory_id")
            ),
            "deliveries": repository.upsert(
                "fact_deliveries", deliveries, ("enterprise_id", "delivery_id")
            ),
        }
        if orders:
            repository.save_checkpoint(
                "lead-orders", max(row["source_updated_at"] for row in orders)
            )
    return counts


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--once", action="store_true")
    args = parser.parse_args()
    settings = get_settings()
    if settings.migration_database_url:
        apply_migrations(
            settings.migration_database_url,
            query_password=settings.analytics_query_password,
            sync_password=settings.analytics_sync_password,
        )
    if args.once:
        LOGGER.info("Analytics synchronization completed: %s", sync_once(settings))
        return
    while True:
        LOGGER.info("Analytics synchronization completed: %s", sync_once(settings))
        time.sleep(settings.sync_interval_seconds)


if __name__ == "__main__":
    main()
