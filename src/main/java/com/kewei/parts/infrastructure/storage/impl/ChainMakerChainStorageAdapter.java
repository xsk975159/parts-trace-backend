package com.kewei.parts.infrastructure.storage.impl;

import com.kewei.parts.infrastructure.chainmaker.ChainMakerSdkInvoker;
import com.kewei.parts.infrastructure.storage.ChainAnchorRequest;
import com.kewei.parts.infrastructure.storage.ChainStorageAdapter;
import com.kewei.parts.module.parts.entity.BlockchainRecord;
import com.kewei.parts.module.parts.mapper.BlockchainRecordMapper;
import com.kewei.parts.utils.DidUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 长安链模式：SDK 就绪时调用 Wasm 合约；否则回退为库表镜像，保证业务可用。
 */
@Component
@ConditionalOnProperty(prefix = "parts.trace.storage", name = "chain-mode", havingValue = "chainmaker")
public class ChainMakerChainStorageAdapter implements ChainStorageAdapter {

    private static final Logger log = LoggerFactory.getLogger(ChainMakerChainStorageAdapter.class);

    private final AtomicBoolean loggedFallback = new AtomicBoolean(false);

    @Autowired
    private BlockchainRecordMapper blockchainRecordMapper;

    @Autowired
    private DidUtil didUtil;

    @Autowired(required = false)
    private ChainMakerSdkInvoker sdkInvoker;

    @Value("${chainmaker.chain-id:chain1}")
    private String chainId;

    private StoreChainResult persistMirror(ChainAnchorRequest request, String txIdPrefix) {
        long height = System.currentTimeMillis() / 1000;
        String txId = txIdPrefix + UUID.randomUUID().toString().replace("-", "");
        BlockchainRecord record = new BlockchainRecord();
        record.setBusinessType(request.getBusinessType());
        record.setBusinessId(request.getBusinessId());
        record.setTxId(txId);
        record.setBlockHeight(height);
        record.setDataHash(request.getDataHash());
        record.setChainId(chainId);
        record.setBlockHash("block-" + didUtil.sha256Hex(txId).substring(0, 24));
        record.setStatus(1);
        blockchainRecordMapper.insert(record);
        return new StoreChainResult(txId, height);
    }

    @Override
    public StoreChainResult storeAnchor(ChainAnchorRequest request) {
        if (sdkInvoker != null && sdkInvoker.isReady()) {
            try {
                return sdkInvoker.invokeSaveAnchor(request);
            } catch (Exception e) {
                log.warn("save_anchor 上链失败，写入本地镜像: {}", e.getMessage());
            }
        } else if (loggedFallback.compareAndSet(false, true)) {
            log.warn("ChainMaker SDK 未启用或未就绪（设置 chainmaker.sdk.invoke-enabled=true 并配置 sdk_config.yml）。使用 DB 镜像。");
        }
        return persistMirror(request, "chainmaker-fallback-");
    }

    @Override
    public void appendVcIfSupported(String partsDid, String vcId, String vcHash) {
        if (!StringUtils.hasText(partsDid) || !StringUtils.hasText(vcId) || !StringUtils.hasText(vcHash)) {
            return;
        }
        if (sdkInvoker != null && sdkInvoker.isReady()) {
            try {
                sdkInvoker.invokeAppendVc(partsDid, vcId, vcHash);
                return;
            } catch (Exception e) {
                log.warn("append_vc 失败: {}", e.getMessage());
            }
        }
        ChainAnchorRequest mirror = ChainAnchorRequest.builder()
                .businessType("vc_append")
                .businessId((long) (partsDid + ":" + vcId).hashCode())
                .dataHash(vcHash)
                .partsDid(partsDid)
                .build();
        persistMirror(mirror, "vc-append-fallback-");
    }
}
