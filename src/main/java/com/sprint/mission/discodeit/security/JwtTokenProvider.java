package com.sprint.mission.discodeit.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sprint.mission.discodeit.dto.response.UserDto;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private final long accessTokenValiditySeconds;
  private final long refreshTokenValiditySeconds;
  private final MACSigner signer;
  private final MACVerifier verifier;

  private static final String TYPE_CLAIM = "type";
  private static final String USER_ID_CLAIM = "userId";
  private static final String ACCESS = "access";
  private static final String REFRESH = "refresh";

  public static final String REFRESH_TOKEN_COOKIE_NAME = "REFRESH_TOKEN";

  public JwtTokenProvider(
      @Value("${discodeit.jwt.secret}") String secret,
      @Value("${discodeit.jwt.access-token-validity-seconds}") long accessTokenValiditySeconds,
      @Value("${discodeit.jwt.refresh-token-validity-seconds}") long refreshTokenValiditySeconds) {
    this.accessTokenValiditySeconds = accessTokenValiditySeconds;
    this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    try {
      byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
      this.signer = new MACSigner(secretBytes);
      this.verifier = new MACVerifier(secretBytes);
    } catch (JOSEException e) {
      throw new IllegalStateException("JWT secret이 올바르지 않습니다. (최소 32바이트 필요)", e);
    }
  }

  public String generateAccessToken(UserDto user) {
    return generateToken(user, ACCESS, accessTokenValiditySeconds);
  }

  public String generateRefreshToken(UserDto user) {
    return generateToken(user, REFRESH, refreshTokenValiditySeconds);
  }

  private String generateToken(UserDto user, String type, long validitySeconds) {
    Instant now = Instant.now();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject(user.username())
        .claim(USER_ID_CLAIM, user.id().toString())
        .claim(TYPE_CLAIM, type)
        .jwtID(UUID.randomUUID().toString())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plusSeconds(validitySeconds)))
        .build();

    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    try {
      jwt.sign(signer);
    } catch (JOSEException e) {
      throw new IllegalStateException("JWT 서명에 실패했습니다.", e);
    }
    return jwt.serialize();
  }

  public boolean validateAccessToken(String token) {
    return validate(token, ACCESS);
  }

  public boolean validateRefreshToken(String token) {
    return validate(token, REFRESH);
  }

  private boolean validate(String token, String expectedType) {
    try {
      JWTClaimsSet claims = parseVerified(token);
      Date expiration = claims.getExpirationTime();
      return expectedType.equals(claims.getStringClaim(TYPE_CLAIM))
          && expiration != null
          && expiration.after(new Date());
    } catch (Exception e) {
      return false;
    }
  }

  // 서명만 검증하고 클레임을 반환한다. 만료는 검사하지 않는다.
  private JWTClaimsSet parseVerified(String token) throws ParseException, JOSEException {
    SignedJWT jwt = SignedJWT.parse(token);
    if (!jwt.verify(verifier)) {
      throw new JOSEException("서명이 올바르지 않습니다.");
    }
    return jwt.getJWTClaimsSet();
  }

  public long getRefreshTokenValiditySeconds() {
    return refreshTokenValiditySeconds;
  }

  public String getUsername(String token) {
    return claimsOf(token).getSubject();
  }

  public UUID getUserId(String token) {
    try {
      return UUID.fromString(claimsOf(token).getStringClaim(USER_ID_CLAIM));
    } catch (ParseException e) {
      throw new IllegalArgumentException("유효하지 않은 JWT입니다.", e);
    }
  }

  public Instant getExpiration(String token) {
    return claimsOf(token).getExpirationTime().toInstant();
  }

  private JWTClaimsSet claimsOf(String token) {
    try {
      return parseVerified(token);
    } catch (ParseException | JOSEException e) {
      throw new IllegalArgumentException("유효하지 않은 JWT입니다.", e);
    }
  }

}