package com.kewei.parts.module.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.common.ResultCode;
import com.kewei.parts.module.identity.dto.DidBindDTO;
import com.kewei.parts.module.identity.entity.SysUserDid;
import com.kewei.parts.module.identity.entity.SysUserRsaKey;
import com.kewei.parts.module.identity.mapper.SysUserDidMapper;
import com.kewei.parts.module.identity.mapper.SysUserRsaKeyMapper;
import com.kewei.parts.module.identity.service.DidService;
import com.kewei.parts.module.system.entity.SysUser;
import com.kewei.parts.module.system.mapper.SysUserMapper;
import com.kewei.parts.utils.DidUtil;
import com.kewei.parts.utils.RsaUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;

/**
 * DID服务实现
 */
@Service
public class DidServiceImpl implements DidService {

    @Autowired
    private SysUserDidMapper userDidMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserRsaKeyMapper rsaKeyMapper;

    @Autowired
    private DidUtil didUtil;
    @Autowired private com.kewei.parts.security.CurrentActor actor;
    @Autowired private com.kewei.parts.security.KeyVault keyVault;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUserDid bindDid(Long userId, DidBindDTO didBindDTO) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        LambdaQueryWrapper<SysUserDid> wrapper = new LambdaQueryWrapper<SysUserDid>();
        wrapper.eq(SysUserDid::getUserId, userId);
        SysUserDid existDid = userDidMapper.selectOne(wrapper);
        if (existDid != null && existDid.getBindStatus() == 1) {
            throw new BusinessException(ResultCode.DID_ALREADY_BOUND);
        }

        try {
            String publicKey = didBindDTO.getPublicKey();
            String privateKey = didBindDTO.getPrivateKey();

            if (publicKey == null || publicKey.isEmpty()) {
                SysUserRsaKey selected=rsaKeyMapper.selectOne(new LambdaQueryWrapper<SysUserRsaKey>().eq(SysUserRsaKey::getUserId,userId)
                    .eq(SysUserRsaKey::getStatus,1).and(w->w.isNull(SysUserRsaKey::getExpireTime).or().gt(SysUserRsaKey::getExpireTime,new Date()))
                    .orderByDesc(SysUserRsaKey::getDefaultKey).orderByDesc(SysUserRsaKey::getId).last("LIMIT 1"));
                if(selected!=null) {publicKey=selected.getPublicKey();privateKey=keyVault.decrypt(selected.getPrivateKey());}
                else {Map<String,String> pair=RsaUtil.generateKeyPair();publicKey=pair.get("publicKey");privateKey=pair.get("privateKey");}

            }
            if (privateKey == null || privateKey.isEmpty()
                    || !RsaUtil.verify("bind-did:" + userId, RsaUtil.sign("bind-did:" + userId, privateKey), publicKey))
                throw new BusinessException("绑定DID需要匹配的公私钥以证明密钥控制权");
            SysUserRsaKey stored=rsaKeyMapper.selectOne(new LambdaQueryWrapper<SysUserRsaKey>().eq(SysUserRsaKey::getUserId,userId).eq(SysUserRsaKey::getPublicKey,publicKey).last("LIMIT 1"));
            if(stored==null) {
                SysUserRsaKey rsaKey = new SysUserRsaKey();
                rsaKey.setUserId(userId);
                rsaKey.setPublicKey(publicKey);
                rsaKey.setPrivateKey(keyVault.encrypt(privateKey));
                rsaKey.setKeySize(2048);
                rsaKey.setStatus(1);
                rsaKey.setDefaultKey(0);
                rsaKeyMapper.insert(rsaKey);
            }

            String did = didUtil.generateDid();
            String didDocument = didUtil.generateDidDocument(did, publicKey, userId, user.getUsername());

            SysUserDid userDid = new SysUserDid();
            userDid.setUserId(userId);
            userDid.setDid(did);
            userDid.setDidDocument(didDocument);
            userDid.setPublicKey(publicKey);
            userDid.setBindStatus(1);
            userDid.setBindTime(new Date());

            if (existDid != null) {
                userDid.setId(existDid.getId());
                userDidMapper.updateById(userDid);
            } else {
                userDidMapper.insert(userDid);
            }

            return userDid;
        } catch (Exception e) {
            throw new BusinessException("DID绑定失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindDid(Long userId) {
        LambdaQueryWrapper<SysUserDid> wrapper = new LambdaQueryWrapper<SysUserDid>();
        wrapper.eq(SysUserDid::getUserId, userId);
        SysUserDid userDid = userDidMapper.selectOne(wrapper);
        if (userDid == null) {
            throw new BusinessException(ResultCode.DID_NOT_BOUND);
        }

        userDid.setBindStatus(0);
        int result = userDidMapper.updateById(userDid);
        if (result <= 0) {
            throw new BusinessException("DID解绑失败");
        }
    }

    @Override
    public SysUserDid getDidByUserId(Long userId) {
        LambdaQueryWrapper<SysUserDid> wrapper = new LambdaQueryWrapper<SysUserDid>();
        wrapper.eq(SysUserDid::getUserId, userId);
        return userDidMapper.selectOne(wrapper);
    }

    @Override
    public SysUserDid getUserByDid(String did) {
        LambdaQueryWrapper<SysUserDid> wrapper = new LambdaQueryWrapper<SysUserDid>();
        wrapper.eq(SysUserDid::getDid, did);
        SysUserDid userDid = userDidMapper.selectOne(wrapper);
        if (userDid == null) {
            throw new BusinessException(ResultCode.DID_NOT_BOUND);
        }
        actor.requireSelfOrAdmin(userDid.getUserId());
        return userDid;
    }

    @Override
    public boolean verifyDid(String did, String signature) {
        if (!didUtil.validateDidFormat(did)) {
            throw new BusinessException(ResultCode.DID_INVALID);
        }

        LambdaQueryWrapper<SysUserDid> wrapper = new LambdaQueryWrapper<SysUserDid>();
        wrapper.eq(SysUserDid::getDid, did);
        SysUserDid userDid = userDidMapper.selectOne(wrapper);
        if (userDid == null || userDid.getBindStatus() == 0) {
            throw new BusinessException(ResultCode.DID_NOT_BOUND);
        }

        try {
            return RsaUtil.verify(did, signature, userDid.getPublicKey());
        } catch (Exception e) {
            throw new BusinessException("DID验证失败: " + e.getMessage());
        }
    }
}
