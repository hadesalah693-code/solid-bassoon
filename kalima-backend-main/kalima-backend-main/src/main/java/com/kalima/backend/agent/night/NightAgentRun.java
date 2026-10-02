package com.kalima.backend.agent.night;

import com.kalima.backend.domain.entity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "night_agent_runs")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class NightAgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(name = "report_json", nullable = false)
    private String reportJson;

    @Column(name = "auto_actions", nullable = false)
    private int autoActions;

    @Column(name = "ran_at", nullable = false)
    private LocalDateTime ranAt;

    @PrePersist
    void onCreate() {
        if (this.ranAt == null) {
            this.ranAt = LocalDateTime.now();
        }
        if (this.company == null) {
            throw new IllegalStateException("NightAgentRun.company must be set");
        }
    }
}
