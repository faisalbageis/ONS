package org.example.ons.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ons.Api.ApiException;
import org.example.ons.Api.ApiResponse;
import org.example.ons.DTO.MergeReportsDTO;
import org.example.ons.DTO.SelfServiceReportDTO;
import org.example.ons.Enum.AuthorizedStaffRole;
import org.example.ons.Enum.ReportStatus;
import org.example.ons.Model.AssistanceReport;
import org.example.ons.Model.AuthorizedStaff;
import org.example.ons.Model.User;
import org.example.ons.Service.AssistanceReportService;
import org.example.ons.Service.AuthorizedStaffService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/reports", "/report"})
@RequiredArgsConstructor
public class AssistanceReportController {

    private final AssistanceReportService assistanceReportService;
    private final AuthorizedStaffService authorizedStaffService;

    @PostMapping
    public ResponseEntity<?> createReport(
            @AuthenticationPrincipal User user,
            @RequestBody AssistanceReport report) {

        requireAdmin(user);

        assistanceReportService.createReport(report);

        return ResponseEntity.status(201)
                .body(new ApiResponse("Report created successfully"));
    }

    @GetMapping
    public ResponseEntity<?> getAllReports(
            @AuthenticationPrincipal User user) {

        requireAdmin(user);

        return ResponseEntity.ok(
                assistanceReportService.getAllReports()
        );
    }

    @GetMapping("/{reportId:\\d+}")
    public ResponseEntity<?> getReportById(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId) {

        AssistanceReport report =
                assistanceReportService.getReportById(reportId);

        requireReportAccess(user, report);

        return ResponseEntity.ok(report);
    }

    @PutMapping("/{reportId:\\d+}")
    public ResponseEntity<?> updateReport(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId,
            @RequestBody AssistanceReport updatedReport) {

        requireAdmin(user);

        assistanceReportService.updateReport(reportId, updatedReport);

        return ResponseEntity.ok(
                new ApiResponse("Report updated successfully")
        );
    }

    @PatchMapping("/{reportId:\\d+}/status")
    public ResponseEntity<?> updateReportStatus(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId,
            @RequestBody Map<String, String> request) {

        requireAdmin(user);

        String statusValue = request.get("status");

        if (statusValue == null || statusValue.isBlank()) {
            throw new ApiException("Status is required");
        }

        ReportStatus status;

        try {
            status = ReportStatus.valueOf(
                    statusValue.trim().toUpperCase()
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException("Invalid report status");
        }

        assistanceReportService.updateReportStatus(reportId, status);

        return ResponseEntity.ok(
                new ApiResponse("Report status updated successfully")
        );
    }

    @PostMapping("/{reportId:\\d+}/assign")
    public ResponseEntity<?> assignReport(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId,
            @RequestBody Map<String, Integer> request) {

        requireAdmin(user);

        Integer fieldStaffId = request.get("fieldStaffId");

        if (fieldStaffId == null) {
            throw new ApiException("Field staff ID is required");
        }

        assistanceReportService.assignReport(reportId, fieldStaffId);

        return ResponseEntity.ok(
                new ApiResponse("Report assigned successfully")
        );
    }

    @PostMapping("/{reportId:\\d+}/reassign")
    public ResponseEntity<?> reassignReport(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId,
            @RequestBody Map<String, Integer> request) {

        requireAdmin(user);

        Integer fieldStaffId = request.get("fieldStaffId");

        if (fieldStaffId == null) {
            throw new ApiException("Field staff ID is required");
        }

        assistanceReportService.reassignReport(reportId, fieldStaffId);

        return ResponseEntity.ok(
                new ApiResponse("Report reassigned successfully")
        );
    }

    @PostMapping("/{reportId:\\d+}/accept")
    public ResponseEntity<?> acceptReport(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId) {

        requireAssignedStaff(
                user,
                assistanceReportService.getReportById(reportId)
        );

        assistanceReportService.acceptReport(reportId);

        return ResponseEntity.ok(
                new ApiResponse("Report accepted successfully")
        );
    }

    @PostMapping("/{reportId:\\d+}/start")
    public ResponseEntity<?> startReport(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId) {

        requireAssignedStaff(
                user,
                assistanceReportService.getReportById(reportId)
        );

        assistanceReportService.startReport(reportId);

        return ResponseEntity.ok(
                new ApiResponse("Report processing started")
        );
    }

    @PostMapping("/{reportId:\\d+}/resolve")
    public ResponseEntity<?> resolveReport(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId) {

        requireAssignedStaff(
                user,
                assistanceReportService.getReportById(reportId)
        );

        assistanceReportService.resolveReport(reportId);

        return ResponseEntity.ok(
                new ApiResponse("Report resolved successfully")
        );
    }

    @PostMapping("/{reportId:\\d+}/cancel")
    public ResponseEntity<?> cancelReport(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId) {

        requireAdmin(user);

        assistanceReportService.cancelReport(reportId);

        return ResponseEntity.ok(
                new ApiResponse("Report cancelled successfully")
        );
    }

    @PostMapping("/{reportId:\\d+}/reopen")
    public ResponseEntity<?> reopenReport(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId) {

        requireAdmin(user);

        assistanceReportService.reopenReport(reportId);

        return ResponseEntity.ok(
                new ApiResponse("Report reopened successfully")
        );
    }

    @GetMapping("/{reportId:\\d+}/history")
    public ResponseEntity<?> getReportHistory(@PathVariable Long reportId) {
        return ResponseEntity.ok(
                assistanceReportService.getReportTrackingHistory(reportId)
        );
    }

    @GetMapping("/{reportId:\\d+}/assignees")
    public ResponseEntity<?> getReportAssignees(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId) {

        requireReportAccess(
                user,
                assistanceReportService.getReportById(reportId)
        );

        return ResponseEntity.ok(
                assistanceReportService.getReportAssignees(user, reportId)
        );
    }

    @GetMapping("/{reportId:\\d+}/notifications")
    public ResponseEntity<?> getReportNotifications(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId) {

        requireReportAccess(
                user,
                assistanceReportService.getReportById(reportId)
        );

        return ResponseEntity.ok(
                assistanceReportService.getReportNotifications(user, reportId)
        );
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getReportsSummary(
            @AuthenticationPrincipal User user) {

        requireAdmin(user);

        return ResponseEntity.ok(
                assistanceReportService.getReportsSummary()
        );
    }

    @GetMapping("/statistics")
    public ResponseEntity<?> getReportsStatistics(
            @AuthenticationPrincipal User user) {

        requireAdmin(user);

        return ResponseEntity.ok(
                assistanceReportService.getReportsStatistics()
        );
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportReports(
            @AuthenticationPrincipal User user) {

        requireAdmin(user);

        String csv = assistanceReportService.exportReports();
        byte[] content = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=reports.csv"
                )
                .contentType(
                        MediaType.parseMediaType("text/csv;charset=UTF-8")
                )
                .body(content);
    }

    @PostMapping("/self-service")
    public ResponseEntity<?> createSelfServiceReport(
            @Valid @RequestBody SelfServiceReportDTO dto) {

        AssistanceReport report =
                assistanceReportService.createSelfServiceReport(dto);

        return ResponseEntity.status(201).body(
                Map.of(
                        "message", "Assistance report created successfully",
                        "reportId", report.getReportId(),
                        "caseNumber", report.getCaseNumber(),
                        "status", report.getStatus().name()
                )
        );
    }

    @PostMapping("/add")
    public ResponseEntity<?> createReportLegacy(
            @AuthenticationPrincipal User user,
            @RequestBody AssistanceReport report) {

        requireAdmin(user);

        assistanceReportService.createReport(report);

        return ResponseEntity.ok(
                new ApiResponse("Report created successfully")
        );
    }

    @GetMapping("/get")
    public ResponseEntity<?> getAllReportsLegacy(
            @AuthenticationPrincipal User user) {

        requireAdmin(user);

        return ResponseEntity.ok(
                assistanceReportService.getAllReports()
        );
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<?> getReportByIdLegacy(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        AssistanceReport report =
                assistanceReportService.getReportById(id);

        requireReportAccess(user, report);

        return ResponseEntity.ok(report);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateReportLegacy(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody AssistanceReport updatedReport) {

        requireAdmin(user);

        assistanceReportService.updateReport(id, updatedReport);

        return ResponseEntity.ok(
                new ApiResponse("Report updated successfully")
        );
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteReportLegacy(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        requireAdmin(user);

        assistanceReportService.deleteReport(id);

        return ResponseEntity.ok(
                new ApiResponse("Report deleted successfully")
        );
    }

    @GetMapping("/count/status/{status}")
    public ResponseEntity<?> countReportByStatus(
            @AuthenticationPrincipal User user,
            @PathVariable ReportStatus status) {

        requireAdmin(user);

        return ResponseEntity.ok(
                assistanceReportService.countReportByStatus(status)
        );
    }

    @GetMapping("/count/campaign/{campaignId}/status/{status}")
    public ResponseEntity<?> countReportByStatusForCampaign(
            @AuthenticationPrincipal User user,
            @PathVariable Integer campaignId,
            @PathVariable ReportStatus status) {

        requireCampaignAccess(user, campaignId);

        return ResponseEntity.ok(
                assistanceReportService.countReportByStatusForCampaign(
                        user, status, campaignId
                )
        );
    }

    @PostMapping("/{reportId}/merge")
    public ResponseEntity<?> mergeReports(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId,
            @Valid @RequestBody MergeReportsDTO dto) {

        requireActiveUser(user);

        if (!"ADMIN".equals(user.getRole())) {

            if (!"AuthorizedStaff".equals(user.getRole())) {
                throw new AccessDeniedException("You are not authorized");
            }

            AuthorizedStaff supervisor =
                    authorizedStaffService.getStaffById(user.getId());

            if (supervisor.getStaffRole()
                    != AuthorizedStaffRole.SUPERVISOR) {
                throw new AccessDeniedException(
                        "Only supervisors can assign campaign reports"
                );
            }

            AuthorizedStaff employee =
                    authorizedStaffService.getStaffById(dto.getStaffId());

            if (employee.getCampaign() == null) {
                throw new AccessDeniedException(
                        "Employee has no campaign"
                );
            }

            requireCampaignAccess(
                    user,
                    employee.getCampaign().getId()
            );
        }

        assistanceReportService.mergeReports(user, reportId, dto);

        return ResponseEntity.ok(
                new ApiResponse("Reports merged and assigned successfully")
        );
    }

    @GetMapping("/campaign/{campaignId}/overdue")
    public ResponseEntity<?> getOverdueReportsForCampaign(
            @AuthenticationPrincipal User user,
            @PathVariable Integer campaignId) {

        requireCampaignAccess(user, campaignId);

        return ResponseEntity.ok(
                assistanceReportService.getCampaignOverdueReports(user, campaignId)
        );
    }

    @GetMapping("/overdue")
    public ResponseEntity<?> getAllOverdueReports(
            @AuthenticationPrincipal User user) {

        requireAdmin(user);

        return ResponseEntity.ok(
                assistanceReportService.getAllOverdueReports()
        );
    }

    // Authorization checks only.

    private void requireActiveUser(User user) {
        if (user == null || !user.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }
    }

    private void requireAdmin(User user) {
        requireActiveUser(user);

        if (!"ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException(
                    "Only admins can access this endpoint"
            );
        }
    }

    private void requireCampaignAccess(
            User user,
            Integer campaignId) {

        requireActiveUser(user);

        if ("ADMIN".equals(user.getRole())) {
            return;
        }

        if (!"AuthorizedStaff".equals(user.getRole())) {
            throw new AccessDeniedException("You are not authorized");
        }

        AuthorizedStaff staff =
                authorizedStaffService.getStaffById(user.getId());

        if (campaignId == null
                || staff.getCampaign() == null
                || !campaignId.equals(staff.getCampaign().getId())) {

            throw new AccessDeniedException(
                    "You cannot access another campaign"
            );
        }
    }

    private void requireReportAccess(
            User user,
            AssistanceReport report) {

        requireActiveUser(user);

        if ("ADMIN".equals(user.getRole())) {
            return;
        }

        if ("AuthorizedStaff".equals(user.getRole())) {

            if (report.getCampaign() == null) {
                throw new AccessDeniedException(
                        "Report has no campaign"
                );
            }

            requireCampaignAccess(
                    user,
                    report.getCampaign().getId()
            );

            return;
        }

        if ("FIELDـSTAFF".equals(user.getRole())
                && report.getFieldStaff() != null
                && user.getId().equals(
                report.getFieldStaff().getId()
        )) {
            return;
        }

        throw new AccessDeniedException(
                "You cannot access this report"
        );
    }

    private void requireAssignedStaff(
            User user,
            AssistanceReport report) {

        requireActiveUser(user);

        if ("ADMIN".equals(user.getRole())) {
            return;
        }

        if ("FIELDـSTAFF".equals(user.getRole())
                && report.getFieldStaff() != null
                && user.getId().equals(
                report.getFieldStaff().getId()
        )) {
            return;
        }

        if ("AuthorizedStaff".equals(user.getRole())
                && report.getAuthorizedStaff() != null
                && user.getId().equals(
                report.getAuthorizedStaff().getId()
        )) {

            requireReportAccess(user, report);
            return;
        }

        throw new AccessDeniedException(
                "This report is not assigned to you"
        );
    }
}