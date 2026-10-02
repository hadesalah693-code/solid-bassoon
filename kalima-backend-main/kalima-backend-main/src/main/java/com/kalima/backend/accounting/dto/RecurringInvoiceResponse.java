package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.RecurringInvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringInvoiceResponse(
        UUID id,
        UUID clientId,
        String clientName,
        UUID caseId,
        String description,
        RecurringInvoice.Frequency frequency,
        int intervalCount,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextRunDate,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal total,
        boolean active,
        int timesGenerated,
        LocalDate lastGeneratedDate,
        String notes) {

    public static RecurringInvoiceResponse from(RecurringInvoice r) {
        return new RecurringInvoiceResponse(
                r.getId(),
                r.getClient() == null ? null : r.getClient().getId(),
                r.getClient() == null ? null : r.getClient().getFullName(),
                r.getCaseFile() == null ? null : r.getCaseFile().getId(),
                r.getDescription(),
                r.getFrequency(),
                r.getIntervalCount(),
                r.getStartDate(),
                r.getEndDate(),
                r.getNextRunDate(),
                r.getSubtotal(),
                r.getTaxRate(),
                r.getTaxAmount(),
                r.getTotal(),
                r.isActive(),
                r.getTimesGenerated(),
                r.getLastGeneratedDate(),
                r.getNotes());
    }
}