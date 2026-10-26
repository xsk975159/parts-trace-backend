package com.kewei.parts.module.trace.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.Result;
import com.kewei.parts.module.parts.entity.ChainInfo;
import com.kewei.parts.module.parts.entity.CrossChainRecord;
import com.kewei.parts.module.parts.mapper.ChainInfoMapper;
import com.kewei.parts.module.parts.mapper.CrossChainRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨链存证查询：展示 4 链拓扑（1 主监管链 + 3 业务子链）与主链汇聚的跨链摘要。
 */
@RestController
@RequestMapping("/api/trace/cross-chain")
public class CrossChainController {

    @Autowired
    private ChainInfoMapper chainInfoMapper;

    @Autowired private com.kewei.parts.infrastructure.chain.ChainSdkRouter sdkRouter;

    @Autowired
    private CrossChainRecordMapper crossChainRecordMapper;

    @GetMapping
    public Result<Map<String, Object>> crossChainOverview(@RequestParam(required = false) String did) {
        List<ChainInfo> chains = chainInfoMapper.selectList(
                new LambdaQueryWrapper<ChainInfo>().eq(ChainInfo::getStatus, 1)
                        .orderByAsc(ChainInfo::getId));

        LambdaQueryWrapper<CrossChainRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(did)) {
            wrapper.eq(CrossChainRecord::getPartsDid, did);
        }
        wrapper.orderByAsc(CrossChainRecord::getRelayTime);
        List<CrossChainRecord> records = crossChainRecordMapper.selectList(wrapper);

        for (ChainInfo chain : chains) chain.setChainType(sdkRouter.platform(chain.getChainCode()).name());
        Map<String, Object> result = new HashMap<>();
        result.put("chains", chains);
        result.put("records", records);
        return Result.success(result);
    }
}
