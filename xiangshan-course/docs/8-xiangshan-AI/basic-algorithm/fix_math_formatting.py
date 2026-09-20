#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
批量修复markdown文件中的数学公式格式问题
确保所有行内公式 $...$ 的外部前后都有空格，以兼容GitHub的渲染
注意：只在公式外部加空格，不在 $ 符号和公式内容之间加空格
"""

import re
from pathlib import Path


def fix_math_formatting(content):
    """修复markdown文件中的数学公式格式"""
    lines = content.split('\n')
    fixed_lines = []
    in_code_block = False

    for line in lines:
        # 检测代码块，避免修改代码块内的内容
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

        # 先临时替换 $$ 为占位符，避免误处理行间公式
        line = line.replace('$$', '<<<DISPLAY_MATH>>>')

        # 修复行内公式 $...$
        # 策略：找到所有的 $...$ 配对，在整个公式外部前后确保有空格

        # 使用正则表达式匹配 $...$ 并在外部加空格
        # 匹配模式：非$字符 + $ + 公式内容 + $ + 非$字符
        # 在 $ 前面加空格（如果前面不是空格、行首、或特殊符号）
        line = re.sub(r'([\w一-鿿)\]\}])\$([^\$]+?)\$', r'\1 $\2$', line)

        # 在 $ 后面加空格（如果后面不是空格、行尾、或特殊符号）
        line = re.sub(r'\$([^\$]+?)\$([\w一-鿿\(\[\{])', r'$\1$ \2', line)

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

    print("开始批量修复数学公式格式...\n")

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
