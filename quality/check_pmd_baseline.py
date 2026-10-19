#!/usr/bin/env python3
"""Перевіряє, що кількість PMD-порушень у файлі та за правилом не зросла."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Iterable
from xml.etree import ElementTree as ET

FindingKey = tuple[str, str]


def normalize_source_path(path: str) -> str:
    """Повертає стабільний шлях від `src/`, незалежний від каталогу runner-а.

    @param path абсолютний або відносний шлях із PMD XML-звіту.
    @return шлях до файла від кореня каталогу `src/`.
    @raises ValueError якщо шлях не містить компонента `src/`.
    """
    normalized = re.sub(r"/+", "/", path.replace("\\", "/"))
    match = re.search(r"(?:^|/)src/(.+)$", normalized)
    if match is None:
        raise ValueError(f"Шлях PMD-звіту не містить каталогу src/: {path}")
    return "src/" + match.group(1)


def load_findings(report_path: Path) -> tuple[Counter[FindingKey], str | None]:
    """Зчитує PMD XML і рахує порушення за парою шлях до файла / правило.

    @param report_path шлях до XML-звіту, створеного Maven PMD Plugin.
    @return лічильник порушень і версія рушія PMD зі звіту, якщо вона задана.
    @raises OSError якщо файл звіту недоступний.
    @raises ET.ParseError якщо XML некоректний.
    @raises ValueError якщо звіт має неочікувану структуру.
    """
    root = ET.parse(report_path).getroot()
    if root.tag.split("}")[-1] != "pmd":
        raise ValueError(f"Очікувався кореневий елемент PMD у звіті: {report_path}")

    findings: Counter[FindingKey] = Counter()
    for file_element in root:
        if file_element.tag.split("}")[-1] != "file":
            continue
        source_path = normalize_source_path(file_element.attrib.get("name", ""))
        for violation in file_element:
            if violation.tag.split("}")[-1] != "violation":
                continue
            rule = violation.attrib.get("rule")
            if not rule:
                raise ValueError(f"PMD-порушення без назви правила у файлі {source_path}")
            findings[(source_path, rule)] += 1

    return findings, root.attrib.get("version")


def load_baseline(baseline_path: Path) -> tuple[Counter[FindingKey], dict[str, object]]:
    """Зчитує baseline і перевіряє структуру та невід'ємність лічильників.

    @param baseline_path шлях до JSON-файла з базовими лічильниками PMD.
    @return лічильник базових порушень і метадані baseline.
    @raises OSError якщо baseline недоступний.
    @raises ValueError якщо JSON має некоректну структуру.
    """
    data = json.loads(baseline_path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or not isinstance(data.get("findings"), dict):
        raise ValueError(f"Некоректна структура PMD baseline: {baseline_path}")

    baseline: Counter[FindingKey] = Counter()
    for source_path, rules in data["findings"].items():
        if not isinstance(source_path, str) or not source_path.startswith("src/"):
            raise ValueError(f"Некоректний шлях у PMD baseline: {source_path!r}")
        if not isinstance(rules, dict):
            raise ValueError(f"Очікувалася таблиця правил для {source_path}")
        for rule, count in rules.items():
            if not isinstance(rule, str) or not rule or isinstance(count, bool) or not isinstance(count, int) or count < 0:
                raise ValueError(f"Некоректний лічильник для {source_path} / {rule}: {count!r}")
            if count:
                baseline[(source_path, rule)] = count

    expected_total = data.get("totalFindings")
    if expected_total is not None and (
        isinstance(expected_total, bool)
        or not isinstance(expected_total, int)
        or expected_total != sum(baseline.values())
    ):
        raise ValueError("Сума лічильників не збігається з totalFindings у PMD baseline")

    expected_buckets = data.get("findingBuckets")
    if expected_buckets is not None and (
        isinstance(expected_buckets, bool)
        or not isinstance(expected_buckets, int)
        or expected_buckets != len(baseline)
    ):
        raise ValueError("Кількість пар «файл + правило» не збігається з findingBuckets у PMD baseline")

    return baseline, data


def find_increases(
    baseline: Counter[FindingKey], current: Counter[FindingKey]
) -> list[tuple[str, str, int, int]]:
    """Знаходить пари «файл + правило», для яких кількість порушень зросла.

    @param baseline базова кількість порушень.
    @param current кількість порушень у поточному звіті.
    @return відсортований список пар із базовою та поточною кількістю.
    """
    increases = [
        (path, rule, baseline_count, current[path, rule])
        for (path, rule), baseline_count in baseline.items()
        if current[path, rule] > baseline_count
    ]
    increases.extend(
        (path, rule, 0, current_count)
        for (path, rule), current_count in current.items()
        if (path, rule) not in baseline and current_count > 0
    )
    return sorted(set(increases))


def parse_arguments(arguments: Iterable[str] | None = None) -> argparse.Namespace:
    """Створює параметри CLI для перевірки PMD baseline.

    @param arguments явний список аргументів або `None` для системного CLI.
    @return розібрані аргументи командного рядка.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, default=Path("quality/pmd-baseline.json"))
    parser.add_argument("--report", type=Path, default=Path("target/pmd.xml"))
    return parser.parse_args(arguments)


def main(arguments: Iterable[str] | None = None) -> int:
    """Порівнює звіт із baseline та завершує процес помилкою при будь-якому прирості.

    @param arguments явний список аргументів або `None` для системного CLI.
    @return код завершення 0 за відсутності приросту, інакше 1.
    """
    options = parse_arguments(arguments)
    try:
        baseline, metadata = load_baseline(options.baseline)
        current, report_version = load_findings(options.report)
    except (OSError, json.JSONDecodeError, ET.ParseError, ValueError) as error:
        print(f"Помилка перевірки PMD baseline: {error}", file=sys.stderr)
        return 2

    baseline_version = metadata.get("pmdVersion")
    if baseline_version and report_version and baseline_version != report_version:
        print(
            "Версія PMD відрізняється від baseline: "
            f"baseline={baseline_version}, report={report_version}. "
            "Перегляньте зміни правил і оновіть baseline свідомо.",
            file=sys.stderr,
        )
        return 2

    increases = find_increases(baseline, current)
    baseline_total = sum(baseline.values())
    current_total = sum(current.values())
    if increases:
        print(
            f"PMD quality gate: виявлено приріст у {len(increases)} парах «файл + правило» "
            f"({baseline_total} базових, {current_total} поточних порушень):",
            file=sys.stderr,
        )
        for path, rule, previous, actual in increases:
            print(f"  {path} — {rule}: {previous} -> {actual} (+{actual - previous})", file=sys.stderr)
        print("Виправте нові порушення або перегляньте baseline окремим обґрунтованим комітом.", file=sys.stderr)
        return 1

    reductions = sum(max(0, baseline[key] - current[key]) for key in baseline)
    print(
        "PMD quality gate пройдено: "
        f"поточних порушень {current_total}, базових {baseline_total}; "
        f"приросту немає, зменшено {reductions}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
