#!/bin/bash
# 2026CIE-RISC-V大赛批量解密与汇总脚本
# 用法：./decrypt_summary.sh

# 选手提交目录
SUBMISSIONS_DIR="../01_参赛选手提交区"

# 输出文件
OUTPUT_CSV="decrypted_results.csv"
OUTPUT_TXT="decrypted_results.txt"

# 检查目录
if [ ! -d "$SUBMISSIONS_DIR" ]; then
    echo "错误：找不到选手提交目录: $SUBMISSIONS_DIR"
    echo "请确保在 admin/ 目录中运行此脚本"
    exit 1
fi

# 检查是否有选手提交
if [ -z "$(ls -A "$SUBMISSIONS_DIR" 2>/dev/null)" ]; then
    echo "暂无选手提交"
    exit 0
fi

echo "开始解密选手提交文件..."
echo ""

# 创建输出文件头部
echo "序号,选手目录,解密状态,信息摘要" > "$OUTPUT_CSV"
echo "=== 解密结果汇总 ===" > "$OUTPUT_TXT"
echo "解密时间: $(date '+%Y-%m-%d %H:%M:%S')" >> "$OUTPUT_TXT"
echo "解密文件数: $(find "$SUBMISSIONS_DIR" -name "submission.asc" | wc -l)" >> "$OUTPUT_TXT"
echo "" >> "$OUTPUT_TXT"

# 计数器
COUNT=0
SUCCESS=0
FAILED=0

# 遍历所有选手目录
for STUDENT_DIR in "$SUBMISSIONS_DIR"/*/; do
    if [ -d "$STUDENT_DIR" ]; then
        STUDENT_NAME=$(basename "$STUDENT_DIR")
        ENCRYPTED_FILE="$STUDENT_DIR/submission.asc"
        
        if [ -f "$ENCRYPTED_FILE" ]; then
            ((COUNT++))
            
            echo "处理选手 [$COUNT]: $STUDENT_NAME"
            
            # 解密文件
            DECRYPTED_FILE="/tmp/decrypted_$$_$COUNT.txt"
            
            if gpg --decrypt --output "$DECRYPTED_FILE" "$ENCRYPTED_FILE" 2>/dev/null; then
                ((SUCCESS++))
                STATUS="成功"
                
                # 提取前几行作为摘要
                SUMMARY=$(head -10 "$DECRYPTED_FILE" | grep -v "^#" | tr '\n' ' ' | cut -c1-500)
                
                # 保存到汇总文件
                echo "--- 选手: $STUDENT_NAME ---" >> "$OUTPUT_TXT"
                cat "$DECRYPTED_FILE" >> "$OUTPUT_TXT"
                echo "" >> "$OUTPUT_TXT"
                echo "" >> "$OUTPUT_TXT"
                
                echo "  ✓ 解密成功"
            else
                ((FAILED++))
                STATUS="失败"
                SUMMARY="解密失败"
                
                echo "  ✗ 解密失败"
            fi
            
            # 写入CSV
            echo "$COUNT,$STUDENT_NAME,$STATUS,$SUMMARY" >> "$OUTPUT_CSV"
            
            # 清理临时文件
            rm -f "$DECRYPTED_FILE" 2>/dev/null
        fi
    fi
done

echo ""
echo "=== 解密完成 ==="
echo "总计: $COUNT 个文件"
echo "成功: $SUCCESS"
echo "失败: $FAILED"
echo ""
echo "结果文件:"
echo "  - CSV汇总: $OUTPUT_CSV"
echo "  - 详细结果: $OUTPUT_TXT"