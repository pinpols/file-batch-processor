#!/usr/bin/env python3
import re
from pathlib import Path


MIGRATION_DIR = Path("src/main/resources/db/migration")
STATEMENT_SPLIT = re.compile(r";")
LINE_COMMENT = re.compile(r"--.*?$", re.MULTILINE)
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)


def normalized_statements(sql: str) -> list[str]:
    sql = BLOCK_COMMENT.sub("", sql)
    sql = LINE_COMMENT.sub("", sql)
    return [stmt.strip() for stmt in STATEMENT_SPLIT.split(sql) if stmt.strip()]


def unsafe_statement(stmt: str) -> bool:
    collapsed = re.sub(r"\s+", " ", stmt).strip().lower()
    if collapsed.startswith("update ") and " where " not in f" {collapsed} ":
        return True
    if collapsed.startswith("delete from ") and " where " not in f" {collapsed} ":
        return True
    return False


def main() -> int:
    failures: list[str] = []
    for path in sorted(MIGRATION_DIR.glob("V*.sql")):
        for stmt in normalized_statements(path.read_text(encoding="utf-8")):
            if unsafe_statement(stmt):
                preview = re.sub(r"\s+", " ", stmt).strip()[:160]
                failures.append(f"{path}: unsafe migration statement: {preview}")
    if failures:
        print("\n".join(failures))
        return 1
    print("Flyway migration safety check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
