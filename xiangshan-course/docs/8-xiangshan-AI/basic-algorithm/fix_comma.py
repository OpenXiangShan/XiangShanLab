#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将所有 "$，" 替换为 "$ ，"
"""

from pathlib import Path


def fix_comma_after_dollar(content):
    """将 $， 替换为 $ ，"""
    return content.replace('$，', '$ ，')


def process_file(filepath):
    """处理单个markdown文件"""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()

        fixed_content = fix_comma_after_dollar(content)

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

    print("开始修复 $， 为 $ ，...\n")

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
