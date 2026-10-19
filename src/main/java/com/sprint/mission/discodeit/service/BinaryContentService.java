package com.sprint.mission.discodeit.service;

import com.sprint.mission.discodeit.dto.response.BinaryContentDto;

import com.sprint.mission.discodeit.entity.BinaryContentStatus;
import java.util.List;
import java.util.UUID;

public interface BinaryContentService {

  BinaryContentDto create(String fileName, long size, String contentType, byte[] bytes);

  BinaryContentDto find(UUID binaryContentId);

  List<BinaryContentDto> findAllByIdIn(List<UUID> binaryContentIds);

  BinaryContentDto updateStatus(UUID binaryContentId, BinaryContentStatus status);


  void delete(UUID binaryContentId);
}
