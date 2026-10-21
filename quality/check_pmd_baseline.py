#!/usr/bin/env python3
"""Перевіряє нові PMD-знахідки високого пріоритету відносно затвердженого baseline."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Iterable
from xml.etree import ElementTree as ET

FindingKey = tuple[str, str, str]
FINGERPRINT_PATTERN = re.compile(r"^[0-9a-f]{64}$")
VALID_PRIORITIES = frozenset(range(1, 6))
DEFAULT_MAX_PRIORITY = 2


def normalize_source_path(path: str) -> str:
    """Повертає стабільний шлях від `src/`, незалежний від runner-а.

    @param path абсолютний або відносний шлях із PMD XML-звіту.
    @return шлях до файла від кореня каталогу `src/`.
    @raises ValueError якщо шлях не містить компонента `src/`.
    """
    normalized = re.sub(r"/+", "/", path.replace("\\", "/"))
    match = re.search(r"(?:^|/)src/(.+)$", normalized)
    if match is None:
        raise ValueError(f"Шлях PMD-звіту не містить каталогу src/: {path}")
    return "src/" + match.group(1)


def finding_fingerprint(violation: ET.Element) -> str:
    """Обчислює стабільний відбиток конкретної PMD-знахідки.

    Номери рядків навмисно не входять до відбитка: вставлення коду вище за
    знайдений фрагмент не повинно вважатися новим порушенням. Водночас правило,
    повідомлення, клас, метод, набір правил і координати стовпців відрізняють
    різні проблеми всередині того самого файла та правила.

    @param violation XML-елемент одного PMD-порушення.
    @return SHA-256 від канонічного опису знахідки.
    @raises ValueError якщо звіт не містить достатнього опису правила.
    """
    rule = violation.attrib.get("rule", "").strip()
    if not rule:
        raise ValueError("PMD-порушення не містить назви правила")

    description = " ".join((violation.text or "").split())
    identity = {
        "rule": rule,
        "ruleset": violation.attrib.get("ruleset", "").strip(),
        "priority": violation.attrib.get("priority", "").strip(),
        "package": violation.attrib.get("package", "").strip(),
        "class": violation.attrib.get("class", "").strip(),
        "method": violation.attrib.get("method", "").strip(),
        "begincolumn": violation.attrib.get("begincolumn", "").strip(),
        "endcolumn": violation.attrib.get("endcolumn", "").strip(),
        "description": description,
    }
    canonical = json.dumps(identity, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def load_findings(
    report_path: Path,
    max_priority: int | None = None,
) -> tuple[Counter[FindingKey], str | None]:
    """Зчитує PMD XML та рахує відбитки всіх або лише заданих за пріоритетом знахідок.

    Нижче числове значення пріоритету PMD означає менш критичну знахідку.
    Фільтрація використовується лише для quality gate: повний XML-звіт зберігається
    окремо й залишається доступним як CI artifact.

    @param report_path шлях до XML-звіту Maven PMD Plugin.
    @param max_priority максимальний номер пріоритету, який потрібно включити; `None` означає всі.
    @return лічильник за ключем «шлях + правило + відбиток» і версія PMD.
    @raises OSError якщо звіт недоступний.
    @raises ET.ParseError якщо XML некоректний.
    @raises ValueError якщо звіт має неочікувану структуру або некоректний пріоритет.
    """
    if max_priority is not None and max_priority not in VALID_PRIORITIES:
        raise ValueError(f"Некоректний максимальний пріоритет PMD: {max_priority}; допустимо 1–5")

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
            rule = violation.attrib.get("rule", "").strip()
            if not rule:
                raise ValueError(f"PMD-порушення без назви правила у файлі {source_path}")
            if max_priority is not None and _priority(violation, source_path) > max_priority:
                continue
            findings[(source_path, rule, finding_fingerprint(violation))] += 1

    return findings, root.attrib.get("version")


def _priority(violation: ET.Element, source_path: str) -> int:
    """Перевіряє пріоритет знахідки, щоб помилкові дані не обійшли quality gate.

    @param violation XML-елемент одного PMD-порушення.
    @param source_path нормалізований шлях до файла зі звіту.
    @return числовий пріоритет PMD від 1 до 5.
    @raises ValueError якщо пріоритет відсутній або виходить за межі PMD.
    """
    raw_priority = violation.attrib.get("priority", "").strip()
    try:
        priority = int(raw_priority)
    except ValueError as error:
        raise ValueError(f"Некоректний пріоритет PMD у файлі {source_path}: {raw_priority!r}") from error
    if priority not in VALID_PRIORITIES:
        raise ValueError(f"Пріоритет PMD має бути в діапазоні 1–5 у файлі {source_path}: {priority}")
    return priority


def load_baseline(baseline_path: Path) -> tuple[Counter[FindingKey], dict[str, object]]:
    """Зчитує baseline з відбитками й перевіряє структуру та контрольні суми.

    @param baseline_path шлях до JSON-файла з базовими PMD-відбитками.
    @return лічильник відбитків і метадані baseline.
    @raises OSError якщо baseline недоступний.
    @raises ValueError якщо JSON має некоректну структуру або контрольні суми.
    """
    data = json.loads(baseline_path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or not isinstance(data.get("findings"), dict):
        raise ValueError(f"Некоректна структура PMD baseline: {baseline_path}")

    baseline: Counter[FindingKey] = Counter()
    for source_path, rules in data["findings"].items():
        if not isinstance(source_path, str) or not source_path.startswith("src/"):
            raise ValueError(f"Некоректний шлях у PMD baseline: {source_path!r}")
        if not isinstance(rules, dict):
            raise ValueError(f"Очікувалася таблиця відбитків правил для {source_path}")
        for rule, fingerprints in rules.items():
            if not isinstance(rule, str) or not rule or not isinstance(fingerprints, list):
                raise ValueError(f"Некоректний список відбитків для {source_path} / {rule}")
            for fingerprint in fingerprints:
                if not isinstance(fingerprint, str) or FINGERPRINT_PATTERN.fullmatch(fingerprint) is None:
                    raise ValueError(f"Некоректний PMD-відбиток для {source_path} / {rule}: {fingerprint!r}")
                baseline[(source_path, rule, fingerprint)] += 1

    expected_total = data.get("totalFindings")
    if expected_total is not None and (
        isinstance(expected_total, bool)
        or not isinstance(expected_total, int)
        or expected_total != sum(baseline.values())
    ):
        raise ValueError("Сума відбитків не збігається з totalFindings у PMD baseline")

    expected_buckets = data.get("findingBuckets")
    actual_buckets = len({(path, rule) for path, rule, _ in baseline})
    if expected_buckets is not None and (
        isinstance(expected_buckets, bool)
        or not isinstance(expected_buckets, int)
        or expected_buckets != actual_buckets
    ):
        raise ValueError("Кількість пар «файл + правило» не збігається з findingBuckets у PMD baseline")

    return baseline, data


def find_increases(
    baseline: Counter[FindingKey], current: Counter[FindingKey]
) -> list[tuple[str, str, str, int, int]]:
    """Знаходить нові відбитки або збільшення їх кратності в тому самому файлі/правилі.

    @param baseline затверджений лічильник конкретних відбитків.
    @param current лічильник відбитків поточного звіту.
    @return відсортовані записи шляху, правила, відбитка та старої/нової кратності.
    """
    increases = [
        (path, rule, fingerprint, baseline_count, current[path, rule, fingerprint])
        for (path, rule, fingerprint), baseline_count in baseline.items()
        if current[path, rule, fingerprint] > baseline_count
    ]
    increases.extend(
        (path, rule, fingerprint, 0, current_count)
        for (path, rule, fingerprint), current_count in current.items()
        if (path, rule, fingerprint) not in baseline and current_count > 0
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
    parser.add_argument(
        "--max-priority",
        type=int,
        choices=sorted(VALID_PRIORITIES),
        default=DEFAULT_MAX_PRIORITY,
        help="максимальний номер пріоритету, який блокує CI (1 — найвищий; типовий: 2)",
    )
    return parser.parse_args(arguments)


def main(arguments: Iterable[str] | None = None) -> int:
    """Порівнює baseline і блокує CI лише за приросту знахідок високого пріоритету.

    Повний звіт залишається доступним, але пріоритети нижче за встановлений поріг
    не блокують звичайні коміти. Це дає змогу поступово покращувати код без ігнорування
    нових критичних проблем.

    @param arguments явний список аргументів або `None` для системного CLI.
    @return код завершення 0 за відсутності приросту, 1 за нових знахідок високого пріоритету, 2 за помилки входу.
    """
    options = parse_arguments(arguments)
    try:
        baseline, metadata = load_baseline(options.baseline)
        blocking_current, report_version = load_findings(
            options.report,
            max_priority=options.max_priority,
        )
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

    increases = find_increases(baseline, blocking_current)
    blocking_total = sum(blocking_current.values())
    if increases:
        print(
            f"PMD quality gate: виявлено {len(increases)} нових/збільшених відбитків "
            f"пріоритету P1–P{options.max_priority} "
            f"({blocking_total} знахідок P1–P{options.max_priority} у поточному звіті):",
            file=sys.stderr,
        )
        for path, rule, fingerprint, previous, actual in increases:
            print(
                f"  {path} — {rule} [{fingerprint[:12]}]: {previous} -> {actual} "
                f"(+{actual - previous})",
                file=sys.stderr,
            )
        print(
            "Виправте нові знахідки високого пріоритету або перегляньте baseline "
            "окремим обґрунтованим комітом.",
            file=sys.stderr,
        )
        return 1

    print(
        f"PMD quality gate пройдено (P1–P{options.max_priority}): "
        f"нових/збільшених блокувальних знахідок немає; "
        f"перевірено {blocking_total} знахідок P1–P{options.max_priority}; "
        "повний перелік інших знахідок збережено в PMD artifact."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
