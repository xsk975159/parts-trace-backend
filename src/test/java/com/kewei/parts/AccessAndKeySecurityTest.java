package com.kewei.parts;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kewei.parts.common.*;
import com.kewei.parts.interceptor.*;
import com.kewei.parts.module.identity.entity.SysUserRsaKey;
import com.kewei.parts.module.system.entity.SysUser;
import com.kewei.parts.module.system.mapper.SysUserMapper;
import com.kewei.parts.module.system.service.impl.UserServiceImpl;
import com.kewei.parts.module.system.vo.UserInfoVO;
import com.kewei.parts.security.*;
import com.kewei.parts.utils.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AccessAndKeySecurityTest {
    @Test void disabledRoleDoesNotGrantAuthority() {
        com.kewei.parts.module.system.service.impl.RoleServiceImpl roles=spy(new com.kewei.parts.module.system.service.impl.RoleServiceImpl());
        com.kewei.parts.module.system.entity.SysRole role=new com.kewei.parts.module.system.entity.SysRole();role.setRoleCode("admin");role.setStatus(0);
        doReturn(Collections.singletonList(role)).when(roles).getUserRoles(1L);assertFalse(roles.hasRole(1L,"admin"));role.setStatus(1);assertTrue(roles.hasRole(1L,"admin"));
    }
    @Test void normalProfileCannotChangePrivilege() {
        UserServiceImpl service=new UserServiceImpl();SysUserMapper users=mock(SysUserMapper.class);CurrentActor actor=mock(CurrentActor.class);
        ReflectionTestUtils.setField(service,"userMapper",users);ReflectionTestUtils.setField(service,"actor",actor);
        SysUser user=new SysUser();user.setId(1L);user.setUserType("normal");user.setStatus(1);when(users.selectById(1L)).thenReturn(user);
        UserInfoVO dto=new UserInfoVO();dto.setUserType("regulator");
        assertThrows(BusinessException.class,()->service.updateUserInfo(1L,dto));assertEquals("normal",user.getUserType());verify(users,never()).updateById(any(SysUser.class));
    }
    @Test void allManagementAliasesRequireAdmin() throws Exception {
        PermissionInterceptor guard=new PermissionInterceptor();CurrentActor actor=mock(CurrentActor.class);
        doThrow(new BusinessException(ResultCode.FORBIDDEN)).when(actor).requireRoles("admin");ReflectionTestUtils.setField(guard,"actor",actor);
        for(String path:Arrays.asList("/api/role/assign","/api/permission/assign","/api/user/delete/2","/api/user/update","/api/admin/user/2/status")) {
            MockHttpServletResponse response=new MockHttpServletResponse();assertFalse(guard.preHandle(new MockHttpServletRequest("POST",path),response,new Object()));assertEquals(403,response.getStatus());
        }
    }
    private KeyVault vault() {KeyVault vault=new KeyVault();ReflectionTestUtils.setField(vault,"masterKey",Base64.getEncoder().encodeToString(new byte[32]));return vault;}
    @Test void privateKeyEncryptionUsesRandomNonceAndDetectsTampering() {
        KeyVault vault=vault();String a=vault.encrypt("private-key"),b=vault.encrypt("private-key");assertNotEquals(a,b);assertEquals("private-key",vault.decrypt(a));
        byte[] bytes=Base64.getDecoder().decode(a.substring(7));bytes[bytes.length-1]^=1;
        assertThrows(BusinessException.class,()->vault.decrypt("enc:v1:"+Base64.getEncoder().encodeToString(bytes)));
    }
    @Test void plaintextLegacyKeysRequireExplicitMigration() {assertThrows(BusinessException.class,()->vault().decrypt("legacy-plaintext"));}
    @Test void keysCannotBeEncryptedWithoutConfiguredMasterKey() {KeyVault vault=new KeyVault();ReflectionTestUtils.setField(vault,"masterKey","");assertThrows(BusinessException.class,()->vault.encrypt("private"));}
    @Test void neitherJsonSerializerReturnsPrivateKey() throws Exception {
        SysUserRsaKey key=new SysUserRsaKey();key.setPrivateKey("secret-private");key.setPublicKey("public");
        assertFalse(new ObjectMapper().writeValueAsString(key).contains("secret-private"));assertFalse(com.alibaba.fastjson.JSON.toJSONString(key).contains("secret-private"));
    }
    @Test void canonicalJsonIgnoresObjectKeyOrderAndNormalizesDates() {
        Map<String,Object> a=new LinkedHashMap<>();a.put("b",2);a.put("a",new Date(1234));Map<String,Object> b=new TreeMap<>();b.put("a",1000L);b.put("b",2.00);
        assertEquals(CanonicalJson.write(a),CanonicalJson.write(b));
    }
    @Test void accessRequiresLiveAccountPasswordStampAndAccessTokenType() throws Exception {
        JwtUtil jwt=new JwtUtil();ReflectionTestUtils.setField(jwt,"secret","01234567890123456789012345678901");ReflectionTestUtils.setField(jwt,"expiration",3600000L);ReflectionTestUtils.setField(jwt,"refreshExpiration",7200000L);
        JwtAuthInterceptor guard=new JwtAuthInterceptor();SysUserMapper users=mock(SysUserMapper.class);RevokedTokenMapper revoked=mock(RevokedTokenMapper.class);
        ReflectionTestUtils.setField(guard,"jwtUtil",jwt);ReflectionTestUtils.setField(guard,"userMapper",users);ReflectionTestUtils.setField(guard,"revokedTokenMapper",revoked);
        SysUser user=new SysUser();user.setId(4L);user.setUsername("u");user.setPassword("bcrypt-hash");user.setUserType("normal");user.setStatus(1);when(users.selectById(4L)).thenReturn(user);
        String access=jwt.generateToken(4L,"u","normal",user.getPassword());MockHttpServletRequest request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer "+access);
        assertTrue(guard.preHandle(request,new MockHttpServletResponse(),new Object()));
        user.setStatus(0);assertFalse(guard.preHandle(request,new MockHttpServletResponse(),new Object()));user.setStatus(1);
        user.setPassword("changed-hash");assertFalse(guard.preHandle(request,new MockHttpServletResponse(),new Object()));user.setPassword("bcrypt-hash");
        when(revoked.selectById(anyString())).thenReturn(new RevokedToken());assertFalse(guard.preHandle(request,new MockHttpServletResponse(),new Object()));
        MockHttpServletRequest refresh=new MockHttpServletRequest();refresh.addHeader("Authorization","Bearer "+jwt.generateRefreshToken(4L,"u"));assertFalse(guard.preHandle(refresh,new MockHttpServletResponse(),new Object()));
    }
}
