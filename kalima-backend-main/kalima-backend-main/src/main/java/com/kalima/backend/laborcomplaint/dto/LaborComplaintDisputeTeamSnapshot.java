package com.kalima.backend.laborcomplaint.dto;

import com.kalima.backend.dispute.dto.DisputeTeamMemberResponse;

import java.util.List;

public record LaborComplaintDisputeTeamSnapshot(
        List<DisputeTeamMemberResponse> members
) {
}
