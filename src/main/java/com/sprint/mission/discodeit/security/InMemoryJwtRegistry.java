package com.sprint.mission.discodeit.security;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InMemoryJwtRegistry implements JwtRegistry {


  // <userId, Queue<JwtInformation>>
  private final Map<UUID, Queue<JwtInformation>> origin = new ConcurrentHashMap<>();
  private final int maxActiveJwtCount;

  private final JwtTokenProvider jwtTokenProvider;

  public InMemoryJwtRegistry(
      @Value("${discodeit.jwt.max-active-count:1}") int maxActiveJwtCount,
      JwtTokenProvider jwtTokenProvider) {
    this.maxActiveJwtCount = maxActiveJwtCount;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Override
  public void registerJwtInformation(JwtInformation jwtInformation) {
    UUID userId = jwtInformation.getUserDto().id();
    Queue<JwtInformation> queue =
        origin.computeIfAbsent(userId, id -> new ConcurrentLinkedQueue<>());

    queue.add(jwtInformation);
    while (queue.size() > maxActiveJwtCount) {
      queue.poll();
    }
  }

  @Override
  public void invalidateJwtInformationByUserId(UUID userId) {
    origin.remove(userId);
  }

  @Override
  public boolean hasActiveJwtInformationByUserId(UUID userId) {
    Queue<JwtInformation> queue = origin.get(userId);
    return queue != null && !queue.isEmpty();
  }

  @Override
  public boolean hasActiveJwtInformationByAccessToken(String accessToken) {
    return origin.values().stream()
        .flatMap(Queue::stream)
        .anyMatch(info -> info.getAccessToken().equals(accessToken));
  }

  @Override
  public boolean hasActiveJwtInformationByRefreshToken(String refreshToken) {
    return origin.values().stream()
        .flatMap(Queue::stream)
        .anyMatch(info -> info.getRefreshToken().equals(refreshToken));
  }

  @Override
  public void rotateJwtInformation(String refreshToken, JwtInformation newJwtInformation) {
    origin.values().stream()
        .flatMap(Queue::stream)
        .filter(info -> info.getRefreshToken().equals(refreshToken))
        .findFirst()
        .ifPresent(info -> info.rotate(
            newJwtInformation.getAccessToken(),
            newJwtInformation.getRefreshToken()));
  }

  @Scheduled(fixedDelay = 1000 * 60 * 5)
  @Override
  public void clearExpiredJwtInformation() {
    origin.values().forEach(queue ->
        queue.removeIf(info -> !jwtTokenProvider.validateRefreshToken(info.getRefreshToken())));
  }
}
