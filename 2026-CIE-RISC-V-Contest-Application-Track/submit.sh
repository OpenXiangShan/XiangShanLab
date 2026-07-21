#!/bin/bash
# 2026CIE-RISC-V大赛作品提交脚本
# 不使用exit，避免终端关闭

echo "=== 2026CIE-RISC-V大赛作品提交系统 ==="
echo ""

# 使用变量追踪错误
ERROR_OCCURRED=0
ERROR_MESSAGE=""

# 检查是否有参数
if [ $# -ne 1 ]; then
    ERROR_MESSAGE="请提供提交信息文件\n\n使用方法：\n  source ./submit.sh 您的文件.txt\n\n文件格式示例：\n  team-name: 团队名称\n  repo-url: https://github.com/用户名/仓库.git\n "
    ERROR_OCCURRED=1
fi

# 如果没有错误，继续检查文件
if [ $ERROR_OCCURRED -eq 0 ]; then
    INFO_FILE="$1"
    
    # 基本检查：文件是否存在
    if [ ! -f "$INFO_FILE" ]; then
        ERROR_MESSAGE="找不到文件 '$INFO_FILE'，请检查文件路径是否正确"
        ERROR_OCCURRED=1
    fi
fi

# 如果没有错误，检查是否在主仓库
if [ $ERROR_OCCURRED -eq 0 ]; then
    if [ ! -f "contest/public-key.asc" ]; then
        ERROR_MESSAGE="您不在正确的目录中。\n\n请确保您在 XiangShanLab/2026-CIE-RISC-V-Contest-Application-Track 目录中运行此脚本\n"
        ERROR_OCCURRED=1
    fi
fi

# 如果有错误，显示并结束
if [ $ERROR_OCCURRED -eq 1 ]; then
    echo "❌ 错误："
    echo -e "$ERROR_MESSAGE"
    echo ""
    echo "脚本将结束。"
    read -p "按回车键关闭此窗口..."  # 让用户手动关闭
    # 不执行exit，让脚本自然结束
    return 2>/dev/null || true
fi

# 如果没有错误，继续执行
echo "正在准备提交..."

# 导入公钥
echo "导入评审公钥..."
gpg --import contest/public-key.asc 2>/dev/null
if [ $? -ne 0 ]; then
    echo "提示：公钥可能已存在，继续..."
fi

# 简单读取团队名称
TEAM_NAME=""
if grep -q "team-name:" "$INFO_FILE"; then
    TEAM_NAME=$(grep "team-name:" "$INFO_FILE" | head -1 | cut -d: -f2- | sed 's/^[[:space:]]*//')
else
    echo "警告：未找到 'team-name:' 字段"
    # read -p "请输入团队名称: " TEAM_NAME
fi

if [ -z "$TEAM_NAME" ]; then
    echo "❌ 错误：团队名称不能为空"
    echo ""
    read -p "按回车键关闭此窗口..."
    return 2>/dev/null || true
fi

echo "团队名称: $TEAM_NAME"

# 创建提交目录
SUBMISSION_DIR="01_参赛选手提交区/$TEAM_NAME"
mkdir -p "$SUBMISSION_DIR"

# 加密文件
echo "正在加密文件..."
gpg --encrypt \
    --recipient "3502918558@qq.com" \
    --armor \
    --output "$SUBMISSION_DIR/submission.asc" \
    "$INFO_FILE" 2>/dev/null

if [ $? -eq 0 ] && [ -f "$SUBMISSION_DIR/submission.asc" ]; then
    echo ""
    echo "✅ 提交文件准备成功！"
    echo ""
    echo "加密文件已保存到: $SUBMISSION_DIR/submission.asc"
    echo ""
    read -p "按回车键完成操作..."
else
    echo ""
    echo "❌ 加密失败"
    echo ""
    echo "可能的原因："
    echo "1. GPG 未正确安装"
    echo "2. 公钥导入失败"
    echo "3. 文件权限问题"
    echo ""
    echo "您可以尝试："
    echo "1. 安装 GPG:"
    echo "   Ubuntu: sudo apt install gnupg"
    echo "   macOS: brew install gnupg"
    echo "2. 手动导入公钥:"
    echo "   gpg --import contest/public-key.asc"
    echo "3. 手动加密:"
    echo "   gpg --encrypt --recipient contest-admin@xiangshanlab.org --armor -o submission.asc 您的文件.txt"
    echo ""
    read -p "按回车键关闭此窗口..."
fi

# 脚本自然结束，不执行exit