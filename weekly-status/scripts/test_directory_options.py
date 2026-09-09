"""Keep the static GitHub form options aligned with actual course directories."""

import re
import unittest
from pathlib import Path

import generate_report as report


class DirectoryOptionsTest(unittest.TestCase):
    def test_options_match_directories(self):
        root = Path(__file__).resolve().parents[2]
        form = (root / '.github/ISSUE_TEMPLATE/task_request.yml').read_text()
        field = form.split('id: course_directory\n', 1)[1].split('    validations:', 1)[0]
        options = re.findall(r'^        - (.+)$', field, re.MULTILINE)
        directories = report.docs_directories(root)
        paths = [path for _, path in directories]
        actual_paths = [path.relative_to(root).as_posix()
                        for path in (root / 'xiangshan-course/docs').iterdir() if path.is_dir()]
        self.assertCountEqual(paths, actual_paths)
        self.assertTrue(set(report.DIRECTORY_LABELS).issubset(paths), 'Stale directory label mapping')
        names = [name for name, _ in directories]
        self.assertEqual(len(names), len(set(names)), 'Ambiguous directory names')
        self.assertCountEqual(options, names)
        for name, path in directories:
            self.assertEqual(report.resolve_directory(name, directories), (name, path))


if __name__ == '__main__':
    unittest.main()
