# Windows独立运行包说明

独立版内置Java 11、MySQL 8.0和前后端程序，适用于Windows 10、Windows 11的64位电脑。首次运行会创建私有数据库、业务表和初始数据，不需要安装Java、MySQL、Node.js或Maven，也不需要输入数据库密码。运行过程无需联网下载依赖。

## 首次使用

1. 完整解压压缩包到当前用户可写的本地目录，预留至少2GB空间。不要直接在压缩软件中运行EXE。目录字符需能由当前Windows系统语言表示；使用英文目录可避免跨语言电脑上的路径兼容问题。
2. 双击“启动溯源平台.exe”。首次启动需要初始化数据库，请等待控制台显示平台已启动。
3. 浏览器自动打开 http://127.0.0.1:18080/login 。管理员初始账号为admin，密码为admin123，使用后修改密码。
4. 使用完毕，双击“停止溯源平台.cmd”，等待启动窗口显示“本地数据库已停止”，再关闭窗口。

普通Windows账户即可运行，不注册系统服务。程序会创建初始用户、角色、权限、分类、事件类型、规则和链配置；新录入业务数据保存在本地数据库。初始记录以随包db/init.sql为准。

## 数据保存

| 位置 | 内容 |
| --- | --- |
| data/mysql | 本运行包的私有MySQL数据文件 |
| data/standalone-secrets.json | Windows DPAPI加密的数据库密码和应用密钥、实例标识及初始化状态 |
| data/mysql.ini | 本实例生成的数据库配置 |
| logs | 应用和数据库日志 |
| config/runtime.json | 页面端口、链及存储模式等运行配置 |

数据库只监听127.0.0.1。首次运行从13306至13336选择空闲端口并保存；之后始终使用原端口。程序不会连接、创建或改动电脑原有的MySQL服务。root和应用账户使用分别随机生成的密码，应用账户仅获parts_trace库的权限；停止专用账户仅有SHUTDOWN权限。密码不通过命令行参数传递。

后续启动会检查数据库目录和实例标识，复用已有表及数据，不重复执行含DROP TABLE的初始化脚本。初始化中断导致部分表已建立时，程序会停止并保留现场，不擅自删除重建。程序异常关闭后，MySQL会在下次启动时执行自身的崩溃恢复。

必须保留整个data目录。密钥绑定首次启动时的Windows账户，不能把已使用目录直接复制到其他电脑后继续使用；另一台新电脑请重新解压原始ZIP，创建新的独立实例。已有数据需要迁移时，应同时规划数据库备份和原应用加密主密钥迁移。

## 配置与停止

默认页面端口18080，可在首次或后续启动前修改config/runtime.json中的serverPort。databasePort为0时仅在首次运行自动选择端口；数据库建立后，以加密配置保存的原端口为准。端口被占用时程序会停止并提示，不停止其他进程。不要并行启动同一个目录的两份程序。

停止脚本通过本目录的停止请求通知程序，先结束应用，再正常关闭数据库。强行结束启动器、直接关闭控制台或关机可能触发数据库崩溃恢复，应优先使用停止脚本。

命令行还支持：

```powershell
.\启动溯源平台.exe --check
.\启动溯源平台.exe --no-browser
.\启动溯源平台.exe --stop --no-pause
```

--check只检查内置文件和配置，不创建数据、不启动数据库。启动失败时查看logs/mysql-console-error.log、mysql-init-error.log、console.log和error.log；不要通过反复导入init.sql解决已有数据库的问题。

## 区块链与存储

默认chainMode=db、objectMode=db，可进行本地业务演示。运行包不包含长安链、FISCO BCOS节点或IPFS节点。使用真实链时需要提供已经部署的节点、账户、SDK配置、证书和合约，并按原部署文档设置additionalConfig。多机构跨链确认仍需独立部署核验服务。

配套提供异构链Docker环境，解压后见`docker/README.md`。将 `docker` 放在独立版运行目录旁，支持 `standalone` 或压缩包中的原目录名。先运行 `docker/start-chains.cmd`，验收通过后再启动本独立版。脚本会生成本机证书、部署两类链的合约并配置 `real` 模式。单机网络采用授权中继签名，未配置多机构联合核验服务。源码部署说明位于后端`deploy/heterogeneous/README.md`。

## 从源码构建

在后端目录执行scripts/package-windows.ps1并增加-Standalone参数。构建时提供MySqlHome（MySQL 8.0二进制目录）、JreArchive（Eclipse Temurin Java 11 Windows x64 JRE ZIP）和JreSha256（官方SHA256）。其余JavaHome、MavenPath、NodePath、GoPath、MavenRepository、Offline参数与普通运行包相同。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -Standalone -MySqlHome "D:\tools\mysql-8.0" -JreArchive "D:\downloads\java11-jre.zip" -JreSha256 "替换为官方64位十六进制SHA256"
```

以上路径和哈希为占位示例，按本机构建环境填写。脚本向项目根目录的`output/standalone/`和`output`下名称含“独立版”的ZIP生成产物，不覆盖普通版运行数据。当前参赛运行ZIP整理在`output/比赛资料/可执行文件/`，与构建输出位置分别管理。

运行包保留Java的legal目录、MySQL的LICENSE及README、Go许可和运行环境来源记录。MySQL社区版源码可从 https://downloads.mysql.com/archives/community/ 选择对应版本的Source Code下载；Java源码及构建信息见 https://github.com/adoptium/temurin11-binaries 。
