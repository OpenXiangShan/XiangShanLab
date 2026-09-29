XiangShan + VS Code + Metals 配置步骤
一、前置条件
项目	要求
本地	VS Code + Remote-SSH 扩展
服务器	Linux，能 SSH 登录
服务器 Java	JDK 17 或 21
服务器 Mill	/usr/local/bin/mill
XiangShan 的主构建工具是 Mill，不是 sbt。

二、VS Code 扩展准备
打开 VS Code，点左侧扩展图标

搜索 scala

只保留 Scala (Metals)，其他 Scala 相关扩展全部 Disable

三、连接服务器
左下角点绿色 >< 图标

选 Connect to Host，输入 用户名@服务器IP

连接成功后，File → Open Folder，打开项目根目录

四、确认 Metals 装在远程
左侧点扩展图标

搜索 scala

若 Scala (Metals) 显示 "Install in SSH: xxx"，点它安装到服务器

五、选择 Mill 作为构建服务器
Metals 启动后弹出选择框时：

"Multiple build definitions found" → 选 mill

"Multiple build servers detected" → 选 mill-bsp

弹框消失时手动切换
按 Ctrl+Shift+P，执行：

text
Metals: Switch build server
选 mill-bsp。

六、等待导入
观察左下角状态栏，依次显示：

text
Importing build: Xs
→ Connecting to build server...
→ Indexing...
→ Indexing complete!
首次导入需要几分钟到十几分钟，期间不要重复点击、不要 Reload、不要关闭 VS Code。

七、验证成功
状态栏应显示
text
SSH: 服务器 | XiangShan | mill-bsp ⟷ | 🚀 Indexing complete!
打开 Scala 文件验证
打开 src/main/scala/top/XSTop.scala，检查：

功能	预期
语法高亮	关键字、类名有颜色
import 解析	顶部 import 无红色波浪线
补全	输入 chisel3. 弹出成员列表
跳转	Ctrl + 点击类名跳到定义
悬停	显示类型签名

<img width="2427" height="1747" alt="image" src="https://github.com/user-attachments/assets/13027621-1fd4-430d-a2e9-e4a10673b7dd" />
