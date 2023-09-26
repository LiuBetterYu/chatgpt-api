package com.lby.chatgpt.domain.security.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.codec.binary.Base64;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 获取JwtToken，获取JwtToken中封装的信息，判断JwtToken是否存在
 * @author lby
 */
public class JwtUtil {

    private static final String defaultBase64EncodedSecretKey = "B*B^";
    private static final SignatureAlgorithm defaultSignatureAlgorithm = SignatureAlgorithm.HS256;

    public JwtUtil() {
        this(defaultBase64EncodedSecretKey, defaultSignatureAlgorithm);
    }

    private final String base64EncodedSecretKey;
    private final SignatureAlgorithm signatureAlgorithm;


    public JwtUtil(String secretKey, SignatureAlgorithm signatureAlgorithm) {
        this.base64EncodedSecretKey = Base64.encodeBase64String(secretKey.getBytes());
        this.signatureAlgorithm = signatureAlgorithm;
    }

    /**
     * 这里就是产生jwt字符串的地方
     * jwt字符串包括三个部分
     *  1. header
     *      -当前字符串的类型，一般都是“JWT”
     *      -哪种算法加密，“HS256”或者其他的加密算法
     *      所以一般都是固定的，没有什么变化
     *  2. payload
     *      一般有四个最常见的标准字段（下面有）
     *      iat：签发时间，也就是这个jwt什么时候生成的
     *      jti：JWT的唯一标识
     *      iss：签发人，一般都是username或者userId
     *      exp：过期时间
     * */
    public String encode(String issuer, long ttlMillis, Map<String, Object> claims) {
        // iss签发人，ttlMillis生存时间，claims是指还想要在jwt中存储的一些非隐私信息
        if (claims == null) {
            claims = new HashMap<>();
        }
        long nowMillis = System.currentTimeMillis();

        JwtBuilder builder = Jwts.builder()
                .setClaims(claims)                                         // 荷载部分
                .setId(UUID.randomUUID().toString())       // 这个是JWT的唯一标识，一般设置成唯一的，这个方法可以生成唯一标识
                .setIssuedAt(new Date(nowMillis))          // 签发时间
                .setSubject(issuer)                        // 签发人，也就是JWT是给谁的（逻辑上一般都是username或者userId）
                .signWith(signatureAlgorithm, base64EncodedSecretKey);     //这个地方是生成jwt使用的算法和秘钥
        if (ttlMillis >= 0) {
            long expMillis = nowMillis + ttlMillis;
            builder.setExpiration(new Date(expMillis));
        }
        return builder.compact();
    }

    // 相当于encode的方向，传入jwtToken生成对应的username和password等字段。Claim就是一个map
    // 也就是拿到荷载部分所有的键值对
    public Claims decode(String jwtToken) {

        return Jwts.parser()
                .setSigningKey(base64EncodedSecretKey)        // 设置签名的秘钥
                .parseClaimsJws(jwtToken)         // 设置需要解析的 jwt
                .getBody();
    }

    // 判断jwtToken是否合法
    public boolean isVerify(String jwtToken) {
        Algorithm algorithm = null;
        switch (signatureAlgorithm) {
            case HS256:
                algorithm = Algorithm.HMAC256(Base64.decodeBase64(base64EncodedSecretKey));
                break;
            default:
                throw new RuntimeException("不支持该算法");
        }
        JWTVerifier verifier = JWT.require(algorithm).build();
        verifier.verify(jwtToken);
        // 校验不通过会抛出异常
        // 判断合法的标准：1. 头部和荷载部分没有篡改过。2. 没有过期
        return true;
    }
}
