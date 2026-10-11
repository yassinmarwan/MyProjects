package com.team22.eventticketing.sales.repository;

import com.team22.eventticketing.sales.mongo.PaymentAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PaymentAuditEventRepository extends MongoRepository<PaymentAuditEvent, String> {

    List<PaymentAuditEvent> findBySaleIdAndActionNotOrderByTimestampAsc(Long saleId, String excludedAction);
}
