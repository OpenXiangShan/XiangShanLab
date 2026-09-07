"""Keep the static GitHub form options aligned with actual course directories."""

import re
import unittest
from pathlib import Path


class DirectoryOptionsTest(unittest.TestCase):
    def test_options_match_directories(self):
        root = Path(__file__).resolve().parents[2]
        form = (root / '.github/ISSUE_TEMPLATE/task_request.yml').read_text()
        field = form.split('id: course_directory\n', 1)[1].split('    validations:', 1)[0]
        options = re.findall(r'^        - (.+)$', field, re.MULTILINE)
        directories = [re.sub(r'^xiangshan-', '', re.sub(r'^\d+-', '', path.name))
                       for path in (root / 'xiangshan-course/docs').iterdir() if path.is_dir()]
        self.assertEqual(len(directories), len(set(directories)), 'Ambiguous directory names')
        self.assertCountEqual(options, directories)


if __name__ == '__main__':
    unittest.main()
