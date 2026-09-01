package com.oms.audit.repository;

import com.oms.audit.document.ActivityHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ActivityHistoryRepository extends MongoRepository<ActivityHistory, String> {

    List<ActivityHistory> findByUserIdOrderByTimestampDesc(Long userId);

    List<ActivityHistory> findByEntityIdOrderByTimestampDesc(Long entityId);
}
