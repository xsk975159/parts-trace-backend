package com.kewei.parts.infrastructure.desktop;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.kewei.parts.infrastructure.chain.*;
import com.kewei.parts.infrastructure.chainmaker.*;
import com.kewei.parts.infrastructure.fisco.*;
import com.kewei.parts.infrastructure.storage.ChainAnchorRequest;
import com.kewei.parts.infrastructure.storage.ChainStorageAdapter.StoreChainResult;
import org.chainmaker.pb.common.ContractOuterClass;
import org.chainmaker.pb.common.Request;
import org.chainmaker.pb.common.ResultOuterClass;
import org.chainmaker.sdk.*;
import org.chainmaker.sdk.config.SdkConfig;
import org.chainmaker.sdk.utils.SdkUtils;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;
import org.springframework.core.io.DefaultResourceLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** 本地 Docker 网络的部署与验收入口；独立执行，不启动 Web 服务或操作业务数据库。 */
public final class HeterogeneousBootstrap {
    private static final long TIMEOUT = 60000;
    private static final Map<String, String> CHAINS = new LinkedHashMap<>();
    static {
        CHAINS.put("chain1", "main-regulator");
        CHAINS.put("chain2", "sub-production");
        CHAINS.put("chain3", "sub-quality");
    }
    private HeterogeneousBootstrap() {}

    public static void main(String[] args) {
        try {
            if (args.length != 2) throw new IllegalArgumentException("用法：account|deploy|verify 状态目录");
            Path state = Paths.get(args[1]).toAbsolutePath();
            switch (args[0]) {
                case "account":
                    String hex = text(state.resolve("sdk/fisco/account.hex")).trim();
                    if (!hex.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("固定账户格式错误");
                    String account = new CryptoSuite(0).getKeyPairFactory().createKeyPair(hex).getAddress();
                    save(state.resolve("sdk/fisco-account.json"), Collections.singletonMap("address", account));
                    break;
                case "deploy": deploy(state); break;
                case "recover-cm": recoverChainMaker(state); break;
                case "verify": verify(state); break;
                default: throw new IllegalArgumentException("未知操作");
            }
            System.exit(0);
        } catch (Exception | LinkageError error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static ChainClient chainClient(Path configuration) throws Exception {
        SdkConfig config;
        try (InputStream stream = Files.newInputStream(configuration)) {
            config = new Yaml().loadAs(stream, SdkConfig.class);
        }
        if (config.getChainClient() == null) config.setChainClient(config.getChain_client());
        ChainMakerKeyMaterial.prepare(config.getChainClient());
        return ChainManager.getInstance().createChainClient(config);
    }

    private static void awaitChain(ChainClient client) throws Exception {
        long deadline = System.currentTimeMillis() + 180000;
        Exception last = null;
        do {
            try { client.getChainInfo(5000); return; }
            catch (Exception error) { last = error; Thread.sleep(2000); }
        } while (System.currentTimeMillis() < deadline);
        throw new IllegalStateException("长安链尚未就绪：" + client.getChainId(), last);
    }

    private static Map<String, String> relayKeys(Path state) throws Exception {
        String key = object(state.resolve("sdk/relay.json")).getString("publicKey");
        Map<String, String> keys = new LinkedHashMap<>();
        for (String code : Arrays.asList("sub-production", "sub-quality", "sub-logistics")) keys.put(code, key);
        return keys;
    }

    private static void deploy(Path state) throws Exception {
        Path record = state.resolve("deployment.json");
        JSONObject deployments = Files.exists(record) ? object(record) : new JSONObject(true);
        for (Map.Entry<String, String> ledger : CHAINS.entrySet()) {
            String chainId = ledger.getKey(), code = ledger.getValue();
            ChainClient client = chainClient(state.resolve("sdk/cm/" + chainId + "-admin.yml"));
            try {
                awaitChain(client);
                if (!chainId.equals(client.getChainId())) throw new IllegalStateException("长安链身份不符");
                if (deployments.containsKey(code)) {
                    checkCmInfo(client, code);
                    continue;
                }
                Path pending = state.resolve("deploy-" + code + ".pending");
                // 提交前保留标记。异常后须核对链上结果，不能因本地没有地址而重复部署。
                beginDeployment(pending, code);
                Map<String, byte[]> parameters = new LinkedHashMap<>();
                ContractArguments.put(parameters, "local_chain_code", code);
                ContractArguments.put(parameters, "writer_orgs", "[\"wx-org1.chainmaker.org\"]");
                ContractArguments.put(parameters, "relay_public_keys", JSON.toJSONString(relayKeys(state)));
                Request.Payload payload = client.createContractCreatePayload("trace_anchor", "2.0.0",
                        Files.readAllBytes(Paths.get("/opt/contracts/trace_anchor.7z")),
                        ContractOuterClass.RuntimeType.DOCKER_GO, parameters);
                if (payload.getTxId().isEmpty()) payload = payload.toBuilder().setTxId(org.chainmaker.sdk.utils.Utils.generateTxId()).build();
                save(pending, Collections.singletonMap("transactionHash", payload.getTxId()));
                ResultOuterClass.TxResponse response = client.sendContractManageRequest(payload,
                        SdkUtils.getEndorsers(payload, new User[]{client.getClientUser()}), TIMEOUT, TIMEOUT);
                ChainMakerSdkInvoker.check(response);
                long height = ChainMakerSdkInvoker.confirmedHeight(client, response, payload.getContractName(), payload.getMethod(), TIMEOUT);
                JSONObject receipt = new JSONObject(true);
                receipt.put("physicalChainId", chainId);
                receipt.put("contractName", "trace_anchor");
                receipt.put("transactionHash", response.getTxId());
                receipt.put("blockNumber", height);
                // 先持久化回执，再做查询；查询失败时保留地址供恢复，不重新部署。
                deployments.put(code, receipt);
                save(record, deployments);
                Files.delete(pending);
                checkCmInfo(client, code);
                System.out.println("已部署：" + code + "，交易 " + response.getTxId());
            } finally { client.stop(); }
        }
        ChainNetworkProperties.Network network = fiscoNetwork(state, null);
        try (FiscoConnection connection = new FiscoConnectionFactory(new DefaultResourceLoader()).connect(network)) {
            String code = "sub-logistics";
            if (deployments.containsKey(code)) {
                checkFiscoInfo(connection, deployments.getJSONObject(code).getString("contractAddress"));
                return;
            }
            Path pending = state.resolve("deploy-sub-logistics.pending");
            beginDeployment(pending, code);
            FiscoContractDeployer.Manifest manifest = new FiscoContractDeployer.Manifest();
            manifest.setSdkConfig(network.getSdkConfig());
            manifest.setExpectedChainId("chain0");
            manifest.setExpectedAccount(network.getExpectedAccount());
            manifest.setLocalChainCode(code);
            manifest.setWriterAddresses(Collections.singletonList(connection.account()));
            manifest.setRelayPublicKeys(relayKeys(state));
            String binary;
            try (InputStream stream = HeterogeneousBootstrap.class.getResourceAsStream("/fisco/TraceAnchor.bin")) {
                if (stream == null) throw new IllegalStateException("缺少 FISCO 合约字节码");
                binary = org.springframework.util.StreamUtils.copyToString(stream, StandardCharsets.UTF_8).trim();
            }
            TransactionResponse response = connection.deploy(binary, FiscoContractDeployer.constructorArguments(manifest));
            FiscoContractDeployer.validateDeployment(response, connection.account());
            JSONObject receipt = new JSONObject(true);
            receipt.put("physicalChainId", "chain0");
            receipt.put("group", "group0");
            receipt.put("contractAddress", response.getContractAddress());
            receipt.put("transactionHash", response.getTransactionReceipt().getTransactionHash());
            receipt.put("blockNumber", response.getTransactionReceipt().getBlockNumber());
            deployments.put(code, receipt);
            save(record, deployments);
            Files.delete(pending);
            checkFiscoInfo(connection, response.getContractAddress());
            System.out.println("已部署：" + code + "，合约 " + response.getContractAddress());
        }
    }

    static void beginDeployment(Path pending, String code) throws Exception {
        if (Files.exists(pending)) throw new IllegalStateException("上次部署结果待核查：" + code
                + "。请保留 pending 文件和节点数据，核对交易后再恢复。");
        Files.write(pending, (Instant.now().toString() + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
    }

    /** 显式恢复中断的长安链部署，仅核对既有交易，不重新发送部署。 */
    private static void recoverChainMaker(Path state) throws Exception {
        Path record = state.resolve("deployment.json");
        JSONObject deployments = Files.exists(record) ? object(record) : new JSONObject(true);
        for (Map.Entry<String, String> ledger : CHAINS.entrySet()) {
            String chainId = ledger.getKey(), code = ledger.getValue();
            Path pending = state.resolve("deploy-" + code + ".pending");
            if (!Files.exists(pending) || deployments.containsKey(code)) continue;
            ChainClient client = chainClient(state.resolve("sdk/cm/" + chainId + "-admin.yml"));
            try {
                awaitChain(client);
                checkCmInfo(client, code);
                long height = client.getCurrentBlockHeight(TIMEOUT);
                if (height > 1000) throw new IllegalStateException("账本较大，请按pending中的交易编号人工核查，未扫描或重新部署");
                JSONObject found = null;
                for (long number = 1; number <= height; number++) {
                    for (org.chainmaker.pb.common.ChainmakerTransaction.Transaction tx : client.getBlockByHeight(number, false, TIMEOUT).getBlock().getTxsList()) {
                        if (!tx.hasPayload() || !"CONTRACT_MANAGE".equals(tx.getPayload().getContractName())
                                || !"INIT_CONTRACT".equals(tx.getPayload().getMethod())) continue;
                        Map<String, String> parameters = new HashMap<>();
                        for (Request.KeyValuePair pair : tx.getPayload().getParametersList()) parameters.put(pair.getKey(), pair.getValue().toStringUtf8());
                        if (!"trace_anchor".equals(parameters.get("CONTRACT_NAME")) || !code.equals(parameters.get("local_chain_code"))) continue;
                        if (!tx.hasResult() || tx.getResult().getCode() != ResultOuterClass.TxStatusCode.SUCCESS
                                || !tx.getResult().hasContractResult() || tx.getResult().getContractResult().getCode() != 0)
                            throw new IllegalStateException("找到的部署交易没有执行成功");
                        if (found != null) throw new IllegalStateException("找到多笔候选部署交易，请人工核查");
                        found = new JSONObject(true);
                        found.put("physicalChainId", chainId); found.put("contractName", "trace_anchor");
                        found.put("transactionHash", tx.getPayload().getTxId()); found.put("blockNumber", number);
                    }
                }
                if (found == null) throw new IllegalStateException("尚未找到可确认的部署交易，未重新部署");
                deployments.put(code, found);
                save(record, deployments);
                Files.delete(pending);
                System.out.println("已核对并恢复部署回执：" + code + "，交易 " + found.getString("transactionHash"));
            } finally { client.stop(); }
        }
    }

    private static void checkCmInfo(ChainClient client, String code) throws Exception {
        ResultOuterClass.TxResponse response = client.queryContract("trace_anchor", "contract_info", "", Collections.emptyMap(), TIMEOUT);
        ChainMakerSdkInvoker.check(response);
        checkInfo(response.getContractResult().getResult().toStringUtf8(), code);
    }

    static void checkInfo(String json, String code) {
        JSONObject info = JSON.parseObject(json);
        if (info == null || !"parts-trace-anchor/v2".equals(info.getString("schema")) || !code.equals(info.getString("chainCode")))
            throw new IllegalStateException("合约版本或逻辑链身份不符：" + code);
    }

    private static void checkFiscoInfo(FiscoConnection connection, String address) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT;
        CallResponse info;
        while (true) {
            try { info = connection.queryAt(address, "contract_info", Collections.emptyList()); break; }
            catch (org.fisco.bcos.sdk.v3.client.exceptions.ClientException error) {
                // 节点重启期间只重试无副作用的查询，不重发部署或业务交易。
                if ((error.getErrorCode() != -4006 && error.getErrorCode() != -4010) || System.currentTimeMillis() >= deadline) throw error;
                Thread.sleep(3000);
            }
        }
        if (info == null || info.getReturnCode() != 0 || !Arrays.asList("parts-trace-anchor/v2", "sub-logistics").equals(info.getReturnObject()))
            throw new IllegalStateException("FISCO 合约版本或逻辑链身份不符");
    }

    private static ChainNetworkProperties.Network fiscoNetwork(Path state, String address) throws Exception {
        ChainNetworkProperties.Network network = new ChainNetworkProperties.Network();
        network.setPlatform(ChainPlatform.FISCO_BCOS);
        network.setSdkConfig(state.resolve("sdk/fisco.toml").toUri().toString());
        network.setExpectedChainId("chain0");
        network.setExpectedAccount(object(state.resolve("sdk/fisco-account.json")).getString("address"));
        network.setGroup("group0");
        network.setContractAddress(address);
        return network;
    }

    private static void verify(Path state) throws Exception {
        JSONObject deployment = object(state.resolve("deployment.json"));
        if (deployment.size() != 4) throw new IllegalStateException("四条账本的部署尚未完成");
        ChainNetworkProperties properties = new ChainNetworkProperties();
        properties.setStorageMode("real");
        properties.setRelayPrivateKey(object(state.resolve("sdk/relay.json")).getString("privateKey"));
        for (Map.Entry<String, String> chain : CHAINS.entrySet()) {
            ChainNetworkProperties.Network network = properties.network(chain.getValue());
            network.setSdkConfig(state.resolve("sdk/cm/" + chain.getKey() + ".yml").toUri().toString());
            network.setExpectedChainId(chain.getKey());
        }
        properties.getNetworks().put("sub-logistics", fiscoNetwork(state, deployment.getJSONObject("sub-logistics").getString("contractAddress")));
        properties.validate();
        ChainMakerSdkInvoker cm = new ChainMakerSdkInvoker(new DefaultResourceLoader(), properties);
        FiscoBcosDriver fisco = new FiscoBcosDriver(properties, new FiscoConnectionFactory(new DefaultResourceLoader()));
        try {
            cm.init();
            fisco.initialize();
            ChainSdkRouter router = new ChainSdkRouter(properties, Arrays.asList(cm, fisco));
            router.verifyBindings();
            Map<String, ChainDriver> drivers = new LinkedHashMap<>();
            for (String code : CHAINS.values()) drivers.put(code, cm);
            drivers.put("sub-logistics", fisco);
            Set<String> physical = new HashSet<>();
            List<Object> checks = new ArrayList<>();
            long id = System.currentTimeMillis();
            for (Map.Entry<String, ChainDriver> entry : drivers.entrySet()) {
                String code = entry.getKey();
                ChainDriver driver = entry.getValue();
                checkInfo(driver.query(code, "contract_info", Collections.emptyMap()), code);
                if (!physical.add(driver.physicalIdentity(code))) throw new IllegalStateException("逻辑链错误地共用同一账本");
                String hash = hex(MessageDigest.getInstance("SHA-256").digest((code + ":" + id).getBytes(StandardCharsets.UTF_8)));
                ChainAnchorRequest request = ChainAnchorRequest.builder().businessType("docker_smoke").businessId(++id)
                        .partsDid("did:parts:docker-" + id).dataHash(hash).objectCid("docker-test:" + id).chainId(code).build();
                StoreChainResult source = router.storeAnchor(request);
                JSONObject stored = JSON.parseObject(driver.query(code, "get_anchor", ContractArguments.anchorKey("docker_smoke", id)));
                if (stored == null || !hash.equals(stored.getString("data_hash"))) throw new IllegalStateException("存证读写不一致：" + code);
                checks.add(receipt("save_anchor", code, source));
                String vc = "docker-vc-" + id;
                StoreChainResult appended = driver.invoke(code, "append_vc", ContractArguments.vc(request.getPartsDid(), vc, hash));
                if (!hash.equals(driver.query(code, "get_vc", ContractArguments.vc(request.getPartsDid(), vc, null))))
                    throw new IllegalStateException("VC 写入核验失败：" + code);
                checks.add(receipt("append_vc", code, appended));
                StoreChainResult revoked = driver.invoke(code, "revoke_vc", ContractArguments.vc(request.getPartsDid(), vc, null));
                if (!"0".equals(driver.query(code, "get_vc", ContractArguments.vc(request.getPartsDid(), vc, null))))
                    throw new IllegalStateException("VC 撤销核验失败：" + code);
                checks.add(receipt("revoke_vc", code, revoked));
                if (!"main-regulator".equals(code)) {
                    CrossChainAnchorPayload payload = CrossChainAnchorPayload.builder().businessType("docker_smoke").businessId(id)
                            .partsDid(request.getPartsDid()).dataHash(hash).objectCid(request.getObjectCid()).sourceChainId(code)
                            .sourceTxHash(source.getTxId()).sourceBlockHeight(source.getBlockHeight()).build();
                    driver.verifyAnchorTransaction(code, payload);
                    Map<String, byte[]> proof = RelayProof.arguments(payload, Instant.now().getEpochSecond(), properties.getRelayPrivateKey());
                    StoreChainResult target = router.relay(payload);
                    Map<String, byte[]> lookup = new LinkedHashMap<>();
                    ContractArguments.put(lookup, "source_chain_id", code);
                    ContractArguments.put(lookup, "source_tx_hash", source.getTxId());
                    JSONObject cross = JSON.parseObject(cm.query("main-regulator", "get_cross_anchor", lookup));
                    if (cross == null || !hash.equals(cross.getString("data_hash"))) throw new IllegalStateException("跨链存证查询不一致：" + code);
                    checks.add(receipt("receive_cross_anchor:" + code, "main-regulator", target));
                    Map<String, byte[]> tampered = new LinkedHashMap<>(proof);
                    ContractArguments.put(tampered, "data_hash", hex(MessageDigest.getInstance("SHA-256").digest((hash + "tampered").getBytes(StandardCharsets.UTF_8))));
                    boolean rejected = false;
                    try { cm.invoke("main-regulator", "receive_cross_anchor", tampered); }
                    catch (ChainClientException expected) {
                        if (!expected.getMessage().contains("relay proof rejected")) throw expected;
                        rejected = true;
                    }
                    if (!rejected) throw new IllegalStateException("主链未拒绝摘要被篡改的中继消息");
                    JSONObject unchanged = JSON.parseObject(cm.query("main-regulator", "get_cross_anchor", lookup));
                    if (unchanged == null || !hash.equals(unchanged.getString("data_hash"))) throw new IllegalStateException("失败交易改变了已确认存证");
                    checks.add(Collections.singletonMap("tamperedRelayRejected", code));
                }
            }
            JSONObject result = new JSONObject(true);
            result.put("verifiedAt", Instant.now().toString());
            result.put("mode", "real");
            result.put("trustModel", "authorized-relay-signature");
            result.put("physicalLedgers", physical);
            result.put("checks", checks);
            result.put("passed", true);
            save(state.resolve("verification.json"), result);
            System.out.println("四条真实账本存证、VC 操作、三条从链至主链的中继及篡改拒绝检查通过。");
        } finally { fisco.close(); cm.close(); }
    }

    private static Map<String, Object> receipt(String operation, String code, StoreChainResult result) {
        if (result.isSimulated() || result.getTxId() == null || result.getTxId().isEmpty()
                || result.getBlockHeight() == null || result.getBlockHeight() <= 0) throw new IllegalStateException("缺少实链确认回执");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("operation", operation); item.put("chainCode", code);
        item.put("transactionHash", result.getTxId()); item.put("blockNumber", result.getBlockHeight());
        return item;
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02x", value & 255));
        return result.toString();
    }
    private static String text(Path path) throws Exception { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    private static JSONObject object(Path path) throws Exception { return JSON.parseObject(text(path)); }
    private static void save(Path path, Object value) throws Exception {
        Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.write(temporary, (JSON.toJSONString(value, true) + "\n").getBytes(StandardCharsets.UTF_8));
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
