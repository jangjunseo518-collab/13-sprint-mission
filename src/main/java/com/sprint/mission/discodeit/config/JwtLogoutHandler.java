package com.sprint.mission.discodeit.config;

import com.sprint.mission.discodeit.security.JwtRegistry;
import com.sprint.mission.discodeit.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtLogoutHandler implements LogoutHandler {

  private final JwtTokenProvider jwtTokenProvider;
  private final JwtRegistry jwtRegistry;

  @Override
  public void logout(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) {

    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      Arrays.stream(cookies)
          .filter(cookie -> cookie.getName()
              .equals(JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME))
          .findFirst()
          .ifPresent(cookie -> {
            try {
              UUID userId = jwtTokenProvider.getUserId(cookie.getValue());
              jwtRegistry.invalidateJwtInformationByUserId(userId);
            } catch (IllegalArgumentException e) {
              // 유효하지 않은 토큰이어도 로그아웃은 계속 진행한다.
            }
          });
    }

    Cookie expiredCookie = new Cookie(JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME, null);
    expiredCookie.setHttpOnly(true);
    expiredCookie.setPath("/");
    expiredCookie.setMaxAge(0);
    response.addCookie(expiredCookie);
  }
}
