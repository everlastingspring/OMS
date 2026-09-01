package com.oms.audit.repository;

import com.oms.audit.document.ApplicationLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ApplicationLogRepository extends MongoRepository<ApplicationLog, String> {

    List<ApplicationLog> findByOrderIdOrderByTimestampDesc(String orderId);
}
