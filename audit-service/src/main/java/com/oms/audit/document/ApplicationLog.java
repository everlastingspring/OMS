package com.oms.audit.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "application_logs")
public class ApplicationLog {

    @Id
    private String id;

    private String service;
    private String eventType;

    @Indexed
    private String orderId;

    private String message;
    private String status;
    private String source;
    private Instant timestamp = Instant.now();
}
