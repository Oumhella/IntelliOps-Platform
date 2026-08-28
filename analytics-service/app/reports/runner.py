import argparse
import logging

from app.config import get_settings
from app.reports.repository import list_enterprise_ids
from app.reports.service import generate_report

LOGGER = logging.getLogger("analytics.reports")


def main() -> None:
    logging.basicConfig(level=logging.INFO)
    parser = argparse.ArgumentParser(description="Generate closed-period analytics reports")
    parser.add_argument("--period", choices=("WEEKLY", "MONTHLY"), default="WEEKLY")
    args = parser.parse_args()
    settings = get_settings()
    generated = 0
    failed = 0
    for enterprise_id in list_enterprise_ids(settings):
        for locale in sorted(settings.report_locale_set or {"en"}):
            try:
                generate_report(
                    settings,
                    enterprise_id=enterprise_id,
                    user_id=None,
                    role="ROLE_ADMIN",
                    period_type=args.period,
                    locale=locale,
                )
                generated += 1
            except Exception:
                failed += 1
                LOGGER.exception(
                    "Report failed enterprise_id=%s locale=%s", enterprise_id, locale
                )
    LOGGER.info("Generated %s %s report(s)", generated, args.period.lower())
    if failed:
        raise RuntimeError(f"{failed} report(s) failed")


if __name__ == "__main__":
    main()
