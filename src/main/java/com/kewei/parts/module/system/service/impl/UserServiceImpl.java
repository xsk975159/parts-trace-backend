package com.kewei.parts.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.common.ResultCode;
import com.kewei.parts.module.identity.entity.SysUserDid;
import com.kewei.parts.module.identity.mapper.SysUserDidMapper;
import com.kewei.parts.module.system.dto.UserLoginDTO;
import com.kewei.parts.module.system.dto.UserRegisterDTO;
import com.kewei.parts.module.system.entity.SysUser;
import com.kewei.parts.module.system.mapper.SysUserMapper;
import com.kewei.parts.module.system.service.UserService;
import com.kewei.parts.module.system.vo.UserInfoVO;
import com.kewei.parts.module.system.vo.UserLoginVO;
import com.kewei.parts.utils.JwtUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserDidMapper userDidMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserRegisterDTO registerDTO) {
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            throw new BusinessException("两次密码输入不一致");
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>();
        wrapper.eq(SysUser::getUsername, registerDTO.getUsername());
        SysUser existUser = userMapper.selectOne(wrapper);
        if (existUser != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        wrapper = new LambdaQueryWrapper<SysUser>();
        wrapper.eq(SysUser::getEmail, registerDTO.getEmail());
        existUser = userMapper.selectOne(wrapper);
        if (existUser != null) {
            throw new BusinessException("邮箱已被注册");
        }

        wrapper = new LambdaQueryWrapper<SysUser>();
        wrapper.eq(SysUser::getPhone, registerDTO.getPhone());
        existUser = userMapper.selectOne(wrapper);
        if (existUser != null) {
            throw new BusinessException("手机号已被注册");
        }

        SysUser user = new SysUser();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setRealName(registerDTO.getRealName());
        user.setEmail(registerDTO.getEmail());
        user.setPhone(registerDTO.getPhone());
        user.setUserType(registerDTO.getUserType());
        user.setStatus(1);
        user.setDeleted(0);

        int result = userMapper.insert(user);
        if (result <= 0) {
            throw new BusinessException("用户注册失败");
        }
    }

    @Override
    public UserLoginVO login(UserLoginDTO loginDTO) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>();
        wrapper.eq(SysUser::getUsername, loginDTO.getUsername());
        SysUser user = userMapper.selectOne(wrapper);

        if (user == null || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        user.setLastLoginTime(new Date());
        userMapper.updateById(user);

        String accessToken = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getUserType());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        UserLoginVO loginVO = new UserLoginVO();
        loginVO.setUserId(user.getId());
        loginVO.setUsername(user.getUsername());
        loginVO.setRealName(user.getRealName());
        loginVO.setUserType(user.getUserType());
        loginVO.setAccessToken(accessToken);
        loginVO.setRefreshToken(refreshToken);
        loginVO.setExpiresIn(jwtExpiration);
        return loginVO;
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        UserInfoVO userInfoVO = new UserInfoVO();
        BeanUtils.copyProperties(user, userInfoVO);

        LambdaQueryWrapper<SysUserDid> didWrapper = new LambdaQueryWrapper<SysUserDid>();
        didWrapper.eq(SysUserDid::getUserId, userId);
        SysUserDid userDid = userDidMapper.selectOne(didWrapper);
        if (userDid != null) {
            userInfoVO.setDid(userDid.getDid());
            userInfoVO.setDidBindStatus(userDid.getBindStatus());
        }

        return userInfoVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserInfo(Long userId, UserInfoVO userInfoVO) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (userInfoVO.getRealName() != null) user.setRealName(userInfoVO.getRealName());
        if (userInfoVO.getEmail() != null) user.setEmail(userInfoVO.getEmail());
        if (userInfoVO.getPhone() != null) user.setPhone(userInfoVO.getPhone());
        if (userInfoVO.getAvatar() != null) user.setAvatar(userInfoVO.getAvatar());
        if (userInfoVO.getUserType() != null) user.setUserType(userInfoVO.getUserType());
        if (userInfoVO.getStatus() != null) user.setStatus(userInfoVO.getStatus());

        int result = userMapper.updateById(user);
        if (result <= 0) {
            throw new BusinessException("更新用户信息失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        int result = userMapper.updateById(user);
        if (result <= 0) {
            throw new BusinessException("修改密码失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long userId, Integer status) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        user.setStatus(status);
        int result = userMapper.updateById(user);
        if (result <= 0) {
            throw new BusinessException("更新用户状态失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        int result = userMapper.deleteById(userId);
        if (result <= 0) {
            throw new BusinessException("删除用户失败");
        }
    }

    @Override
    public UserInfoVO getUserByUsername(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        UserInfoVO userInfoVO = new UserInfoVO();
        BeanUtils.copyProperties(user, userInfoVO);
        return userInfoVO;
    }

    @Override
    public Object getUserList(Integer page, Integer pageSize, String username, Integer status, String userType) {
        Page<SysUser> pageObj = new Page<SysUser>(page, pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>();

        if (username != null && !username.isEmpty()) {
            wrapper.like(SysUser::getUsername, username);
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        if (userType != null && !userType.isEmpty()) {
            wrapper.eq(SysUser::getUserType, userType);
        }

        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> result = userMapper.selectPage(pageObj, wrapper);

        List<UserInfoVO> list = result.getRecords().stream().map(u -> {
            UserInfoVO vo = new UserInfoVO();
            BeanUtils.copyProperties(u, vo);
            return vo;
        }).collect(Collectors.toList());

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("list", list);
        map.put("total", result.getTotal());
        return map;
    }
}
