package com.kewei.parts.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具"
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    /**
     * 生成访问Token
     */
    public String generateToken(Long userId, String username, String userType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("userType", userType);
        claims.put("tokenType", "access");
        return generateToken(claims, expiration);
    }

    public String generateToken(Long userId, String username, String userType, String passwordHash) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId); claims.put("username", username); claims.put("userType", userType);
        claims.put("tokenType", "access"); claims.put("authStamp", authStamp(passwordHash));
        return generateToken(claims, expiration);
    }

    public String authStamp(String passwordHash) {
        return new DidUtil().sha256Hex(passwordHash == null ? "" : passwordHash);
    }

    @javax.annotation.PostConstruct
    public void checkSecret() {
        if (secret == null || secret.length() < 32) throw new IllegalStateException("JWT_SECRET须配置为至少32个字符的独立随机密钥");
    }

    /**
     * 生成刷新Token
     */
    public String generateRefreshToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("tokenType", "refresh");
        return generateToken(claims, refreshExpiration);
    }

    /**
     * 生成Token
     */
    private String generateToken(Map<String, Object> claims, Long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return JWT.create()
                .withJWTId(java.util.UUID.randomUUID().toString())
                .withClaim("tokenType", (String) claims.get("tokenType"))
                .withClaim("authStamp", (String) claims.get("authStamp"))
                .withClaim("userId", (Long) claims.get("userId"))
                .withClaim("username", (String) claims.get("username"))
                .withClaim("userType", (String) claims.get("userType"))
                .withIssuedAt(now)
                .withExpiresAt(expiryDate)
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 验证Token
     */
    public boolean validateToken(String token) {
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret)).build();
            verifier.verify(token);
            return true;
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    /**
     * 解析Token
     */
    public DecodedJWT parseToken(String token) {
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret)).build();
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    /**
     * 从Token中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        DecodedJWT jwt = parseToken(token);
        return jwt != null ? jwt.getClaim("userId").asLong() : null;
    }

    /**
     * 从Token中获取用户名
     */
    public String getUsernameFromToken(String token) {
        DecodedJWT jwt = parseToken(token);
        return jwt != null ? jwt.getClaim("username").asString() : null;
    }

    /**
     * 从Token中获取用户类"
     */
    public String getUserTypeFromToken(String token) {
        DecodedJWT jwt = parseToken(token);
        return jwt != null ? jwt.getClaim("userType").asString() : null;
    }

    /**
     * 判断Token是否过期
     */
    public boolean isTokenExpired(String token) {
        DecodedJWT jwt = parseToken(token);
        if (jwt == null) {
            return true;
        }
        Date expiration = jwt.getExpiresAt();
        return expiration.before(new Date());
    }
}





