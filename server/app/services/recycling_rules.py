from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import Any

from app.schemas.analysis import GuidanceSource, SortingStatus

_RULES_PATH = Path(__file__).resolve().parents[1] / "rules" / "data" / "gwanak.json"


@lru_cache(maxsize=1)
def load_rule_dataset() -> dict[str, Any]:
    with _RULES_PATH.open("r", encoding="utf-8-sig") as rules_file:
        dataset = json.load(rules_file)
    if not isinstance(dataset, dict) or not isinstance(dataset.get("rules"), list):
        raise ValueError("The Gwanak rule dataset has an invalid structure.")
    return dataset


def rule_context_json() -> str:
    return json.dumps(load_rule_dataset(), ensure_ascii=False, separators=(",", ":"))


def resolve_rule_ids(rule_ids: list[str]) -> tuple[bool, SortingStatus | None, list[GuidanceSource]]:
    dataset = load_rule_dataset()
    rules_by_id = {
        rule.get("id"): rule
        for rule in dataset["rules"]
        if isinstance(rule, dict) and isinstance(rule.get("id"), str)
    }
    selected_rules = [rules_by_id.get(rule_id) for rule_id in dict.fromkeys(rule_ids)]
    if not selected_rules or any(rule is None for rule in selected_rules):
        return False, None, []

    statuses = {rule.get("sorting_status") for rule in selected_rules if rule is not None}
    if len(statuses) != 1:
        return False, None, []
    try:
        status = SortingStatus(next(iter(statuses)))
    except ValueError:
        return False, None, []

    sources = sources_for_ids(
        source_id
        for rule in selected_rules
        if rule is not None
        for source_id in rule.get("source_ids", [])
    )
    return True, status, sources


def fallback_sources() -> list[GuidanceSource]:
    return sources_for_ids(load_rule_dataset().get("fallback", {}).get("source_ids", []))


def region_name() -> str:
    return str(load_rule_dataset()["region"]["display_name"])


def sources_for_ids(source_ids: Any) -> list[GuidanceSource]:
    dataset = load_rule_dataset()
    source_by_id = {
        source.get("id"): source
        for source in dataset.get("sources", [])
        if isinstance(source, dict) and isinstance(source.get("id"), str)
    }
    resolved: list[GuidanceSource] = []
    seen: set[str] = set()
    for source_id in source_ids:
        if source_id in seen:
            continue
        seen.add(source_id)
        source = source_by_id.get(source_id)
        if source is None:
            continue
        resolved.append(
            GuidanceSource(
                title=source["title"],
                url=source["url"],
                region=region_name(),
                checked_at=source.get("checked_at") or dataset.get("checked_at"),
            )
        )
    return resolved
