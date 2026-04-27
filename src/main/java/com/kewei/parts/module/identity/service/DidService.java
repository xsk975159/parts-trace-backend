package com.kewei.parts.module.identity.service;

import com.kewei.parts.module.identity.dto.DidBindDTO;
import com.kewei.parts.module.identity.entity.SysUserDid;

/**
 * DID服务接口
 */
public interface DidService {

    /**
     * 绑定DID
     */
    SysUserDid bindDid(Long userId, DidBindDTO didBindDTO);

    /**
     * 解绑DID
     */
    void unbindDid(Long userId);

    /**
     * 根据用户ID获取DID信息
     */
    SysUserDid getDidByUserId(Long userId);

    /**
     * 根据DID获取用户信息
     */
    SysUserDid getUserByDid(String did);

    /**
     * 验证DID
     */
    boolean verifyDid(String did, String signature);
}





