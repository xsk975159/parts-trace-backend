package com.kewei.parts.module.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.common.ResultCode;
import com.kewei.parts.module.identity.dto.RsaKeyGenerateDTO;
import com.kewei.parts.module.identity.entity.SysUserRsaKey;
import com.kewei.parts.module.identity.mapper.SysUserRsaKeyMapper;
import com.kewei.parts.module.identity.service.RsaKeyService;
import com.kewei.parts.module.system.entity.SysUser;
import com.kewei.parts.module.system.mapper.SysUserMapper;
import com.kewei.parts.utils.RsaUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * RSA密钥服务实现
 */
@Service
public class RsaKeyServiceImpl implements RsaKeyService {

    @Autowired
    private SysUserRsaKeyMapper rsaKeyMapper;

    @Autowired
    private SysUserMapper userMapper;
    @Autowired private com.kewei.parts.security.KeyVault keyVault;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUserRsaKey generateKeyPair(RsaKeyGenerateDTO generateDTO) {
        Long userId = generateDTO.getUserId();

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        try {
            Map<String, String> keyPair = RsaUtil.generateKeyPair();

            Integer expireDays = generateDTO.getExpireDays();
            if (expireDays == null || expireDays <= 0) {
                expireDays = 365;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, expireDays);
            Date expireTime = calendar.getTime();

            SysUserRsaKey rsaKey = new SysUserRsaKey();
            rsaKey.setUserId(userId);
            rsaKey.setPublicKey(keyPair.get("publicKey"));
            rsaKey.setPrivateKey(keyVault.encrypt(keyPair.get("privateKey")));
            rsaKey.setKeySize(Integer.parseInt(keyPair.get("keySize")));
            rsaKey.setStatus(1);
            rsaKey.setDefaultKey(0);
            rsaKey.setExpireTime(expireTime);

            int result = rsaKeyMapper.insert(rsaKey);
            if (result <= 0) {
                throw new BusinessException(ResultCode.RSA_KEY_GENERATE_ERROR);
            }
            return rsaKey;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.RSA_KEY_GENERATE_ERROR.getMessage() + ": " + e.getMessage());
        }
    }

    @Override
    public List<SysUserRsaKey> getKeysByUserId(Long userId) {
        LambdaQueryWrapper<SysUserRsaKey> wrapper = new LambdaQueryWrapper<SysUserRsaKey>();
        wrapper.eq(SysUserRsaKey::getUserId, userId);
        wrapper.orderByDesc(SysUserRsaKey::getCreateTime);
        return rsaKeyMapper.selectList(wrapper);
    }

    @Override
    public SysUserRsaKey getDefaultKeyByUserId(Long userId) {
        LambdaQueryWrapper<SysUserRsaKey> wrapper = new LambdaQueryWrapper<SysUserRsaKey>();
        wrapper.eq(SysUserRsaKey::getUserId, userId);
        wrapper.eq(SysUserRsaKey::getStatus, 1);
        wrapper.and(w -> w.isNull(SysUserRsaKey::getExpireTime).or().gt(SysUserRsaKey::getExpireTime, new Date()));
        wrapper.orderByDesc(SysUserRsaKey::getDefaultKey).orderByDesc(SysUserRsaKey::getId);
        wrapper.last("LIMIT 1");
        SysUserRsaKey rsaKey = rsaKeyMapper.selectOne(wrapper);

        if (rsaKey == null) {
            throw new BusinessException(ResultCode.RSA_KEY_NOT_FOUND);
        }

        if (rsaKey.getExpireTime() != null && rsaKey.getExpireTime().before(new Date())) {
            throw new BusinessException(ResultCode.RSA_KEY_EXPIRED);
        }

        return rsaKey;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultKey(Long userId, Long keyId) {
        // 锁定用户行，使同一用户同时切换默认密钥时仍只有一个默认项。
        userMapper.lockById(userId);
        SysUserRsaKey rsaKey = rsaKeyMapper.selectById(keyId);
        if (rsaKey == null || !rsaKey.getUserId().equals(userId)) throw new BusinessException(ResultCode.RSA_KEY_NOT_FOUND);
        if (!Integer.valueOf(1).equals(rsaKey.getStatus()) || (rsaKey.getExpireTime() != null && !rsaKey.getExpireTime().after(new Date())))
            throw new BusinessException("不能选择已停用或过期的密钥");
        rsaKeyMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUserRsaKey>()
                .eq(SysUserRsaKey::getUserId, userId).set(SysUserRsaKey::getDefaultKey, 0));
        rsaKey.setDefaultKey(1); rsaKeyMapper.updateById(rsaKey);
    }

    @Transactional(rollbackFor = Exception.class)
    public int migrateOwnKeys(Long userId) {
        userMapper.lockById(userId);
        int count = 0;
        for (SysUserRsaKey key : getKeysByUserId(userId)) {
            if (key.getPrivateKey() != null && !keyVault.isEncrypted(key.getPrivateKey())) {
                key.setPrivateKey(keyVault.encrypt(key.getPrivateKey())); rsaKeyMapper.updateById(key); count++;
            }
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKey(Long userId, Long keyId) {
        SysUserRsaKey rsaKey = rsaKeyMapper.selectById(keyId);
        if (rsaKey == null || !rsaKey.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.RSA_KEY_NOT_FOUND);
        }

        int result = rsaKeyMapper.deleteById(keyId);
        if (result <= 0) {
            throw new BusinessException("删除密钥失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKeyStatus(Long userId, Long keyId, Integer status) {
        SysUserRsaKey rsaKey = rsaKeyMapper.selectById(keyId);
        if (rsaKey == null || !rsaKey.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.RSA_KEY_NOT_FOUND);
        }

        rsaKey.setStatus(status);
        int result = rsaKeyMapper.updateById(rsaKey);
        if (result <= 0) {
            throw new BusinessException("更新密钥状态失败");
        }
    }
}
