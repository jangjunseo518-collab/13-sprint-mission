package com.sprint.mission.discodeit.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class ContextCopyingTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    // 1. 여기는 "요청 스레드"에서 실행됩니다. 이때 값을 복사해 둡니다.
    Map<String, String> mdc = MDC.getCopyOfContextMap();
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    // 2. 아래 Runnable은 "비동기 스레드"에서 실행됩니다.
    return () -> {
      try {
        if (mdc != null) {
          MDC.setContextMap(mdc);
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        runnable.run();
      } finally {
        MDC.clear();
        SecurityContextHolder.clearContext();
      }
    };
  }
}
