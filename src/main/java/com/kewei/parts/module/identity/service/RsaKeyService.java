package com.kewei.parts.module.identity.service;

import com.kewei.parts.module.identity.dto.RsaKeyGenerateDTO;
import com.kewei.parts.module.identity.entity.SysUserRsaKey;

import java.util.List;

/**
 * RSA密钥服务接口
 */
public interface RsaKeyService {

    /**
     * 生成RSA密钥"
     */
    SysUserRsaKey generateKeyPair(RsaKeyGenerateDTO generateDTO);

    /**
     * 根据用户ID获取密钥列表
     */
    List<SysUserRsaKey> getKeysByUserId(Long userId);

    /**
     * 根据用户ID获取默认密钥
     */
    SysUserRsaKey getDefaultKeyByUserId(Long userId);

    /**
     * 设置默认密钥
     */
    void setDefaultKey(Long userId, Long keyId);

    /**
     * 删除密钥
     */
    void deleteKey(Long userId, Long keyId);

    /**
     * 启用/禁用密钥
     */
    void updateKeyStatus(Long userId, Long keyId, Integer status);
}





