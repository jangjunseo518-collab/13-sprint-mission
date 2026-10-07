package com.sprint.mission.discodeit.controller;

import com.sprint.mission.discodeit.dto.request.RoleUpdateRequest;
import com.sprint.mission.discodeit.dto.response.JwtDto;
import com.sprint.mission.discodeit.dto.response.UserDto;
import com.sprint.mission.discodeit.security.JwtInformation;
import com.sprint.mission.discodeit.security.JwtTokenProvider;
import com.sprint.mission.discodeit.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final JwtTokenProvider jwtTokenProvider;

  @GetMapping("/csrf-token")
  public ResponseEntity<Void> getCsrfToken(CsrfToken csrfToken) {
    csrfToken.getToken();
    return ResponseEntity.status(HttpStatus.NON_AUTHORITATIVE_INFORMATION).build();

  }


  @PutMapping("/role")
  public ResponseEntity<UserDto> updateRole(@RequestBody @Valid RoleUpdateRequest request) {

    UserDto updateRole = authService.updateRole(request.userId(), request.newRole());
    return ResponseEntity.ok(updateRole);
  }

  @PostMapping("/refresh")
  public ResponseEntity<JwtDto> refresh(
      @CookieValue(name = JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME, required = false)
      String refreshToken,
      HttpServletResponse response) {

    JwtInformation jwtInformation = authService.refresh(refreshToken);

    Cookie cookie = new Cookie(
        JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME, jwtInformation.getRefreshToken());
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge((int) jwtTokenProvider.getRefreshTokenValiditySeconds());
    response.addCookie(cookie);

    return ResponseEntity.ok(
        new JwtDto(jwtInformation.getUserDto(), jwtInformation.getAccessToken()));
  }

}