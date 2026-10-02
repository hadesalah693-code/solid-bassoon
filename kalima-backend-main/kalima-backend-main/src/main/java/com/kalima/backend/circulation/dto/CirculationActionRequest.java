package com.kalima.backend.circulation.dto;

import jakarta.validation.constraints.Size;

public record CirculationActionRequest(@Size(max = 1000) String notes) {
}