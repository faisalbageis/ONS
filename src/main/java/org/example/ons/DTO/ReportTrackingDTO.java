package org.example.ons.DTO;

import org.example.ons.Enum.ReportStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ReportTrackingDTO(
        Long reportId,
        String caseNumber,
        ReportStatus status,
        LocalDateTime createdAt,
        String assigneeName,
        List<StatusChange> history
) {
    public record StatusChange(
            ReportStatus oldStatus,
            ReportStatus newStatus,
            LocalDateTime changedAt,
            Long durationMinutes
    ) {}
}
