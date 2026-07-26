#!/usr/bin/env python3
"""Scan repository content without emitting sensitive values."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path, PurePosixPath
from typing import Iterable


@dataclass(frozen=True)
class Finding:
    rule_id: str
    path: str
    line: int
    summary: str = "potential sensitive value"
    git_object: str | None = None


@dataclass(frozen=True)
class Rule:
    rule_id: str
    pattern: re.Pattern[str]


RULES = (
    Rule(
        "private-key",
        re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    ),
    Rule(
        "github-token",
        re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b"),
    ),
    Rule(
        "openai-style-token",
        re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b"),
    ),
    Rule(
        "jwt-token",
        re.compile(
            r"\beyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\."
            r"[A-Za-z0-9_-]{5,}\b"
        ),
    ),
    Rule(
        "phone-number",
        re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)"),
    ),
    Rule(
        "email-address",
        re.compile(
            r"(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b"
        ),
    ),
    Rule(
        "password-hash",
        re.compile(r"(?i)(?<![0-9a-f])[0-9a-f]{32,64}(?![0-9a-f])"),
    ),
    Rule(
        "sql-data-row",
        re.compile(r"(?im)^\s*INSERT\s+INTO\b"),
    ),
)

ASSIGNMENT_PATTERN = re.compile(
    r"""(?ix)
    \b(
        api[-_.]?key|apikey|secret(?:[-_.]?key)?|client[-_.]?secret|
        access[-_.]?key|auth[-_.]?code|password|passwd|token|
        private[-_.]?key
    )\b
    \s*[:=]\s*
    (?P<value>"[^"]*"|'[^']*'|[^\s,;#>]+)
    """
)

SAFE_VALUE_PATTERNS = (
    re.compile(r"^\$\{[A-Z][A-Z0-9_]*(?::[^}]*)?\}$"),
    re.compile(r"^\{\{[A-Za-z][A-Za-z0-9_]*\}\}$"),
    re.compile(r"^(?:change-me|example|placeholder|redacted|none|null)$", re.I),
)

EXCLUDED_PARTS = {
    ".git",
    ".idea",
    ".worktrees",
    "node_modules",
    "target",
    "dist",
    "build",
    "__pycache__",
}

MAX_TEXT_BYTES = 10 * 1024 * 1024


def _is_safe_value(value: str) -> bool:
    normalized = value.strip("\"'")
    if not normalized:
        return True
    return any(pattern.fullmatch(normalized) for pattern in SAFE_VALUE_PATTERNS)


def _inside_quoted_literal(line: str, position: int) -> bool:
    quote: str | None = None
    escaped = False
    for char in line[:position]:
        if escaped:
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if quote:
            if char == quote:
                quote = None
        elif char in {"'", '"'}:
            quote = char
    return quote is not None


def scan_text(
    path: Path | PurePosixPath,
    text: str,
    *,
    git_object: str | None = None,
) -> list[Finding]:
    findings: list[Finding] = []
    display_path = path.as_posix()
    configuration_suffixes = {
        ".env",
        ".ini",
        ".properties",
        ".toml",
        ".yaml",
        ".yml",
    }
    for line_number, line in enumerate(text.splitlines(), start=1):
        for match in ASSIGNMENT_PATTERN.finditer(line):
            if _inside_quoted_literal(line, match.start()):
                continue
            value = match.group("value")
            is_configuration = path.suffix.lower() in configuration_suffixes
            is_literal = value.startswith(("\"", "'"))
            if (
                (is_configuration or is_literal)
                and not _is_safe_value(value)
            ):
                findings.append(
                    Finding(
                        "generic-secret-assignment",
                        display_path,
                        line_number,
                        git_object=git_object,
                    )
                )
        for rule in RULES:
            if rule.rule_id == "sql-data-row" and path.suffix.lower() != ".sql":
                continue
            if (
                rule.rule_id == "password-hash"
                and path.suffix.lower()
                not in {".env", ".json", ".log", ".properties", ".sql", ".yaml", ".yml"}
            ):
                continue
            if rule.pattern.search(line):
                findings.append(
                    Finding(
                        rule.rule_id,
                        display_path,
                        line_number,
                        git_object=git_object,
                    )
                )
    return findings


def _is_excluded(path: Path) -> bool:
    normalized_parts = tuple(part.lower() for part in path.parts)
    if any(part in EXCLUDED_PARTS for part in normalized_parts):
        return True
    joined = "/".join(normalized_parts)
    return (
        "docs/superpowers/" in joined
        or "tests/scripts/security/" in joined
    )


def _read_text(path: Path) -> str | None:
    try:
        data = path.read_bytes()
    except OSError:
        return None
    if len(data) > MAX_TEXT_BYTES or b"\x00" in data:
        return None
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        return None


def scan_paths(paths: Iterable[Path]) -> list[Finding]:
    findings: list[Finding] = []
    for root in paths:
        candidates = root.rglob("*") if root.is_dir() else (root,)
        for candidate in candidates:
            if _is_excluded(candidate) or not candidate.is_file():
                continue
            text = _read_text(candidate)
            if text is None:
                continue
            display_path = (
                candidate.relative_to(root)
                if root.is_dir()
                else Path(candidate.name)
            )
            findings.extend(scan_text(display_path, text))
    return findings


def _run_git(repo: Path, *args: str, text: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True,
        check=True,
        text=text,
    )


def scan_git_refs(repo: Path) -> list[Finding]:
    object_listing = _run_git(repo, "rev-list", "--objects", "--all").stdout
    findings: list[Finding] = []
    seen: set[str] = set()
    for entry in object_listing.splitlines():
        object_id, separator, object_path = entry.partition(" ")
        if not separator or object_id in seen:
            continue
        seen.add(object_id)
        path = PurePosixPath(object_path)
        if _is_excluded(Path(*path.parts)):
            continue
        object_type = _run_git(repo, "cat-file", "-t", object_id).stdout.strip()
        if object_type != "blob":
            continue
        size = int(_run_git(repo, "cat-file", "-s", object_id).stdout.strip())
        if size > MAX_TEXT_BYTES:
            continue
        blob = _run_git(repo, "cat-file", "blob", object_id, text=False).stdout
        if b"\x00" in blob:
            continue
        try:
            text = blob.decode("utf-8")
        except UnicodeDecodeError:
            continue
        findings.extend(
            scan_text(path, text, git_object=object_id)
        )
    return findings


def _write_report(path: Path, findings: list[Finding]) -> None:
    report = {
        "finding_count": len(findings),
        "findings": [asdict(finding) for finding in findings],
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def _parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Scan paths or all reachable Git objects."
    )
    parser.add_argument("path", type=Path)
    parser.add_argument("--all-refs", action="store_true")
    parser.add_argument("--report", type=Path)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv or sys.argv[1:])
    try:
        findings = (
            scan_git_refs(args.path)
            if args.all_refs
            else scan_paths([args.path])
        )
        if args.report:
            _write_report(args.report, findings)
        counts: dict[str, int] = {}
        for finding in findings:
            counts[finding.rule_id] = counts.get(finding.rule_id, 0) + 1
        print(
            json.dumps(
                {
                    "finding_count": len(findings),
                    "rule_counts": counts,
                },
                sort_keys=True,
            )
        )
        return 1 if findings else 0
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        print(
            json.dumps(
                {
                    "error": type(error).__name__,
                    "message": "repository scan failed",
                }
            ),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
