import re

from sqlglot import exp, parse

ALLOWED_TABLES = {"fact_orders", "fact_order_lines", "dim_products", "dim_stores", "fact_inventory"}
FORBIDDEN = (exp.Insert, exp.Update, exp.Delete, exp.Create, exp.Drop, exp.Alter, exp.Command)


def validate_sql(sql: str, max_rows: int) -> str:
    statements = parse(sql, read="postgres")
    if len(statements) != 1:
        raise ValueError("Exactly one SQL statement is required")
    statement = statements[0]
    if not isinstance(statement, (exp.Select, exp.Union)) and not statement.find(exp.Select):
        raise ValueError("Only read-only SELECT queries are allowed")
    if any(statement.find(node_type) for node_type in FORBIDDEN):
        raise ValueError("Data-changing SQL is forbidden")
    tables = {table.name.lower() for table in statement.find_all(exp.Table)}
    if not tables or not tables <= ALLOWED_TABLES:
        raise ValueError("The query references an unapproved reporting table")
    if re.search(r"\benterprise_id\s*=", sql, re.IGNORECASE):
        raise ValueError("Tenant predicates are database-managed")
    limit = statement.args.get("limit")
    if limit is None:
        statement.set("limit", exp.Limit(expression=exp.Literal.number(max_rows)))
    else:
        value = limit.expression
        if not isinstance(value, exp.Literal) or not value.is_int or int(value.this) > max_rows:
            raise ValueError(f"LIMIT must be a literal no greater than {max_rows}")
    return statement.sql(dialect="postgres")
