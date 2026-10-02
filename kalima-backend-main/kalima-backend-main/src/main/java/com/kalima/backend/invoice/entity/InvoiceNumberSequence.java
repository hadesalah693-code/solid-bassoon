package com.kalima.backend.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** عدّاد ترقيم الفواتير السنوي لكل شركة (INV-YYYY-xxxxx). */
@Entity
@Table(
        name = "invoice_number_sequences",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "year"}))
@Getter
@Setter
@NoArgsConstructor
public class InvoiceNumberSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "last_value", nullable = false)
    private long lastValue;
}
