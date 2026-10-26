package com.kewei.parts.infrastructure.storage;

import org.springframework.stereotype.Component;

/**
 * 多链路由：把「业务阶段」映射到对应的链。
 *
 * <p>对应论文 5.2「支撑溯源数据分离存储的跨链协作机制」：生产 / 质检 / 物流三类数据
 * 分存到三条业务子链，主监管链只汇聚摘要。数据库模拟下仅用 chain 编码区分账本边界。</p>
 */
@Component
public class ChainRouter {

    /** 主监管链（默认长安链，可独立配置平台） */
    public static final String MAIN_REGULATOR = "main-regulator";
    /** 生产子链（默认长安链） */
    public static final String SUB_PRODUCTION = "sub-production";
    /** 质检子链（默认长安链） */
    public static final String SUB_QUALITY = "sub-quality";
    /** 物流子链（默认FISCO BCOS） */
    public static final String SUB_LOGISTICS = "sub-logistics";

    /** 事件类型 → 业务子链 */
    public String eventChain(String eventType) {
        if ("quality".equals(eventType)) {
            return SUB_QUALITY;
        }
        if ("logistics".equals(eventType)) {
            return SUB_LOGISTICS;
        }
        return SUB_PRODUCTION;
    }

    /** 凭证类型 → 子链（comprehensive 综合凭证聚合全生命周期，直接锚主监管链） */
    public String vcChain(String vcType) {
        if ("comprehensive".equals(vcType)) {
            return MAIN_REGULATOR;
        }
        if ("quality".equals(vcType)) {
            return SUB_QUALITY;
        }
        if ("transfer".equals(vcType)) {
            return SUB_LOGISTICS;
        }
        return SUB_PRODUCTION;
    }

    /** DID 文档锚定链：单件诞生于生产环节，锚生产子链 */
    public String didChain() {
        return SUB_PRODUCTION;
    }

    /** 是否为业务子链（业务子链存证成功后需中继摘要到主链） */
    public boolean isBusinessChain(String chainId) {
        return SUB_PRODUCTION.equals(chainId)
                || SUB_QUALITY.equals(chainId)
                || SUB_LOGISTICS.equals(chainId);
    }
}
