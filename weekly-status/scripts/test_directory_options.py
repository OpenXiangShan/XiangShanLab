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
        directory_topics = [Path(path).name.split('-', 1)[-1] for path in paths]
        self.assertTrue(set(report.DIRECTORY_ALIASES).issubset(directory_topics), 'Stale directory alias mapping')
        self.assertEqual(len(options), len(set(options)), 'Ambiguous directory options')
        for option in options:
            self.assertNotEqual(report.resolve_directory(option, directories), ("未分类", None))
        for name, path in directories:
            self.assertEqual(report.resolve_directory(name, directories), (name, path))


if __name__ == '__main__':
    unittest.main()
