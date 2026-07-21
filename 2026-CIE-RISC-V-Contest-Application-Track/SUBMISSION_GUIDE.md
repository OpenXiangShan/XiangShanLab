# 2026CIE全国RISC-V高水平创新及应用大赛作品提交流程指南 
## 📋 概述 
本指南详细说明了如何向2026CIE全国RISC-V高水平创新及应用大赛提交您的作品。整个流程基于GitHub平台，采用加密方式保护您的作品隐私。请严格按照以下步骤操作，确保您的作品能够被成功加密、接收和评审。

---

## 🔧 第一步：Fork主仓库 
### 1.1 访问主仓库 
打开浏览器，访问主仓库：

```plain
https://github.com/OpenXiangShan/XiangShanLab
```

### 1.2 Fork到您的账号 
1. 点击页面右上角的 **"Fork"** 按钮
2. 选择您的个人账号作为目标位置
3. 等待Fork完成，系统会自动创建 `您的用户名/XiangShanLab`仓库

### 1.3 克隆到本地 
在终端中运行：

```plain
# 克隆您Fork的仓库
git clone https://github.com/您的用户名/XiangShanLab.git

# 进入仓库目录
cd XiangShanLab/2026-CIE-RISC-V-Contest-Application-Track
```

---

## 🏗️ 第二步：创建您的作品公有仓库 
### 2.1 在GitHub上创建公有仓库 
1. 登录GitHub，点击右上角 **"+"** → **"New repository"**
2. 设置仓库信息：
    - **Repository name**: `cie2026-团队名称-作品名`（示例：`cie2026-innovators-riscv-ai`）
    - **Description**: 简要描述您的作品
    - **重要**：必须选择 **"public"**（公有仓库）
3. 点击 **"Create repository"**

### 2.2 上传作品到公有仓库 
在您的作品目录中运行：

```plain
# 初始化Git仓库
git init

# 添加所有文件
git add .

# 提交更改
git commit -m "2026CIE大赛作品-团队名称-作品名"

# 关联远程仓库
git remote add origin https://github.com/您的用户名/您的公有仓库名.git

# 推送代码
git push -u origin main
```

---

## 📄 第三步：创建提交信息文件 
### 3.1 创建信息文件 
进入您之前克隆的`XiangShanLab/2026-CIE-RISC-V-Contest-Application-Track`仓库目录：

在此目录下创建一个纯文本文件，命名为 `my_submission.txt`。

```plain
# 确保在正确的目录[重要！！！]
cd XiangShanLab/2026-CIE-RISC-V-Contest-Application-Track

# 创建提交内容
touch my_submission.txt
```

### 3.2 编辑文件内容 
用文本编辑器（如记事本、VS Code、Sublime Text等）打开文件，按照以下格式填写信息：

```plain
team-name: 您的团队名称
repo-url: https://github.com/您的用户名/您的私有仓库.git
github-username: 您的GitHub用户名
contact-email: 您的邮箱@example.com
project-description: 简要描述您的项目，不超过100字

# 可选信息（可根据需要添加）
team-members: 成员1(角色), 成员2(角色)
university: 您的学校/单位
advisor: 指导老师
phone: 联系电话
notes: 其他需要说明的事项
```

### 3.3 保存文件 
确保文件使用**UTF-8编码**保存，扩展名为`.txt`。

---

## 🔐 第四步：加密并提交信息 
### 4.1 运行提交脚本 
在您之前克隆的`XiangShanLab`仓库目录中运行：

```plain
# 确保在正确的目录
# cd XiangShanLab/2026-CIE-RISC-V-Contest-Application-Track

# 运行提交脚本
source ./submit.sh my_submission.txt
```

### 4.2 脚本运行说明 
脚本会自动执行以下操作：

1. 检查文件格式
2. 导入评审公钥
3. 加密您的提交信息文件
4. 生成加密文件

## 📤 第五步：推送到GitHub 
### 5.1 添加和提交文件 
```plain
# 添加您的加密文件
git add .

# 提交更改
git commit -m "提交2026CIE大赛作品：您的团队名称"
```

### 5.2 推送到您的Fork 
```plain
# 推送到您的GitHub仓库
git push
```

### 5.3 验证推送成功 
访问以下链接，确认您的文件已上传：

```plain
https://github.com/您的用户名/XiangShanLab/tree/main/01_参赛选手提交区/您的团队名称
```

确保您能看到 `submission.asc`文件。

---

## 🔄 第六步：发起Pull Request（PR） 
### 6.1 访问Pull Request页面 
1. 打开浏览器，访问您的Fork仓库：

```plain
https://github.com/您的用户名/XiangShanLab
```

2. 点击 **"Pull requests"** 选项卡
3. 点击 **"New pull request"**

### 6.2 设置比较分支 
确保设置如下：

+ **base repository**: `OpenXiangShan/XiangShanLab`
+ **base**: `main`
+ **head repository**: `您的用户名/XiangShanLab`
+ **compare**: `main/master`

### 6.3 填写PR信息 
**标题格式**（必须遵守）：

```plain
[2026CIE大赛] 团队名称 - 项目名称
```

**描述模板**：

```plain
## 团队与项目信息
- **团队名称**：
- **参赛单位**：
- **赛题**：面向边缘AI大语言模型推理的RISC-V自定义vdot指令设计

## 阶段完成情况
- [ ] 阶段一：环境部署与验证
- [ ] 阶段二：vdot设计与实现
- [ ] 阶段三：协同仿真与评估

## 提交内容概述
（请简要说明本次提交的内容）

---
## 评审区（请评委填写）
**评审意见**：
```

### 6.4 创建PR 
1. 仔细检查填写的信息
2. 点击 **"Create pull request"**

---

## 📊 第七步：后续流程 
### 7.1 等待评审 
+ PR创建后，评审委员会会收到通知
+ 评审状态会显示在PR页面
+ 评审意见会通过PR评论的方式反馈

