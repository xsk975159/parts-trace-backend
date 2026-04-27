package com.kewei.parts.module.system.controller;

import com.kewei.parts.common.Result;
import com.kewei.parts.module.system.dto.UserLoginDTO;
import com.kewei.parts.module.system.dto.UserRegisterDTO;
import com.kewei.parts.module.system.service.UserService;
import com.kewei.parts.module.system.vo.UserInfoVO;
import com.kewei.parts.module.system.vo.UserLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 用户管理控制器
 */
@Tag(name = "用户管理", description = "用户注册、登录、信息管理等接口")
@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody UserRegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.successMsg("注册成功");
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<UserLoginVO> login(@Validated @RequestBody UserLoginDTO loginDTO) {
        UserLoginVO loginVO = userService.login(loginDTO);
        return Result.success(loginVO);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/user/info")
    public Result<UserInfoVO> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        UserInfoVO userInfo = userService.getUserInfo(userId);
        return Result.success(userInfo);
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/user/info")
    public Result<Void> updateUserInfo(HttpServletRequest request, @RequestBody UserInfoVO userInfoVO) {
        Long userId = (Long) request.getAttribute("userId");
        userService.updateUserInfo(userId, userInfoVO);
        return Result.successMsg("更新成功");
    }

    @Operation(summary = "修改密码")
    @PutMapping("/user/password")
    public Result<Void> changePassword(HttpServletRequest request,
                                       @RequestParam String oldPassword,
                                       @RequestParam String newPassword) {
        Long userId = (Long) request.getAttribute("userId");
        userService.changePassword(userId, oldPassword, newPassword);
        return Result.successMsg("密码修改成功");
    }

    @Operation(summary = "根据用户名查询用户")
    @GetMapping("/user/username/{username}")
    public Result<UserInfoVO> getUserByUsername(@PathVariable String username) {
        UserInfoVO userInfo = userService.getUserByUsername(username);
        return Result.success(userInfo);
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/admin/user/{userId}/status")
    public Result<Void> updateUserStatus(@PathVariable Long userId, @RequestParam Integer status) {
        userService.updateUserStatus(userId, status);
        return Result.successMsg("用户状态更新成功");
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/admin/user/{userId}")
    public Result<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return Result.successMsg("用户删除成功");
    }

    @Operation(summary = "获取用户列表")
    @GetMapping("/user/list")
    public Result<Object> getUserList(@RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "10") Integer pageSize,
                                      @RequestParam(required = false) String username,
                                      @RequestParam(required = false) Integer status,
                                      @RequestParam(required = false) String userType) {
        Object result = userService.getUserList(page, pageSize, username, status, userType);
        return Result.success(result);
    }

    @Operation(summary = "创建用户")
    @PostMapping("/user/create")
    public Result<Void> createUser(@Validated @RequestBody UserRegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.successMsg("用户创建成功");
    }

    @Operation(summary = "更新用户")
    @PutMapping("/user/update")
    public Result<Void> updateUser(@RequestBody UserInfoVO userInfoVO) {
        userService.updateUserInfo(userInfoVO.getId(), userInfoVO);
        return Result.successMsg("用户更新成功");
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/user/delete/{userId}")
    public Result<Void> deleteUserForFrontend(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return Result.successMsg("用户删除成功");
    }
}
