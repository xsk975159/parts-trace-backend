package com.kewei.parts.module.system.service;

import com.kewei.parts.module.system.dto.UserLoginDTO;
import com.kewei.parts.module.system.dto.UserRegisterDTO;
import com.kewei.parts.module.system.vo.UserInfoVO;
import com.kewei.parts.module.system.vo.UserLoginVO;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     */
    void register(UserRegisterDTO registerDTO);

    /**
     * 用户登录
     */
    UserLoginVO login(UserLoginDTO loginDTO);

    /**
     * 根据用户ID获取用户信息
     */
    UserInfoVO getUserInfo(Long userId);

    /**
     * 更新用户信息
     */
    void updateUserInfo(Long userId, UserInfoVO userInfoVO);

    /**
     * 修改密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 启用/禁用用户
     */
    void updateUserStatus(Long userId, Integer status);

    /**
     * 删除用户
     */
    void deleteUser(Long userId);

    /**
     * 根据用户名查询用户
     */
    UserInfoVO getUserByUsername(String username);

    /**
     * 获取用户列表（分页）
     */
    Object getUserList(Integer page, Integer pageSize, String username, Integer status, String userType);
}
