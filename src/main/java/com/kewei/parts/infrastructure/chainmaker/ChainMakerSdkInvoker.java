package com.kewei.parts.infrastructure.chainmaker;

import com.kewei.parts.infrastructure.storage.ChainAnchorRequest;
import com.kewei.parts.infrastructure.storage.ChainStorageAdapter;
import org.chainmaker.pb.common.ResultOuterClass;
import org.chainmaker.sdk.ChainClient;
import org.chainmaker.sdk.ChainClientException;
import org.chainmaker.sdk.ChainManager;
import org.chainmaker.sdk.config.SdkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 长安链 Java SDK 调用封装。仅在 {@code chainmaker.sdk.invoke-enabled=true} 且 {@code sdk_config.yml} 有效时初始化。
 */
@Component
@ConditionalOnProperty(prefix = "chainmaker.sdk", name = "invoke-enabled", havingValue = "true")
public class ChainMakerSdkInvoker {

    private static final Logger log = LoggerFactory.getLogger(ChainMakerSdkInvoker.class);

    private final ResourceLoader resourceLoader;

    @Value("${chainmaker.config-path:classpath:chainmaker/sdk_config.yml}")
    private String configPath;

    @Value("${chainmaker.wasm-contract-name:trace_anchor}")
    private String wasmContractName;

    @Value("${chainmaker.sdk.invoke-timeout-ms:60000}")
    private long invokeTimeoutMs;

    private volatile ChainClient chainClient;

    public ChainMakerSdkInvoker(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            Resource resource = resourceLoader.getResource(configPath);
            if (!resource.exists()) {
                log.warn("ChainMaker sdk_config 不存在: {}，跳过 SDK 初始化", configPath);
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                Yaml yaml = new Yaml();
                SdkConfig sdkConfig = yaml.loadAs(in, SdkConfig.class);
                if (sdkConfig == null) {
                    log.warn("ChainMaker sdk_config 解析为空");
                    return;
                }
                if (sdkConfig.getChainClient() == null && sdkConfig.getChain_client() != null) {
                    sdkConfig.setChainClient(sdkConfig.getChain_client());
                }
                this.chainClient = ChainManager.getInstance().createChainClient(sdkConfig);
                log.info("ChainMaker SDK 初始化成功，合约名默认: {}", wasmContractName);
            }
        } catch (Exception e) {
            log.error("ChainMaker SDK 初始化失败（部署链并检查 sdk_config / 证书路径后重试）", e);
            this.chainClient = null;
        }
    }

    public boolean isReady() {
        return chainClient != null;
    }

    /**
     * 调用合约 {@code save_anchor}。
     */
    public ChainStorageAdapter.StoreChainResult invokeSaveAnchor(ChainAnchorRequest req) throws ChainClientException {
        Map<String, byte[]> args = saveAnchorArgs(req);
        return invokeContract(wasmContractName, "save_anchor", args);
    }

    /**
     * 调用合约 {@code append_vc}。
     */
    public void invokeAppendVc(String partsDid, String vcId, String vcHash) throws ChainClientException {
        if (partsDid == null || vcId == null || vcHash == null) {
            return;
        }
        Map<String, byte[]> args = new HashMap<>();
        args.put("parts_did", partsDid.getBytes(StandardCharsets.UTF_8));
        args.put("vc_id", vcId.getBytes(StandardCharsets.UTF_8));
        args.put("vc_hash", vcHash.getBytes(StandardCharsets.UTF_8));
        invokeContract(wasmContractName, "append_vc", args);
    }

    /**
     * 调用合约 {@code receive_cross_anchor}（跨链中继在上链成功后调用）。
     */
    public ChainStorageAdapter.StoreChainResult invokeReceiveCrossAnchor(CrossChainAnchorPayload payload)
            throws ChainClientException {
        Map<String, byte[]> args = new HashMap<>();
        putUtf8(args, "source_chain_id", payload.getSourceChainId());
        putUtf8(args, "source_tx_hash", payload.getSourceTxHash());
        putUtf8(args, "parts_did", payload.getPartsDid());
        putUtf8(args, "data_hash", payload.getDataHash());
        if (payload.getObjectCid() != null) {
            putUtf8(args, "object_cid", payload.getObjectCid());
        }
        return invokeContract(wasmContractName, "receive_cross_anchor", args);
    }

    private static Map<String, byte[]> saveAnchorArgs(ChainAnchorRequest req) {
        Map<String, byte[]> args = new HashMap<>();
        putUtf8(args, "business_type", req.getBusinessType());
        args.put("business_id", String.valueOf(req.getBusinessId()).getBytes(StandardCharsets.UTF_8));
        putUtf8(args, "data_hash", req.getDataHash());
        if (req.getObjectCid() != null && !req.getObjectCid().isEmpty()) {
            putUtf8(args, "object_cid", req.getObjectCid());
        }
        if (req.getPartsDid() != null && !req.getPartsDid().isEmpty()) {
            putUtf8(args, "parts_did", req.getPartsDid());
        }
        return args;
    }

    private static void putUtf8(Map<String, byte[]> args, String k, String v) {
        if (v != null) {
            args.put(k, v.getBytes(StandardCharsets.UTF_8));
        }
    }

    private ChainStorageAdapter.StoreChainResult invokeContract(String contractName, String method, Map<String, byte[]> args)
            throws ChainClientException {
        if (chainClient == null) {
            throw new ChainClientException("ChainClient 未初始化");
        }
        try {
            ResultOuterClass.TxResponse resp = chainClient.invokeContract(
                    contractName,
                    method,
                    "",
                    args,
                    invokeTimeoutMs,
                    invokeTimeoutMs);
            if (resp.getCode() != ResultOuterClass.TxStatusCode.SUCCESS) {
                throw new ChainClientException("合约调用失败: code=" + resp.getCode() + " msg=" + resp.getMessage());
            }
            return new ChainStorageAdapter.StoreChainResult(resp.getTxId(), resp.getTxBlockHeight());
        } catch (ChainClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ChainClientException(e.getMessage());
        }
    }
}
