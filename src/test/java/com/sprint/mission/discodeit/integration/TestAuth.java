package com.sprint.mission.discodeit.integration;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.sprint.mission.discodeit.dto.response.UserDto;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.security.DiscodeitUserDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class TestAuth {

  private TestAuth() {
  }

  /**
   * ADMIN 권한으로 로그인한 사용자처럼 요청을 보내게 해주는 후처리기
   */
  public static RequestPostProcessor admin() {
    DiscodeitUserDetails userDetails = mock(DiscodeitUserDetails.class);
    UserDto userDto = new UserDto(UUID.randomUUID(), "admin", "admin@example.com", null, true,
        Role.ADMIN);
    given(userDetails.getUserDto()).willReturn(userDto);

    return authentication(new UsernamePasswordAuthenticationToken(
        userDetails, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }
}
