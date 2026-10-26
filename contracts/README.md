# 智能合约

本文路径相对于 `parts-trace-backend/contracts/`。两种平台共用业务字段与中继签名原文，分别使用不同运行时和部署产物。

| 平台 | 源码 | 运行时 | 产物 |
| --- | --- | --- | --- |
| 长安链 | [trace_anchor/main.go](trace_anchor/main.go) | DockerGo，部署时使用 `DOCKER_GO` | Linux amd64可执行文件及7zip包 |
| FISCO BCOS 3.x | [fisco/TraceAnchor.sol](fisco/TraceAnchor.sol)、[RsaSha256.sol](fisco/RsaSha256.sol) | EVM，Solidity 0.8.11、Istanbul目标 | ABI与部署字节码 |

长安链Go合约不使用TinyGo或WASM；FISCO字节码不能用于DockerGo部署。

## 1. 长安链构建与初始化

在安装Go和7zip的Bash环境执行：

```bash
cd trace_anchor
bash build.sh
```

脚本使用 `contract-sdk-go/v2 v2.3.3`，交叉编译Linux amd64并生成 `trace_anchor/build/trace_anchor.7z`（路径相对于本合约目录）。链节点需要启用Docker VM。

| 初始化参数 | 内容 |
| --- | --- |
| `local_chain_code` | 本合约所属逻辑链，如 `main-regulator` |
| `writer_orgs` | 授权写入组织编码的JSON数组，省略时仅允许创建者组织 |
| `relay_public_keys` | 受信源链编码到X.509 DER RSA公钥Base64的JSON映射 |

接收中继的主链须登记实际源链公钥。配置升级仅允许合约创建者。后端可通过统一合约名或逐链 `contract-name` 调用，须与各链部署一致。

## 2. FISCO构建与部署

```powershell
cd fisco
npm ci --ignore-scripts
npm run compile
```

普通版编译输出为 `fisco/artifacts/TraceAnchor.json`，并更新后端 `src/main/resources/fisco/TraceAnchor.abi.json` 与 `TraceAnchor.bin`。后两个路径相对于后端目录，文件随后端JAR提供。

构造参数包括逻辑链编码、写入账户列表、受信源链及对应RSA公钥模数/指数。可使用后端JAR中的 `FiscoContractDeployer`，由部署清单自动转换公钥；默认只检查，显式增加 `--deploy` 才发送交易。配置、完整命令、固定账户和国密编译要求见[双平台接入说明](../docs/双平台接入说明.md)。

部署账户为所有者，可维护写入账户与受信中继公钥。原写入者或所有者可撤销凭证，普通其他写入者不能冒用撤销权限。

## 3. 共用业务方法

| 方法 | 作用 |
| --- | --- |
| `contract_info` | 返回 `parts-trace-anchor/v2` 及实际逻辑链编码，供后端启动核对 |
| `save_anchor` | 保存业务编号、摘要、CID和DID；相同内容幂等，冲突内容拒绝覆盖 |
| `get_anchor` | 读取存证原文元数据 |
| `append_vc` | 登记DID对应凭证摘要 |
| `get_vc` | 返回凭证摘要或撤销标记字符串0 |
| `revoke_vc` | 撤销凭证；迟到追加不能恢复已撤销凭证 |
| `receive_cross_anchor` | 验证授权中继签名、目标链、时效与重复消息后接收摘要 |
| `get_cross_anchor` | 读取指定源链交易的跨链记录 |

跨链字段为 `source_chain_id`、`source_tx_hash`、`source_block_height`、`business_type`、`business_id`、`parts_did`、`data_hash`、`object_cid`、`target_chain_id`和 `issued_at`。字段名按字典序排列，以UTF-8逐行拼接 `名称=值\n`，执行RSA PKCS#1 v1.5 SHA-256签名。

目标链接受其时间之前300秒至之后60秒的消息。相同源链交易的相同业务内容可重试；冲突内容即使有新签名仍被拒绝。授权中继模型不提供源链轻客户端共识或交易包含证明。

## 4. 测试与部署范围

Go合约在 `trace_anchor/` 执行 `go test ./...`。Solidity先在后端运行 `mvn test`生成SDK向量，再在 `fisco/` 执行 `npm test`。具体范围、记录和前置条件见[测试说明](../docs/测试说明.md)。

旧合约状态不会自动迁入当前v2合约；实际升级和数据迁移应按部署环境处理。
