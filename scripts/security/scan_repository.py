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
    (?<![A-Z0-9_])["']?(
        api[-_.]?key|apikey|secret(?:[-_.]?key)?|client[-_.]?secret|
        access[-_.]?key|auth[-_.]?code|password|passwd|token|
        private[-_.]?key
    )\b["']?
    \s*[:=]\s*
    (?P<value>"[^"]*"|'[^']*'|[^\s,;#>]+)
    """
)

SAFE_VALUE_PATTERNS = (
    re.compile(r"^\$\{[A-Z][A-Z0-9_]*(?::[^}]*)?\}$"),
    re.compile(r"^\{\{[A-Za-z][A-Za-z0-9_]*\}\}$"),
    re.compile(r"^(?:change-me|example|placeholder|redacted|none|null)$", re.I),
)

PINNED_GITHUB_ACTION_PATTERN = re.compile(
    r"^\s*(?:-\s*)?uses:\s*"
    r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@"
    r"[0-9a-f]{40}(?:\s+#.*)?$",
    re.I,
)

KNOWN_NON_SECRET_FIXTURES = {
    (
        "business/src/test/java/com/jiawa/lyw/security/SensitiveLoggingTests.java",
        "sensitive-member-session-token",
    ),
    (
        "docs/superpowers/plans/2026-08-29-intelligent-travel-platform-phase-0-foundation.md",
        "Secret123",
    ),
}

NON_SENSITIVE_REFERENCE_SEED_PATTERN = re.compile(
    r"(?i)^\s*INSERT\s+INTO\s+`?post_category`?\b"
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
RESERVED_EXAMPLE_EMAIL_DOMAINS = {
    "example.com",
    "example.org",
    "example.net",
    "example.invalid",
}
LOCAL_SECRET_CONFIGS = {
    "business/src/main/resources/application.properties",
    "business/src/main/resources/application.yml",
}


def _is_safe_value(value: str) -> bool:
    normalized = value.strip("\"'")
    if not normalized:
        return True
    return any(pattern.fullmatch(normalized) for pattern in SAFE_VALUE_PATTERNS)


def _is_known_non_secret_fixture(path: Path | PurePosixPath, value: str) -> bool:
    normalized = value.strip("\"'")
    return (path.as_posix(), normalized) in KNOWN_NON_SECRET_FIXTURES


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


def _is_pinned_github_action(path: Path | PurePosixPath, line: str) -> bool:
    parts = tuple(part.lower() for part in path.parts)
    return (
        len(parts) >= 3
        and parts[-3:-1] == (".github", "workflows")
        and path.suffix.lower() in {".yaml", ".yml"}
        and PINNED_GITHUB_ACTION_PATTERN.fullmatch(line) is not None
    )


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
        ".json",
        ".properties",
        ".toml",
        ".yaml",
        ".yml",
    }
    is_configuration = path.suffix.lower() in configuration_suffixes
    for line_number, line in enumerate(text.splitlines(), start=1):
        for match in ASSIGNMENT_PATTERN.finditer(line):
            if line[match.start()] in {"'", '"'} and not is_configuration:
                continue
            if _inside_quoted_literal(line, match.start()):
                continue
            value = match.group("value")
            is_literal = value.startswith(("\"", "'"))
            if (
                (is_configuration or is_literal)
                and not _is_safe_value(value)
                and not _is_known_non_secret_fixture(path, value)
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
            if (
                rule.rule_id == "email-address"
                and path.name.lower()
                in {"package-lock.json", "npm-shrinkwrap.json"}
            ):
                continue
            if rule.rule_id == "email-address":
                email_matches = rule.pattern.findall(line)
                if email_matches and all(
                    match.rsplit("@", 1)[-1].lower()
                    in RESERVED_EXAMPLE_EMAIL_DOMAINS
                    for match in email_matches
                ):
                    continue
            if rule.rule_id == "sql-data-row" and path.suffix.lower() != ".sql":
                continue
            if (
                rule.rule_id == "sql-data-row"
                and NON_SENSITIVE_REFERENCE_SEED_PATTERN.search(line)
            ):
                continue
            if (
                rule.rule_id == "password-hash"
                and path.suffix.lower()
                not in {".env", ".json", ".log", ".properties", ".sql", ".yaml", ".yml"}
            ):
                continue
            if (
                rule.rule_id == "password-hash"
                and _is_pinned_github_action(path, line)
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
    return any(part in EXCLUDED_PARTS for part in normalized_parts)


def _is_local_secret_config(path: Path, root: Path) -> bool:
    try:
        relative = path.relative_to(root)
    except ValueError:
        return False
    return relative.as_posix().lower() in LOCAL_SECRET_CONFIGS


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
            if (
                _is_excluded(candidate)
                or _is_local_secret_config(candidate, root)
                or not candidate.is_file()
            ):
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


def scan_git_refs(
    repo: Path,
    *,
    refs: Iterable[str] | None = None,
) -> list[Finding]:
    selected_refs = tuple(refs or ())
    revision_args = selected_refs if selected_refs else ("--all",)
    commits = _run_git(repo, "rev-list", *revision_args).stdout.splitlines()
    candidate_blobs: dict[str, PurePosixPath] = {}
    for commit in commits:
        tree = _run_git(
            repo,
            "ls-tree",
            "-r",
            "--full-tree",
            "-z",
            commit,
            text=False,
        ).stdout
        for raw_entry in tree.split(b"\0"):
            metadata, separator, raw_path = raw_entry.partition(b"\t")
            fields = metadata.split()
            if not separator or len(fields) != 3 or fields[1] != b"blob":
                continue
            object_id = fields[2].decode("ascii")
            path = PurePosixPath(raw_path.decode("utf-8", errors="surrogateescape"))
            if _is_excluded(Path(*path.parts)):
                continue
            candidate_blobs.setdefault(object_id, path)

    findings: list[Finding] = []
    for object_id, path in candidate_blobs.items():
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
    history_scope = parser.add_mutually_exclusive_group()
    history_scope.add_argument("--all-refs", action="store_true")
    history_scope.add_argument("--ref", action="append", dest="refs")
    parser.add_argument("--report", type=Path)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv or sys.argv[1:])
    try:
        findings = (
            scan_git_refs(args.path, refs=args.refs)
            if args.all_refs or args.refs
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
