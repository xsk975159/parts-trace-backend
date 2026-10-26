package com.kewei.parts.module.parts.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.infrastructure.storage.ChainAnchorRequest;
import com.kewei.parts.infrastructure.storage.ChainRouter;
import com.kewei.parts.infrastructure.storage.ChainStorageAdapter;
import com.kewei.parts.infrastructure.storage.ObjectStorageAdapter;
import com.kewei.parts.module.parts.dto.PartsCreateDTO;
import com.kewei.parts.module.parts.dto.PartsDidBindDTO;
import com.kewei.parts.module.parts.dto.PartsDidQueryDTO;
import com.kewei.parts.module.parts.dto.PartsQueryDTO;
import com.kewei.parts.module.parts.dto.PartsUpdateDTO;
import com.kewei.parts.module.parts.entity.PartsCategory;
import com.kewei.parts.module.parts.entity.PartsDid;
import com.kewei.parts.module.parts.entity.PartsInfo;
import com.kewei.parts.module.parts.entity.PartsUnit;
import com.kewei.parts.module.parts.mapper.PartsCategoryMapper;
import com.kewei.parts.module.parts.mapper.PartsDidMapper;
import com.kewei.parts.module.parts.mapper.PartsInfoMapper;
import com.kewei.parts.module.parts.mapper.PartsUnitMapper;
import com.kewei.parts.module.parts.service.PartsService;
import com.kewei.parts.module.parts.vo.DidGenerateTaskVO;
import com.kewei.parts.module.parts.vo.DidValidateVO;
import com.kewei.parts.module.parts.vo.PartsDidVO;
import com.kewei.parts.module.parts.vo.PartsInfoVO;
import com.kewei.parts.utils.DidUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 零部件服务实现类
 */
@Service
public class PartsServiceImpl implements PartsService {
    @Autowired private com.kewei.parts.module.identity.mapper.SysUserDidMapper userDidMapper;
    @Autowired private com.kewei.parts.security.CurrentActor actor;
    @Autowired private com.kewei.parts.module.parts.service.DidDocumentVerifier documentVerifier;
    @Autowired private com.kewei.parts.infrastructure.storage.AuditRecorder audits;
    @Autowired private com.kewei.parts.module.anomaly.service.QualitySafetyService safety;
    @Autowired private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private static final Logger log = LoggerFactory.getLogger(PartsServiceImpl.class);

    @Autowired
    private PartsInfoMapper partsInfoMapper;

    @Autowired
    private PartsCategoryMapper partsCategoryMapper;

    @Autowired
    private PartsDidMapper partsDidMapper;

    @Autowired
    private PartsUnitMapper partsUnitMapper;

    @Autowired
    private DidUtil didUtil;

    @Autowired
    private ObjectStorageAdapter objectStorageAdapter;

    @Autowired
    private ChainStorageAdapter chainStorageAdapter;

    @Autowired
    private ChainRouter chainRouter;

    @Value("${did.chain-id:chain1}")
    private String didNetwork;

    private final Map<String, DidGenerateTaskVO> didTaskStore = new ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PartsInfo createParts(PartsCreateDTO createDTO, Long creatorId) {
        actor.requireRoles("manufacturer");
        creatorId=actor.id();
        // 检查零部件编码是否已存"
        LambdaQueryWrapper<PartsInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PartsInfo::getPartsCode, createDTO.getPartsCode());
        PartsInfo existParts = partsInfoMapper.selectOne(wrapper);
        if (existParts != null) {
            throw new BusinessException("零部件编码已存在");
        }

        // 同一批次允许多条零部件主数据（不同 parts_code），不再校验批次号全局唯一

        // 检查分类是否存"
        PartsCategory category = partsCategoryMapper.selectById(createDTO.getCategoryId());
        if (category == null) {
            throw new BusinessException("零部件分类不存在");
        }

        // 创建零部"
        PartsInfo partsInfo = new PartsInfo();
        BeanUtils.copyProperties(createDTO, partsInfo);
        partsInfo.setStatus(1); // 默认状态为正常
        partsInfo.setCreatorId(creatorId);

        int result = partsInfoMapper.insert(partsInfo);
        if (result <= 0) {
            throw new BusinessException("创建零部件失");
        }

        // 创建批次后自动初始化单件，数量默认跟随批次数量（上限在 initPartsUnits 内控制）
        initPartsUnits(partsInfo.getId(), null);

        return partsInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateParts(Long partsId, PartsUpdateDTO updateDTO) {
        requireOwner(partsId);
        PartsInfo partsInfo = partsInfoMapper.selectById(partsId);
        if (partsInfo == null) {
            throw new BusinessException("零部件不存在");
        }

        // 更新零部件信"
        if (StringUtils.hasText(updateDTO.getPartsName())) {
            partsInfo.setPartsName(updateDTO.getPartsName());
        }
        if (updateDTO.getCategoryId() != null) {
            partsInfo.setCategoryId(updateDTO.getCategoryId());
        }
        if (StringUtils.hasText(updateDTO.getSpecification())) {
            partsInfo.setSpecification(updateDTO.getSpecification());
        }
        if (updateDTO.getQuantity() != null) {
            partsInfo.setQuantity(updateDTO.getQuantity());
        }
        if (updateDTO.getWeight() != null) {
            partsInfo.setWeight(updateDTO.getWeight());
        }
        if (StringUtils.hasText(updateDTO.getMaterial())) {
            partsInfo.setMaterial(updateDTO.getMaterial());
        }
        if (StringUtils.hasText(updateDTO.getManufacturer())) {
            partsInfo.setManufacturer(updateDTO.getManufacturer());
        }
        if (updateDTO.getProductionDate() != null) {
            partsInfo.setProductionDate(updateDTO.getProductionDate());
        }
        if (updateDTO.getExpiryDate() != null) {
            partsInfo.setExpiryDate(updateDTO.getExpiryDate());
        }
        if (StringUtils.hasText(updateDTO.getQualityStandard())) {
            partsInfo.setQualityStandard(updateDTO.getQualityStandard());
        }
        if (StringUtils.hasText(updateDTO.getImageUrl())) {
            partsInfo.setImageUrl(updateDTO.getImageUrl());
        }
        if (StringUtils.hasText(updateDTO.getDescription())) {
            partsInfo.setDescription(updateDTO.getDescription());
        }

        int result = partsInfoMapper.updateById(partsInfo);
        if (result <= 0) {
            throw new BusinessException("更新零部件失");
        }
    }

    @Override
    public PartsInfoVO getPartsInfo(Long partsId) {
        PartsInfo partsInfo = partsInfoMapper.selectById(partsId);
        if (partsInfo == null) {
            throw new BusinessException("零部件不存在");
        }

        PartsInfoVO vo = new PartsInfoVO();
        BeanUtils.copyProperties(partsInfo, vo);

        // 查询分类名称
        PartsCategory category = partsCategoryMapper.selectById(partsInfo.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getCategoryName());
        }

        // 查询DID信息
        LambdaQueryWrapper<PartsDid> didWrapper = new LambdaQueryWrapper<>();
        didWrapper.eq(PartsDid::getPartsId, partsId);
        PartsDid partsDid = partsDidMapper.selectOne(didWrapper);
        if (partsDid != null) {
            vo.setDid(partsDid.getDid());
            vo.setDidBindStatus(partsDid.getBindStatus());
        }

        // 设置状态名"
        vo.setStatusName(getStatusName(partsInfo.getStatus()));

        return vo;
    }

    @Override
    public PartsInfo getPartsByBatchNumber(String batchNumber) {
        LambdaQueryWrapper<PartsInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PartsInfo::getBatchNumber, batchNumber)
                .orderByDesc(PartsInfo::getCreateTime)
                .orderByDesc(PartsInfo::getId)
                .last("LIMIT 1");
        List<PartsInfo> list = partsInfoMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            throw new BusinessException("零部件不存在");
        }
        return list.get(0);
    }

    @Override
    public Page<PartsInfoVO> getPartsList(PartsQueryDTO queryDTO) {
        Page<PartsInfo> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        
        LambdaQueryWrapper<PartsInfo> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(queryDTO.getPartsName())) {
            wrapper.like(PartsInfo::getPartsName, queryDTO.getPartsName());
        }
        if (StringUtils.hasText(queryDTO.getBatchNumber())) {
            wrapper.eq(PartsInfo::getBatchNumber, queryDTO.getBatchNumber());
        }
        if (queryDTO.getCategoryId() != null) {
            wrapper.eq(PartsInfo::getCategoryId, queryDTO.getCategoryId());
        }
        if (StringUtils.hasText(queryDTO.getManufacturer())) {
            wrapper.like(PartsInfo::getManufacturer, queryDTO.getManufacturer());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(PartsInfo::getStatus, queryDTO.getStatus());
        }
        
        wrapper.orderByDesc(PartsInfo::getCreateTime)
                .orderByDesc(PartsInfo::getId);
        
        Page<PartsInfo> partsPage = partsInfoMapper.selectPage(page, wrapper);
        
        // 转换为VO
        Page<PartsInfoVO> voPage = new Page<>(partsPage.getCurrent(), partsPage.getSize(), partsPage.getTotal());
        voPage.setRecords(partsPage.getRecords().stream().map(parts -> {
            PartsInfoVO vo = new PartsInfoVO();
            BeanUtils.copyProperties(parts, vo);
            vo.setStatusName(getStatusName(parts.getStatus()));
            return vo;
        }).collect(java.util.stream.Collectors.toList()));
        
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteParts(Long partsId) {
        requireOwner(partsId);
        if(partsDidMapper.selectCount(new LambdaQueryWrapper<PartsDid>().eq(PartsDid::getPartsId,partsId))>0)
            throw new BusinessException("已生成DID的零部件不能删除，请使用归档保留追溯记录");
        PartsInfo partsInfo = partsInfoMapper.selectById(partsId);
        if (partsInfo == null) {
            throw new BusinessException("零部件不存在");
        }

        int result = partsInfoMapper.deleteById(partsId);
        if (result <= 0) {
            throw new BusinessException("删除零部件失");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveParts(Long partsId) {
        requireOwner(partsId);
        PartsInfo partsInfo = partsInfoMapper.selectById(partsId);
        if (partsInfo == null) {
            throw new BusinessException("零部件不存在");
        }

        partsInfo.setStatus(2); // 状态设置为已归"
        int result = partsInfoMapper.updateById(partsInfo);
        if (result <= 0) {
            throw new BusinessException("归档零部件失");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PartsDid generatePartsDid(Long partsId) {
        requireOwner(partsId);
        PartsInfo partsInfo = partsInfoMapper.selectById(partsId);
        if (partsInfo == null) {
            throw new BusinessException("零部件不存在");
        }

        try {
            // 优先按“批次下单件”逐个生成DID
            LambdaQueryWrapper<PartsUnit> unitWrapper = new LambdaQueryWrapper<>();
            unitWrapper.eq(PartsUnit::getPartsId, partsId).orderByAsc(PartsUnit::getId);
            List<PartsUnit> units = partsUnitMapper.selectList(unitWrapper);
            if (units == null || units.isEmpty()) {
                // 无单件时先自动初始化，再按单件生成
                initPartsUnits(partsId, null);
                units = partsUnitMapper.selectList(unitWrapper);
            }
            if (units != null && !units.isEmpty()) {
                PartsDid first = null;
                for (PartsUnit unit : units) {
                    LambdaQueryWrapper<PartsDid> unitDidWrapper = new LambdaQueryWrapper<>();
                    unitDidWrapper.eq(PartsDid::getUnitId, unit.getId()).last("LIMIT 1");
                    PartsDid existUnitDid = partsDidMapper.selectOne(unitDidWrapper);
                    PartsDid current;
                    if (existUnitDid != null && existUnitDid.getDidStatus() != null && existUnitDid.getDidStatus() != 3) {
                        current = existUnitDid;
                    } else {
                        current = generateUnitDid(unit.getId());
                    }
                    if (first == null) {
                        first = current;
                    }
                }
                if (first != null) {
                    return first;
                }
            }
            throw new BusinessException("批次下未找到可生成DID的单件数据");
        } catch (Exception e) {
            throw new BusinessException("生成零部件DID失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePartsStatus(Long partsId, Integer status) {
        requireOwner(partsId);
        PartsInfo partsInfo = partsInfoMapper.selectById(partsId);
        if (partsInfo == null) {
            throw new BusinessException("零部件不存在");
        }

        partsInfo.setStatus(status);
        int result = partsInfoMapper.updateById(partsInfo);
        if (result <= 0) {
            throw new BusinessException("更新零部件状态失");
        }
    }

    @Override
    public DidGenerateTaskVO generatePartsDidAsync(Long partsId) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        DidGenerateTaskVO task = new DidGenerateTaskVO();
        task.setTaskId(taskId);
        task.setPartsId(partsId);
        task.setStatus(0);
        task.setProgress(0);
        didTaskStore.put(taskId, task);

        try {
            task.setStatus(1);
            task.setProgress(30);
            PartsDid did = transactionTemplate.execute(status -> generatePartsDid(partsId));
            task.setStatus(2);
            task.setProgress(100);
            task.setResultDid(did.getDid());
        } catch (Exception e) {
            task.setStatus(3);
            task.setProgress(100);
            task.setErrorMsg(e.getMessage());
        }
        return task;
    }

    @Override
    public DidGenerateTaskVO getDidGenerateTask(String taskId) {
        DidGenerateTaskVO task = didTaskStore.get(taskId);
        if (task == null) {
            throw new BusinessException("DID任务不存在");
        }
        return task;
    }

    @Override
    public List<PartsDidVO> getPartsDidList(PartsDidQueryDTO queryDTO) {
        LambdaQueryWrapper<PartsDid> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getPartsId() != null) {
            wrapper.eq(PartsDid::getPartsId, queryDTO.getPartsId());
        }
        if (StringUtils.hasText(queryDTO.getDid())) {
            wrapper.like(PartsDid::getDid, queryDTO.getDid());
        }
        if (queryDTO.getDidStatus() != null) {
            wrapper.eq(PartsDid::getDidStatus, queryDTO.getDidStatus());
        }
        if (!Boolean.FALSE.equals(queryDTO.getOnlyUnit())) {
            wrapper.isNotNull(PartsDid::getUnitId);
        }
        wrapper.orderByDesc(PartsDid::getCreateTime);
        List<PartsDid> didList = partsDidMapper.selectList(wrapper);
        if (didList == null || didList.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<Long> partIds = didList.stream()
                .map(PartsDid::getPartsId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, PartsInfo> partsMap = partIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : partsInfoMapper.selectBatchIds(partIds).stream().collect(Collectors.toMap(PartsInfo::getId, p -> p));
        List<Long> unitIds = didList.stream()
                .map(PartsDid::getUnitId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, PartsUnit> unitMap = unitIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : partsUnitMapper.selectBatchIds(unitIds).stream().collect(Collectors.toMap(PartsUnit::getId, u -> u));

        return didList.stream()
                .filter(d -> {
                    if (!StringUtils.hasText(queryDTO.getBatchNumber())) {
                        return true;
                    }
                    PartsInfo p = partsMap.get(d.getPartsId());
                    return p != null && queryDTO.getBatchNumber().equals(p.getBatchNumber());
                })
                .map(d -> {
                    PartsDidVO vo = new PartsDidVO();
                    BeanUtils.copyProperties(d, vo);
                    PartsInfo p = partsMap.get(d.getPartsId());
                    if (p != null) {
                        vo.setPartsCode(p.getPartsCode());
                        vo.setPartsName(p.getPartsName());
                        vo.setBatchNumber(p.getBatchNumber());
                    }
                    if (d.getUnitId() != null) {
                        PartsUnit u = unitMap.get(d.getUnitId());
                        if (u != null) {
                            vo.setUnitCode(u.getUnitCode());
                        }
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public String getDidDocument(Long didId) {
        PartsDid did = partsDidMapper.selectById(didId);
        if (did == null) {
            throw new BusinessException("DID不存在");
        }
        return did.getDidDocument();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PartsDid bindDid(Long didId, PartsDidBindDTO bindDTO) {
        actor.requireRoles("manufacturer");
        PartsDid did = partsDidMapper.selectById(didId);
        if (did == null) {
            throw new BusinessException("DID不存在");
        }
        if (did.getDidStatus() == null || did.getDidStatus() != 1) {
            throw new BusinessException("仅激活状态的DID可绑定");
        }
        requireOwner(did.getPartsId());
        did.setBindStatus(1);
        did.setBindType(bindDTO.getBindType());
        did.setBindCode(bindDTO.getBindCode());
        did.setBindTime(new Date());
        did.setUnboundTime(null);
        did.setRemark(bindDTO.getRemark());
        partsDidMapper.updateById(did);
        audits.record("bindDid",did.getId(),did);
        return did;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PartsDid unbindDid(Long didId) {
        actor.requireRoles("manufacturer");
        PartsDid did = partsDidMapper.selectById(didId);
        if (did == null) {
            throw new BusinessException("DID不存在");
        }
        requireOwner(did.getPartsId());
        did.setBindStatus(0);
        did.setUnboundTime(new Date());
        did.setBindType(null);
        did.setBindCode(null);
        partsDidMapper.updateById(did);
        audits.record("unbindDid",did.getId(),did);
        return did;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PartsDid freezeDid(Long didId, String reason) {
        actor.requireRoles("quality");
        PartsDid did = partsDidMapper.selectById(didId);
        if (did == null) {
            throw new BusinessException("DID不存在");
        }
        partsInfoMapper.lockById(did.getPartsId());
        if(!Integer.valueOf(1).equals(did.getDidStatus()) && !Integer.valueOf(2).equals(did.getDidStatus())) throw new BusinessException("仅激活对象可以冻结");
        did.setDidStatus(2);
        if (StringUtils.hasText(reason)) {
            did.setRemark(reason);
        }
        partsDidMapper.updateById(did);
        audits.record("freezeDid",did.getId(),did);
        return did;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PartsDid unfreezeDid(Long didId) {
        actor.requireRoles("quality");
        PartsDid did = partsDidMapper.selectById(didId);
        if (did == null) {
            throw new BusinessException("DID不存在");
        }
        partsInfoMapper.lockById(did.getPartsId());
        if(!Integer.valueOf(2).equals(did.getDidStatus())) throw new BusinessException("仅被冻结对象可以解冻");
        safety.assertCanUnfreeze(did.getPartsId());
        did.setDidStatus(1);
        partsDidMapper.updateById(did);
        audits.record("unfreezeDid",did.getId(),did);
        return did;
    }

    @Override
    public DidValidateVO validateDid(String did) {
        DidValidateVO vo = new DidValidateVO();
        vo.setDid(did);
        boolean formatValid = didUtil.validatePartDidFormat(did) || didUtil.validateDidFormat(did);
        vo.setFormatValid(formatValid);
        if (!formatValid) {
            vo.setExists(false);
            vo.setActivated(false);
            vo.setBound(false);
            vo.setMessage("DID格式不合法");
            return vo;
        }

        LambdaQueryWrapper<PartsDid> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PartsDid::getDid, did).last("LIMIT 1");
        PartsDid entity = partsDidMapper.selectOne(wrapper);
        if (entity == null) {
            vo.setExists(false);
            vo.setActivated(false);
            vo.setBound(false);
            vo.setMessage("DID不存在");
            return vo;
        }
        vo.setExists(true);
        vo.setPartsId(entity.getPartsId());
        vo.setActivated(entity.getDidStatus() != null && entity.getDidStatus() == 1);
        vo.setBound(entity.getBindStatus() != null && entity.getBindStatus() == 1);
        vo.setDocumentCheck(documentVerifier.verify(entity));
        vo.setSimulated(chainStorageAdapter.isSimulated());
        vo.setMessage(Boolean.TRUE.equals(vo.getDocumentCheck()) && Boolean.TRUE.equals(vo.getActivated()) && Boolean.TRUE.equals(vo.getBound())
            ? (chainStorageAdapter.isSimulated()?"核验通过，存证来源为本地模拟账本":"核验通过") : "DID存在，但文档存证、激活状态或物理绑定尚未通过核验");
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int initPartsUnits(Long partsId, Integer unitCount) {
        requireOwner(partsId);
        PartsInfo partsInfo = partsInfoMapper.selectById(partsId);
        if (partsInfo == null) {
            throw new BusinessException("零部件不存在");
        }
        int target = unitCount != null && unitCount > 0
                ? unitCount
                : (partsInfo.getQuantity() == null ? 1 : Math.max(1, partsInfo.getQuantity().intValue()));
        if (target > 5000) {
            target = 5000;
        }
        LambdaQueryWrapper<PartsUnit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PartsUnit::getPartsId, partsId);
        Long existCount = partsUnitMapper.selectCount(wrapper);
        int existed = existCount == null ? 0 : existCount.intValue();
        if (existed >= target) {
            return existed;
        }
        for (int i = existed + 1; i <= target; i++) {
            PartsUnit unit = new PartsUnit();
            unit.setPartsId(partsId);
            unit.setBatchNumber(partsInfo.getBatchNumber());
            unit.setUnitCode(partsInfo.getPartsCode() + "-" + String.format("%06d", i));
            unit.setUnitStatus(0);
            partsUnitMapper.insert(unit);
        }
        return target;
    }

    @Override
    public List<PartsUnit> getUnitsByBatch(String batchNumber) {
        try {
            LambdaQueryWrapper<PartsUnit> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PartsUnit::getBatchNumber, batchNumber).orderByDesc(PartsUnit::getCreateTime).orderByDesc(PartsUnit::getId);
            return partsUnitMapper.selectList(wrapper);
        } catch (BadSqlGrammarException e) {
            if (isPartsUnitTableMissing(e)) {
                log.warn("parts_unit 表不存在，批次单件查询降级为空列表。batchNumber={}", batchNumber);
                return java.util.Collections.emptyList();
            }
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PartsDid generateUnitDid(Long unitId) {
        actor.requireRoles("manufacturer");
        PartsUnit unit;
        try {
            unit = partsUnitMapper.selectById(unitId);
        } catch (BadSqlGrammarException e) {
            if (isPartsUnitTableMissing(e)) {
                throw new BusinessException("当前数据库缺少 parts_unit 表，请执行最新 init.sql 或增量建表脚本后重试");
            }
            throw e;
        }
        if (unit == null) {
            throw new BusinessException("单件不存在");
        }
        PartsInfo partsInfo = partsInfoMapper.selectById(unit.getPartsId());
        if (partsInfo == null) {
            throw new BusinessException("单件所属零部件不存在");
        }
        requireOwner(unit.getPartsId());
        LambdaQueryWrapper<PartsDid> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(PartsDid::getUnitId, unitId).last("LIMIT 1");
        PartsDid exist = partsDidMapper.selectOne(existWrapper);
        if (exist != null) {
            throw new BusinessException("该单件已生成DID");
        }

        String manufacturerDid = StringUtils.hasText(partsInfo.getManufacturer())
                ? didUtil.sha256Hex(partsInfo.getManufacturer()).substring(0, 16)
                : "unknown";
        String did = didUtil.generatePartDid(didNetwork, manufacturerDid, unit.getBatchNumber(), unit.getUnitCode());
        com.kewei.parts.module.identity.entity.SysUserDid issuer=userDidMapper.selectOne(new LambdaQueryWrapper<com.kewei.parts.module.identity.entity.SysUserDid>()
            .eq(com.kewei.parts.module.identity.entity.SysUserDid::getUserId,actor.id()).eq(com.kewei.parts.module.identity.entity.SysUserDid::getBindStatus,1));
        if(issuer==null) throw new BusinessException("生产方须先绑定有效DID");
        String publicKey = issuer.getPublicKey();
        String didDocument = didUtil.generateDidDocument(did, publicKey, unitId, unit.getUnitCode());
        ObjectStorageAdapter.StoreObjectResult objectResult =
                objectStorageAdapter.store("did_document_unit", unitId, didDocument);
        ChainStorageAdapter.StoreChainResult chainResult =
                chainStorageAdapter.storeAnchor(ChainAnchorRequest.builder()
                        .businessType("did_document_unit")
                        .businessId(unitId)
                        .dataHash(objectResult.getContentHash())
                        .objectCid(objectResult.getCid())
                        .partsDid(did)
                        .chainId(chainRouter.didChain())
                        .build());

        PartsDid partsDid = new PartsDid();
        partsDid.setPartsId(unit.getPartsId());
        partsDid.setUnitId(unitId);
        partsDid.setDid(did);
        partsDid.setDidDocument(didDocument);
        partsDid.setPublicKey(publicKey);
        partsDid.setBindStatus(0);
        partsDid.setDidStatus(1);
        partsDid.setDocHash(objectResult.getContentHash());
        partsDid.setDocCid(objectResult.getCid());
        partsDid.setChainTxId(chainResult.getTxId());
        partsDid.setBlockHeight(chainResult.getBlockHeight());
        partsDid.setRemark("系统自动生成单件DID");
        if (exist != null) {
            partsDid.setId(exist.getId());
            partsDidMapper.updateById(partsDid);
        } else {
            partsDidMapper.insert(partsDid);
        }
        return partsDid;
    }

    private boolean isPartsUnitTableMissing(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            String msg = cursor.getMessage();
            if (msg != null && msg.contains("parts_trace.parts_unit") && msg.contains("doesn't exist")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * 获取状态名"
     */
    private String getStatusName(Integer status) {
        switch (status) {
            case 0:
                return "草稿";
            case 1:
                return "正常";
            case 2:
                return "已归";
            case 3:
                return "已废";
            default:
                return "未知";
        }
    }

    private void requireOwner(Long partsId) {
        actor.requireRoles("manufacturer");
        PartsInfo part=partsInfoMapper.lockById(partsId);
        if(part==null) throw new BusinessException("零部件不存在");
        actor.requireSelfOrAdmin(part.getCreatorId());
    }
}
