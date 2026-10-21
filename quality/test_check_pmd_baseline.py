"""Автоматизовані перевірки відбитків PMD для сценаріїв CI."""

from collections import Counter
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from xml.etree import ElementTree as ET

from check_pmd_baseline import (
    find_increases,
    finding_fingerprint,
    load_baseline,
    load_findings,
    main,
    normalize_source_path,
)


class PmdBaselineTest(unittest.TestCase):
    """Перевіряє нормалізацію шляхів і порівняння окремих PMD-знахідок."""

    def test_normalizes_unix_and_windows_runner_paths(self) -> None:
        """Шляхи з різних ОС нормалізуються до єдиного шляху в репозиторії."""
        self.assertEqual(
            "src/main/java/example/Editor.java",
            normalize_source_path("/home/runner/work/NeuronMap/NeuronMap/src/main/java/example/Editor.java"),
        )
        self.assertEqual(
            "src/test/java/example/EditorTest.java",
            normalize_source_path(r"C:\\a\\NeuronMap\\src\\test\\java\\example\\EditorTest.java"),
        )

    def test_rejects_report_path_outside_source_tree(self) -> None:
        """Звіт із незнайомим шляхом не ігнорується мовчки."""
        with self.assertRaises(ValueError):
            normalize_source_path("/tmp/generated/File.java")

    def test_fingerprint_is_stable_when_unrelated_lines_are_inserted(self) -> None:
        """Номери рядків не створюють нову знахідку після сторонніх змін вище за неї."""
        first = ET.Element(
            "violation",
            {
                "rule": "CommentSize",
                "ruleset": "Documentation",
                "priority": "3",
                "package": "example",
                "class": "Editor",
                "method": "open",
                "beginline": "20",
                "endline": "22",
                "begincolumn": "5",
                "endcolumn": "8",
            },
        )
        first.text = "Comment is too large: Too many lines"
        shifted = ET.Element("violation", dict(first.attrib))
        shifted.set("beginline", "44")
        shifted.set("endline", "46")
        shifted.text = first.text
        self.assertEqual(finding_fingerprint(first), finding_fingerprint(shifted))

    def test_fingerprint_distinguishes_different_findings_in_same_bucket(self) -> None:
        """Відмінні повідомлення або методи дають різні ключі навіть за однакового файла й правила."""
        first = ET.Element(
            "violation",
            {"rule": "CommentSize", "class": "Editor", "method": "open", "begincolumn": "1"},
        )
        first.text = "Comment is too large: Too many lines"
        replacement = ET.Element(
            "violation",
            {"rule": "CommentSize", "class": "Editor", "method": "save", "begincolumn": "1"},
        )
        replacement.text = "Comment is too large: Line too long"
        self.assertNotEqual(finding_fingerprint(first), finding_fingerprint(replacement))

    def test_loads_and_aggregates_xml_violations(self) -> None:
        """Однакові знахідки з різних XML-блоків зберігають кратність."""
        namespace = "http://pmd.sourceforge.net/report/2.0.0"
        xml = ET.Element(f"{{{namespace}}}pmd", {"version": "7.17.0"})
        for line in (10, 20, 30):
            file_element = ET.SubElement(
                xml,
                f"{{{namespace}}}file",
                {"name": "/runner/project/src/main/java/example/Editor.java"},
            )
            violation = ET.SubElement(
                file_element,
                f"{{{namespace}}}violation",
                {
                    "rule": "CommentSize",
                    "ruleset": "Documentation",
                    "priority": "3",
                    "class": "Editor",
                    "method": "open",
                    "beginline": str(line),
                    "endline": str(line),
                    "begincolumn": "1",
                    "endcolumn": "1",
                },
            )
            violation.text = "Comment is too large: Line too long"
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "pmd.xml"
            ET.ElementTree(xml).write(report, encoding="utf-8", xml_declaration=True)
            findings, version = load_findings(report)
        self.assertEqual("7.17.0", version)
        self.assertEqual(3, sum(findings.values()))
        self.assertEqual(
            3,
            findings[(
                "src/main/java/example/Editor.java",
                "CommentSize",
                next(iter(findings))[2],
            )],
        )

    def test_loads_fingerprint_baseline_and_rejects_inconsistent_metadata(self) -> None:
        """Помилкові контрольні лічильники або відбитки не дозволяють послабити quality gate."""
        fingerprint = "a" * 64
        baseline_data = {
            "pmdVersion": "7.17.0",
            "totalFindings": 2,
            "findingBuckets": 1,
            "findings": {"src/Editor.java": {"CommentSize": [fingerprint, fingerprint]}},
        }
        with tempfile.TemporaryDirectory() as directory:
            baseline_path = Path(directory) / "pmd-baseline.json"
            baseline_path.write_text(json.dumps(baseline_data), encoding="utf-8")
            loaded, _ = load_baseline(baseline_path)
            self.assertEqual(2, loaded[("src/Editor.java", "CommentSize", fingerprint)])

            baseline_data["totalFindings"] = 1
            baseline_path.write_text(json.dumps(baseline_data), encoding="utf-8")
            with self.assertRaises(ValueError):
                load_baseline(baseline_path)

            baseline_data["totalFindings"] = 2
            baseline_data["findings"]["src/Editor.java"]["CommentSize"] = ["not-a-fingerprint"]
            baseline_path.write_text(json.dumps(baseline_data), encoding="utf-8")
            with self.assertRaises(ValueError):
                load_baseline(baseline_path)

    def test_allows_unchanged_and_reduced_fingerprints(self) -> None:
        """Наявні незмінені або прибрані знахідки не блокують збірку."""
        first = ("src/Editor.java", "CommentSize", "a" * 64)
        removed = ("src/Editor.java", "CommentSize", "b" * 64)
        self.assertEqual([], find_increases(Counter({first: 1, removed: 2}), Counter({first: 1})))

    def test_blocks_replacement_in_same_file_and_rule_bucket(self) -> None:
        """Нова знахідка блокує CI, навіть якщо стару знахідку того самого правила прибрано."""
        previous = ("src/Editor.java", "CommentSize", "a" * 64)
        replacement = ("src/Editor.java", "CommentSize", "b" * 64)
        self.assertEqual(
            [("src/Editor.java", "CommentSize", "b" * 64, 0, 1)],
            find_increases(Counter({previous: 1}), Counter({replacement: 1})),
        )

    def test_blocks_new_fingerprints_and_increased_duplicate_count(self) -> None:
        """Нова пара «файл + правило» та нова кратність відбитка блокують CI."""
        existing = ("src/Editor.java", "CommentSize", "a" * 64)
        new_rule = ("src/Other.java", "AvoidDuplicateLiterals", "c" * 64)
        baseline = Counter({existing: 1})
        current = Counter({existing: 2, new_rule: 1})
        self.assertEqual(
            [
                ("src/Editor.java", "CommentSize", "a" * 64, 1, 2),
                ("src/Other.java", "AvoidDuplicateLiterals", "c" * 64, 0, 1),
            ],
            find_increases(baseline, current),
        )

    def test_priority_filter_keeps_only_findings_at_or_above_selected_severity(self) -> None:
        """Quality gate зберігає пріоритети P1–P2, не блокуючись на P3–P5."""
        xml = ET.Element("pmd", {"version": "7.17.0"})
        file_element = ET.SubElement(
            xml,
            "file",
            {"name": "/runner/project/src/main/java/example/Editor.java"},
        )
        for priority in ("1", "2", "3", "4", "5"):
            violation = ET.SubElement(
                file_element,
                "violation",
                {
                    "rule": f"Rule{priority}",
                    "ruleset": "Test",
                    "priority": priority,
                    "class": "Editor",
                    "method": "run",
                    "begincolumn": priority,
                    "endcolumn": priority,
                },
            )
            violation.text = f"Test rule with priority {priority}"

        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "pmd.xml"
            ET.ElementTree(xml).write(report, encoding="utf-8", xml_declaration=True)
            all_findings, _ = load_findings(report)
            blocking_findings, version = load_findings(report, max_priority=2)

        self.assertEqual("7.17.0", version)
        self.assertEqual(5, sum(all_findings.values()))
        self.assertEqual(2, sum(blocking_findings.values()))
        self.assertEqual({"Rule1", "Rule2"}, {rule for _, rule, _ in blocking_findings})

    def test_priority_filter_rejects_missing_or_invalid_priority(self) -> None:
        """Звіт без коректного пріоритету не можна використати для обходу quality gate."""
        xml = ET.Element("pmd", {"version": "7.17.0"})
        file_element = ET.SubElement(
            xml,
            "file",
            {"name": "/runner/project/src/main/java/example/Editor.java"},
        )
        violation = ET.SubElement(file_element, "violation", {"rule": "MissingPriority"})
        violation.text = "Missing priority"

        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "pmd.xml"
            ET.ElementTree(xml).write(report, encoding="utf-8", xml_declaration=True)
            with self.assertRaisesRegex(ValueError, "Некоректний пріоритет PMD"):
                load_findings(report, max_priority=2)

    def test_quality_gate_ignores_new_low_priority_findings_but_blocks_priority_two(self) -> None:
        """Нові P3 не перешкоджають комітам, а нові P2 залишаються блокувальними."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline_path = root / "pmd-baseline.json"
            report_path = root / "pmd.xml"
            baseline_path.write_text(
                json.dumps({
                    "pmdVersion": "7.17.0",
                    "totalFindings": 0,
                    "findingBuckets": 0,
                    "findings": {},
                }),
                encoding="utf-8",
            )
            xml = ET.Element("pmd", {"version": "7.17.0"})
            file_element = ET.SubElement(
                xml,
                "file",
                {"name": "/runner/project/src/main/java/example/Editor.java"},
            )
            low_priority = ET.SubElement(
                file_element,
                "violation",
                {"rule": "CommentSize", "priority": "3", "class": "Editor", "method": "run"},
            )
            low_priority.text = "Comment is too large"
            ET.ElementTree(xml).write(report_path, encoding="utf-8", xml_declaration=True)

            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(
                    0,
                    main(["--baseline", str(baseline_path), "--report", str(report_path)]),
                )

            high_priority = ET.SubElement(
                file_element,
                "violation",
                {"rule": "NullDereference", "priority": "2", "class": "Editor", "method": "run"},
            )
            high_priority.text = "Potential null dereference"
            ET.ElementTree(xml).write(report_path, encoding="utf-8", xml_declaration=True)

            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(
                    1,
                    main(["--baseline", str(baseline_path), "--report", str(report_path)]),
                )


if __name__ == "__main__":
    unittest.main()
