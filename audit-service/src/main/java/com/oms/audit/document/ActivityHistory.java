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
@Document(collection = "activity_history")
public class ActivityHistory {

    @Id
    private String id;

    @Indexed
    private Long userId;

    private String action;
    private String entityType;

    @Indexed
    private Long entityId;

    private String orderNumber;
    private String detail;
    private Instant timestamp = Instant.now();
}
