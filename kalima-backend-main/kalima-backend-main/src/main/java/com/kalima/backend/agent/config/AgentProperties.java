package com.kalima.backend.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kalima.agent")
public class AgentProperties {

    /** Minimum model confidence (0–1) before auto-executing non-destructive tools. */
    private double confidenceThreshold = 0.85;

    /** Pending destructive-action TTL (minutes). */
    private int pendingActionTtlMinutes = 15;

    /** Night agent cron (default 2 AM daily). */
    private String nightAgentCron = "0 0 2 * * *";

    private boolean nightAgentEnabled = true;

    private int maxToolSteps = 8;

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public int getPendingActionTtlMinutes() {
        return pendingActionTtlMinutes;
    }

    public void setPendingActionTtlMinutes(int pendingActionTtlMinutes) {
        this.pendingActionTtlMinutes = pendingActionTtlMinutes;
    }

    public String getNightAgentCron() {
        return nightAgentCron;
    }

    public void setNightAgentCron(String nightAgentCron) {
        this.nightAgentCron = nightAgentCron;
    }

    public boolean isNightAgentEnabled() {
        return nightAgentEnabled;
    }

    public void setNightAgentEnabled(boolean nightAgentEnabled) {
        this.nightAgentEnabled = nightAgentEnabled;
    }

    public int getMaxToolSteps() {
        return maxToolSteps;
    }

    public void setMaxToolSteps(int maxToolSteps) {
        this.maxToolSteps = maxToolSteps;
    }
}
