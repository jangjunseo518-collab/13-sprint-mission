package com.sprint.mission.discodeit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.discodeit.dto.response.ChannelDto;
import com.sprint.mission.discodeit.dto.response.NotificationDto;
import com.sprint.mission.discodeit.entity.Notification;
import com.sprint.mission.discodeit.repository.NotificationRepository;
import com.sprint.mission.discodeit.service.ChannelService;
import com.sprint.mission.discodeit.service.NotificationService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// 기존 통합 테스트와 같은 설정이라 스프링 컨텍스트를 재사용합니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CacheIntegrationTest {

  @Autowired
  private ChannelService channelService;
  @Autowired
  private NotificationService notificationService;
  @Autowired
  private NotificationRepository notificationRepository;
  @Autowired
  private CacheManager cacheManager;

  private Cache cache(String name) {
    return Objects.requireNonNull(cacheManager.getCache(name));
  }

  @BeforeEach
  void setUp() {
    cache("channels").clear();
    cache("notifications").clear();
    // createPublic / update 는 CHANNEL_MANAGER 권한이 필요하다.
    SecurityContextHolder.getContext().setAuthentication(
        new TestingAuthenticationToken("tester", null, "ROLE_CHANNEL_MANAGER"));
  }

  @AfterEach
  void tearDown() {
    cache("channels").clear();
    cache("notifications").clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("채널 목록 조회 - 같은 사용자로 두 번 조회하면 캐시된 결과가 반환된다")
  void findAllByUserId_isCached() {
    // given
    UUID userId = UUID.randomUUID();

    // when
    List<ChannelDto> first = channelService.findAllByUserId(userId);
    List<ChannelDto> second = channelService.findAllByUserId(userId);

    // then
    assertThat(second).isSameAs(first);
    assertThat(cache("channels").get(userId)).isNotNull();
  }

  @Test
  @DisplayName("채널 생성 - 채널 목록 캐시가 비워지고 다음 조회에 새 채널이 반영된다")
  void createPublic_evictsChannelCache() {
    // given
    UUID userId = UUID.randomUUID();
    channelService.findAllByUserId(userId);
    assertThat(cache("channels").get(userId)).isNotNull();

    // when
    channelService.createPublic("캐시테스트채널", "설명");

    // then
    assertThat(cache("channels").get(userId)).isNull();
    assertThat(channelService.findAllByUserId(userId))
        .extracting(ChannelDto::name)
        .contains("캐시테스트채널");
  }

  @Test
  @DisplayName("채널 수정 - 채널 목록 캐시가 비워진다")
  void update_evictsChannelCache() {
    // given
    UUID userId = UUID.randomUUID();
    ChannelDto created = channelService.createPublic("수정전", "설명");
    channelService.findAllByUserId(userId);
    assertThat(cache("channels").get(userId)).isNotNull();

    // when
    channelService.update(created.id(), "수정후", "새설명");

    // then
    assertThat(cache("channels").get(userId)).isNull();
  }

  @Test
  @DisplayName("알림 - 조회는 캐시되고, 삭제하면 해당 사용자의 캐시가 비워진다")
  void notification_cacheAndEvict() {
    // given
    UUID receiverId = UUID.randomUUID();
    Notification saved = notificationRepository.save(
        new Notification(receiverId, "제목", "내용"));

    // when & then - 조회 캐시
    List<NotificationDto> first = notificationService.findAllByReceiverId(receiverId);
    List<NotificationDto> second = notificationService.findAllByReceiverId(receiverId);
    assertThat(first).hasSize(1);
    assertThat(second).isSameAs(first);

    // when & then - 삭제 시 캐시 삭제
    notificationService.delete(saved.getId(), receiverId);
    assertThat(cache("notifications").get(receiverId)).isNull();
    assertThat(notificationService.findAllByReceiverId(receiverId)).isEmpty();
  }
}