from dataclasses import dataclass
from datetime import UTC, date, datetime, timedelta
from typing import Literal

PeriodType = Literal["WEEKLY", "MONTHLY"]


@dataclass(frozen=True)
class ReportPeriod:
    kind: PeriodType
    start: date
    end: date

    @property
    def previous(self) -> "ReportPeriod":
        if self.kind == "WEEKLY":
            return ReportPeriod(self.kind, self.start - timedelta(days=7), self.start)
        previous_end = self.start
        previous_start = (previous_end.replace(day=1) - timedelta(days=1)).replace(day=1)
        return ReportPeriod(self.kind, previous_start, previous_end)


def last_closed_period(kind: PeriodType, now: datetime | None = None) -> ReportPeriod:
    today = (now or datetime.now(UTC)).date()
    if kind == "WEEKLY":
        current_monday = today - timedelta(days=today.weekday())
        return ReportPeriod(kind, current_monday - timedelta(days=7), current_monday)
    current_month = today.replace(day=1)
    previous_month = (current_month - timedelta(days=1)).replace(day=1)
    return ReportPeriod(kind, previous_month, current_month)
