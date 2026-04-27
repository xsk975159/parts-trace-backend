package com.kewei.parts.module.parts.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kewei.parts.module.parts.dto.PartsCreateDTO;
import com.kewei.parts.module.parts.dto.PartsDidBindDTO;
import com.kewei.parts.module.parts.dto.PartsDidQueryDTO;
import com.kewei.parts.module.parts.dto.PartsQueryDTO;
import com.kewei.parts.module.parts.dto.PartsUpdateDTO;
import com.kewei.parts.module.parts.entity.PartsDid;
import com.kewei.parts.module.parts.entity.PartsInfo;
import com.kewei.parts.module.parts.entity.PartsUnit;
import com.kewei.parts.module.parts.vo.DidGenerateTaskVO;
import com.kewei.parts.module.parts.vo.DidValidateVO;
import com.kewei.parts.module.parts.vo.PartsDidVO;
import com.kewei.parts.module.parts.vo.PartsInfoVO;

import java.util.List;

/**
 * 零部件服务接"
 */
public interface PartsService {

    /**
     * 创建零部"
     */
    PartsInfo createParts(PartsCreateDTO createDTO, Long creatorId);

    /**
     * 更新零部件信"
     */
    void updateParts(Long partsId, PartsUpdateDTO updateDTO);

    /**
     * 获取零部件详"
     */
    PartsInfoVO getPartsInfo(Long partsId);

    /**
     * 根据批次号查询零部件
     */
    PartsInfo getPartsByBatchNumber(String batchNumber);

    /**
     * 分页查询零部件列"
     */
    Page<PartsInfoVO> getPartsList(PartsQueryDTO queryDTO);

    /**
     * 删除零部"
     */
    void deleteParts(Long partsId);

    /**
     * 归档零部"
     */
    void archiveParts(Long partsId);

    /**
     * 生成零部件DID
     */
    PartsDid generatePartsDid(Long partsId);

    /** 异步生成DID任务 */
    DidGenerateTaskVO generatePartsDidAsync(Long partsId);

    /** 查询异步任务进度 */
    DidGenerateTaskVO getDidGenerateTask(String taskId);

    /** DID列表查询 */
    List<PartsDidVO> getPartsDidList(PartsDidQueryDTO queryDTO);

    /** DID文档查询 */
    String getDidDocument(Long didId);

    /** 绑定物理标识 */
    PartsDid bindDid(Long didId, PartsDidBindDTO bindDTO);

    /** 解绑物理标识 */
    PartsDid unbindDid(Long didId);

    /** 冻结DID */
    PartsDid freezeDid(Long didId, String reason);

    /** 解冻DID */
    PartsDid unfreezeDid(Long didId);

    /** DID校验 */
    DidValidateVO validateDid(String did);

    /** 根据批次父记录初始化单件 */
    int initPartsUnits(Long partsId, Integer unitCount);

    /** 查询批次下单件 */
    List<PartsUnit> getUnitsByBatch(String batchNumber);

    /** 为单件生成DID */
    PartsDid generateUnitDid(Long unitId);

    /**
     * 更新零部件状"
     */
    void updatePartsStatus(Long partsId, Integer status);
}





