# Windows运行包说明

本运行包适用于Windows 10或Windows 11的64位环境。前端页面已包含在后端JAR中，EXE负责检查环境、启动服务和打开浏览器，无需安装Node.js、npm或Maven。

## 运行环境

安装Java 8或Java 11，将JAVA_HOME指向对应目录，或将java.exe加入PATH。MySQL 8服务需已启动，数据库名为parts_trace；运行包通过JDBC检查数据库，不要求安装mysql命令行客户端。

区块链节点和IPFS仍由部署环境提供。链模式默认db，对象存储默认db；真实链需要配置SDK、证书、账户和合约。

## 首次启动

1. 完整解压压缩包，保留EXE、JAR及其余目录之间的位置关系。
2. 修改config/runtime.json中的数据库地址、端口和账户。密码不写入该文件，启动时隐藏输入；也可通过环境变量KEWEI_DBPWD提供。
3. 新建测试库时导入db/init.sql。该脚本会删除并重建表，只用于新库。已有业务库先备份，再按原数据库版本依次执行cross_chain_migration.sql和V20261025__security_completion.sql；启用多机构核验还需V20261026__relay_confirmation.sql。
4. 双击“启动溯源平台.exe”，按提示输入数据库密码。检查通过后，浏览器自动打开http://127.0.0.1:8080/login；端口以配置为准。

新库管理员账号为admin，初始密码admin123，使用前修改。已有数据库以实际密码为准。

## 密钥与升级

启动入口通过Windows DPAPI加密保存JWT密钥和私钥加密主密钥，默认位置为data/startup-secrets.json，不保存数据库密码。后续启动必须保留该文件，并使用原Windows账户。

连接已有加密私钥的数据库时，需要沿用原KEY_ENCRYPTION_KEY。可在同一Windows账户下将原startup-secrets.json安全复制到配置指定的位置，或通过PARTS_TRACE_SETTINGS_PATH指向原文件。未取得原密钥时，启动入口会要求输入，不自动生成替代密钥。

更新运行包时保留data目录及实际config配置。DPAPI文件不直接跨机器迁移；迁移环境应安全保留原应用密钥。

## 配置项

| 项目 | 作用 |
| --- | --- |
| databaseHost、databasePort、databaseUser | 数据库连接信息 |
| serverAddress | 默认127.0.0.1；需要局域网访问时按部署要求配置监听地址 |
| serverPort | 前端与后端共同使用的端口，默认8080 |
| chainMode、objectMode | 分别为db/real及db/ipfs |
| secretsFile | 当前环境的DPAPI密钥文件路径 |
| additionalConfig | 可选外部Spring配置文件；相对路径以运行包根目录为准 |

已设置的数据库和存储环境变量优先于runtime.json。真实链与多机构核验的外部配置可通过additionalConfig载入，参考config目录的SDK与委员会样例。

## 停止与故障检查

按启动窗口中的Ctrl+C或关闭启动窗口停止本次启动的服务。启动入口只管理自己启动的进程，不结束其他Java服务。端口已被占用时，修改serverPort或关闭原服务后再启动。

启动失败查看logs/console.log、logs/error.log及logs/parts-trace.log。数据库缺表时先备份并升级；密钥解密失败时核对原密钥与Windows账户，不删除密钥文件重建。

命令行支持：

```powershell
.\启动溯源平台.exe --version
.\启动溯源平台.exe --check
.\启动溯源平台.exe --no-browser
.\启动溯源平台.exe --config C:\deploy\runtime.json
```

自动化调用可增加--no-pause，退出时不等待按键。--check会检查数据库和密钥配置，但不启动服务。

## 从源码重新打包

在后端目录执行scripts/package-windows.ps1，构建需要Java 8或Java 11、Maven、Node.js和Go。脚本先构建前端，再使用Maven的windows-package配置将静态页面写入JAR，编译Windows启动器并生成压缩包。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1
```

脚本生成`output/software/`和`output/工业零部件可信溯源平台-Windows-x64.zip`，均相对于项目根目录；这是重新构建的输出位置。当前整理的普通版ZIP位于`output`根目录，参赛独立版和Docker包位于`output/比赛资料/可执行文件/`。

前端依赖未安装时执行npm ci；Maven和Go依赖需按构建环境准备。可通过JavaHome、MavenPath、NodePath、GoPath及MavenRepository参数指定工具位置，Offline使用Maven离线模式。

运行包不包含实际数据库密码、应用密钥、链私钥、链证书或业务数据。JAR中的第三方依赖保留各自许可信息，Go启动器的许可位于licenses/Go-LICENSE.txt。
