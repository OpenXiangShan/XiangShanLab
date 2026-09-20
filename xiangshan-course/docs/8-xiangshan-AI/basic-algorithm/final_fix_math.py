#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
最终修复数学公式格式
1. 将 $ $...$ $ 恢复为 $$...$$
2. 确保行内公式 $...$ 外部有空格（内部不加空格）
"""

import re
from pathlib import Path


def fix_math_formatting(content):
    """修复数学公式格式"""
    lines = content.split('\n')
    fixed_lines = []
    in_code_block = False

    for line in lines:
        # 检测代码块
        if line.strip().startswith('```'):
            in_code_block = not in_code_block
            fixed_lines.append(line)
            continue

        if in_code_block:
            fixed_lines.append(line)
            continue

        # 处理独立成行的 $$
        if line.strip() == '$$':
            fixed_lines.append(line)
            continue

        # 修复 $ $...$ $ 为 $$...$$
        line = re.sub(r'\$\s+\$(.+?)\$\s+\$', r'$$\1$$', line)

        # 处理行内公式，确保外部有空格
        # 分步骤处理，避免复杂的正则

        # 临时替换 $$ 为占位符
        line = line.replace('$$', '<<<DISPLAY_MATH>>>')

        # 找到所有 $...$ 并处理
        parts = line.split('$')
        result = []

        for i, part in enumerate(parts):
            if i == 0:
                # 第一部分，直接添加
                result.append(part)
            elif i % 2 == 1:
                # 奇数索引是公式内容
                # 检查前一部分是否需要空格
                if result[-1] and result[-1][-1] not in ' \n\t([{':
                    result[-1] += ' '
                result.append('$' + part + '$')
            else:
                # 偶数索引是公式后的内容
                # 检查是否需要在公式后加空格
                if part and part[0] not in ' \n\t)]}.,;:!?，。；：！？':
                    result[-1] += ' '
                result.append(part)

        line = ''.join(result)

        # 恢复 $$
        line = line.replace('<<<DISPLAY_MATH>>>', '$$')

        fixed_lines.append(line)

    return '\n'.join(fixed_lines)


def process_file(filepath):
    """处理单个markdown文件"""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()

        fixed_content = fix_math_formatting(content)

        # 只有内容改变时才写入
        if content != fixed_content:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(fixed_content)
            return True
        return False
    except Exception as e:
        print(f"处理文件 {filepath} 时出错: {e}")
        return False


def main():
    """主函数：遍历所有章节目录并处理markdown文件"""
    base_dir = Path(__file__).parent

    # 获取所有章节目录
    chapter_dirs = [d for d in base_dir.iterdir() if d.is_dir() and not d.name.startswith('.')]
    chapter_dirs.sort()

    total_files = 0
    modified_files = 0

    print("开始最终修复数学公式格式...\n")

    for chapter_dir in chapter_dirs:
        # 查找该章节目录下的所有.md文件
        md_files = list(chapter_dir.glob('*.md'))

        for md_file in md_files:
            total_files += 1
            print(f"处理: {md_file.relative_to(base_dir)}", end=' ... ')

            if process_file(md_file):
                modified_files += 1
                print("✓ 已修复")
            else:
                print("○ 无需修改")

    print(f"\n完成！共处理 {total_files} 个文件，修改了 {modified_files} 个文件。")


if __name__ == '__main__':
    main()
