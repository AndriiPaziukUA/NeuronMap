"""Автоматизовані перевірки ратчета PMD для сценаріїв CI."""

from collections import Counter
import json
from pathlib import Path
import tempfile
import unittest
from xml.etree import ElementTree as ET

from check_pmd_baseline import find_increases, load_baseline, load_findings, normalize_source_path


class PmdBaselineTest(unittest.TestCase):
    """Перевіряє нормалізацію шляхів та порівняння лічильників PMD."""

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

    def test_loads_and_aggregates_xml_violations(self) -> None:
        """Кілька блоків одного файла сумуються, а namespace PMD не впливає на читання."""
        namespace = "http://pmd.sourceforge.net/report/2.0.0"
        xml = ET.Element(f"{{{namespace}}}pmd", {"version": "7.17.0"})
        for count in (2, 1):
            file_element = ET.SubElement(
                xml,
                f"{{{namespace}}}file",
                {"name": "/runner/project/src/main/java/example/Editor.java"},
            )
            for _ in range(count):
                ET.SubElement(file_element, f"{{{namespace}}}violation", {"rule": "CommentSize"})
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "pmd.xml"
            ET.ElementTree(xml).write(report, encoding="utf-8", xml_declaration=True)
            findings, version = load_findings(report)
        self.assertEqual("7.17.0", version)
        self.assertEqual(3, findings[("src/main/java/example/Editor.java", "CommentSize")])

    def test_loads_valid_baseline_and_rejects_inconsistent_metadata(self) -> None:
        """Помилкові загальні лічильники baseline не дозволяють випадково послабити gate."""
        baseline_data = {
            "pmdVersion": "7.17.0",
            "totalFindings": 2,
            "findingBuckets": 1,
            "findings": {"src/Editor.java": {"CommentSize": 2}},
        }
        with tempfile.TemporaryDirectory() as directory:
            baseline_path = Path(directory) / "pmd-baseline.json"
            baseline_path.write_text(json.dumps(baseline_data), encoding="utf-8")
            loaded, _ = load_baseline(baseline_path)
            self.assertEqual(2, loaded[("src/Editor.java", "CommentSize")])

            baseline_data["totalFindings"] = 1
            baseline_path.write_text(json.dumps(baseline_data), encoding="utf-8")
            with self.assertRaises(ValueError):
                load_baseline(baseline_path)

    def test_allows_unchanged_and_reduced_findings(self) -> None:
        """Наявні порушення не блокують збірку, якщо їх кількість не збільшилася."""
        baseline = Counter({("src/Editor.java", "CommentSize"): 3})
        current = Counter({("src/Editor.java", "CommentSize"): 2})
        self.assertEqual([], find_increases(baseline, current))

    def test_blocks_new_rules_and_increased_counts(self) -> None:
        """Нові правила та збільшення старих лічильників блокують CI."""
        baseline = Counter({("src/Editor.java", "CommentSize"): 3})
        current = Counter({
            ("src/Editor.java", "CommentSize"): 4,
            ("src/Other.java", "AvoidDuplicateLiterals"): 1,
        })
        self.assertEqual(
            [
                ("src/Editor.java", "CommentSize", 3, 4),
                ("src/Other.java", "AvoidDuplicateLiterals", 0, 1),
            ],
            find_increases(baseline, current),
        )


if __name__ == "__main__":
    unittest.main()
