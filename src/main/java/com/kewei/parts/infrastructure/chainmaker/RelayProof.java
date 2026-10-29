package com.kewei.parts.infrastructure.chainmaker;
import com.kewei.parts.infrastructure.storage.ChainRouter;
import com.kewei.parts.utils.RsaUtil;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 授权中继签名，不宣称为轻客户端或Merkle包含证明。公钥在目标合约部署时配置。 */
public final class RelayProof {
    private RelayProof() {}
    public static Map<String,byte[]> arguments(CrossChainAnchorPayload p,long issuedAt,String privateKey) {
        Map<String,String> values=values(p,issuedAt);
        try { values.put("signature",RsaUtil.sign(message(values),privateKey)); }
        catch(Exception e) { throw new IllegalStateException("中继签名失败",e); }
        Map<String,byte[]> args=new HashMap<>();values.forEach((k,v)->args.put(k,v.getBytes(StandardCharsets.UTF_8)));return args;
    }
    public static Map<String,String> values(CrossChainAnchorPayload p,long issuedAt) {
        Map<String,String> values=new TreeMap<>();
        values.put("source_chain_id",p.getSourceChainId()); values.put("source_tx_hash",p.getSourceTxHash());
        values.put("source_block_height",String.valueOf(p.getSourceBlockHeight()));values.put("business_type",p.getBusinessType());
        values.put("business_id",String.valueOf(p.getBusinessId()));values.put("parts_did",p.getPartsDid());
        values.put("data_hash",p.getDataHash());values.put("object_cid",p.getObjectCid()==null?"":p.getObjectCid());
        values.put("target_chain_id",ChainRouter.MAIN_REGULATOR);values.put("issued_at",String.valueOf(issuedAt));
        message(values);return values;
    }
    public static String message(Map<String,String> values) {
        StringBuilder message=new StringBuilder();
        new TreeMap<>(values).forEach((key,value)->{
            if(value==null || value.contains("\n") || value.contains("\r")) throw new IllegalArgumentException("跨链参数不合法");
            message.append(key).append('=').append(value).append('\n');
        });
        return message.toString();
    }
}
