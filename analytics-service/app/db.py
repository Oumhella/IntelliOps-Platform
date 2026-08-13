from collections.abc import Iterator
from contextlib import contextmanager

import psycopg
from psycopg import Connection
from psycopg.rows import dict_row


@contextmanager
def connection(url: str, *, readonly: bool = False) -> Iterator[Connection]:
    with psycopg.connect(url, row_factory=dict_row) as conn:
        if readonly:
            conn.execute("SET TRANSACTION READ ONLY")
        yield conn
