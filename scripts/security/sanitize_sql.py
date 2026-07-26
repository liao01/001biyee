#!/usr/bin/env python3
"""Reduce a MySQL dump to schema-only statements."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


class SqlSanitizationError(ValueError):
    pass


ALLOWED_PREFIXES = (
    "ALTER TABLE",
    "CREATE INDEX",
    "CREATE TABLE",
    "CREATE UNIQUE INDEX",
    "DROP TABLE IF EXISTS",
    "SET CHARACTER_SET_CLIENT",
    "SET FOREIGN_KEY_CHECKS",
    "SET NAMES",
)

REMOVED_PREFIXES = (
    "INSERT INTO",
    "LOCK TABLES",
    "UNLOCK TABLES",
)


def _strip_comments(text: str) -> str:
    result: list[str] = []
    index = 0
    quote: str | None = None
    while index < len(text):
        char = text[index]
        following = text[index + 1] if index + 1 < len(text) else ""
        if quote:
            result.append(char)
            if char == "\\" and index + 1 < len(text):
                index += 1
                result.append(text[index])
            elif char == quote:
                quote = None
            index += 1
            continue
        if char in {"'", '"', "`"}:
            quote = char
            result.append(char)
            index += 1
            continue
        if char == "/" and following == "*":
            end = text.find("*/", index + 2)
            if end < 0:
                raise SqlSanitizationError("unterminated block comment")
            result.append("\n")
            index = end + 2
            continue
        if char == "-" and following == "-":
            end = text.find("\n", index + 2)
            if end < 0:
                break
            result.append("\n")
            index = end + 1
            continue
        if char == "#":
            end = text.find("\n", index + 1)
            if end < 0:
                break
            result.append("\n")
            index = end + 1
            continue
        result.append(char)
        index += 1
    if quote:
        raise SqlSanitizationError("unterminated quoted value")
    return "".join(result)


def _split_statements(text: str) -> list[str]:
    statements: list[str] = []
    current: list[str] = []
    quote: str | None = None
    index = 0
    while index < len(text):
        char = text[index]
        current.append(char)
        if quote:
            if char == "\\" and index + 1 < len(text):
                index += 1
                current.append(text[index])
            elif char == quote:
                quote = None
        elif char in {"'", '"', "`"}:
            quote = char
        elif char == ";":
            statement = "".join(current).strip()
            if statement:
                statements.append(statement)
            current = []
        index += 1
    remainder = "".join(current).strip()
    if remainder:
        raise SqlSanitizationError("statement is missing a terminator")
    return statements


def sanitize_sql(text: str) -> str:
    statements = _split_statements(_strip_comments(text))
    kept: list[str] = []
    for statement in statements:
        normalized = " ".join(statement.split()).upper()
        if normalized.startswith(REMOVED_PREFIXES):
            continue
        if normalized.startswith(ALLOWED_PREFIXES):
            kept.append(statement)
            continue
        raise SqlSanitizationError("unclassified SQL statement")
    return "\n\n".join(kept).strip() + "\n"


def _parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Create a schema-only SQL file.")
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv or sys.argv[1:])
    try:
        result = sanitize_sql(args.input.read_text(encoding="utf-8"))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result, encoding="utf-8")
        return 0
    except (OSError, UnicodeError, SqlSanitizationError):
        print("SQL sanitization failed", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
