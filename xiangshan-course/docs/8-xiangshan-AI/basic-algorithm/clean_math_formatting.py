#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
清理数学公式内部多余的空格
- 删除 $$ 和公式内容之间的空格
- 删除 $ 和公式内容之间的空格
- 确保行内公式外部有空格（GitHub渲染需要）
"""

import re
from pathlib import Path


def clean_math_formatting(content):
    """清理数学公式格式"""
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

        # 清理行间公式 $$ ... $$ 内部的空格
        # 匹配 $$ 后面的空格
        line = re.sub(r'\$\$\s+', r'$$', line)
        # 匹配 $$ 前面的空格
        line = re.sub(r'\s+\$\$', r'$$', line)

        # 清理行内公式 $ ... $ 内部的空格，并确保外部有空格
        # 先找到所有 $ ... $ 对
        def fix_inline_math(match):
            before = match.group(1) or ''
            formula = match.group(2).strip()  # 公式内容去掉两端空格
            after = match.group(3) or ''

            # 判断前面是否需要空格
            need_space_before = before and before[-1] not in ' \n\t({\['
            # 判断后面是否需要空格
            need_space_after = after and after[0] not in ' \n\t)}\].,;:!?，。；：！？'

            result = ''
            if need_space_before:
                result += before + ' $' + formula + '$'
            else:
                result += before + '$' + formula + '$'

            if need_space_after:
                result += ' ' + after
            else:
                result += after

            return result

        # 匹配模式：(前面的内容)($ 公式内容 $)(后面的内容)
        # 使用非贪婪匹配，避免跨多个公式
        line = re.sub(r'(^|.?)\$([^\$]+?)\$(.?|$)', fix_inline_math, line)

        fixed_lines.append(line)

    return '\n'.join(fixed_lines)


def process_file(filepath):
    """处理单个markdown文件"""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()

        fixed_content = clean_math_formatting(content)

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

    print("开始清理数学公式内部多余空格...\n")

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
