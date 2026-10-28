package org.example.ons.Service;
import org.example.ons.Enum.NotificationStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.example.ons.Model.Notification;
import org.example.ons.Model.ReportStatusHistory;
import org.example.ons.Repository.NotificationRepository;
import org.example.ons.Repository.ReportStatusHistoryRepository;
import org.example.ons.Repository.FieldStaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ons.Api.ApiException;
import org.example.ons.DTO.MergeReportsDTO;
import org.example.ons.DTO.SelfServiceReportDTO;
import org.example.ons.DTO.ReportTrackingDTO;
import org.example.ons.Enum.AuthorizedStaffRole;
import org.example.ons.Enum.ReportStatus;
import org.example.ons.Model.*;
import org.example.ons.Repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistanceReportService {

    private final AssistanceReportRepository assistanceReportRepository;
    private final PilgrimRepository pilgrimRepository;
    private final KioskRepository kioskRepository;
    private final CampaignRepository campaignRepository;
    private final AuthorizedStaffRepository authorizedStaffRepository;
    private final NotificationService notificationService;
    private final PilgrimCardService pilgrimCardService;
    private final FieldStaffRepository fieldStaffRepository;
    private final ReportStatusHistoryRepository reportStatusHistoryRepository;
    private final NotificationRepository notificationRepository;

    // إنشاء بلاغ بالطريقة الحالية مع توليد رقم البلاغ تلقائياً عند الحاجة
    @Transactional
    public void createReport(AssistanceReport report) {

        if (report == null
                || report.getPilgrim() == null
                || report.getPilgrim().getId() == null) {
            throw new ApiException("Pilgrim ID is required");
        }

        if (report.getKiosk() == null
                || report.getKiosk().getId() == null) {
            throw new ApiException("Kiosk ID is required");
        }

        if (report.getCampaign() == null
                || report.getCampaign().getId() == null) {
            throw new ApiException("Campaign ID is required");
        }

        Pilgrim pilgrim = pilgrimRepository.findById(
                report.getPilgrim().getId()
        ).orElse(null);

        if (pilgrim == null) {
            throw new ApiException("Pilgrim not found");
        }

        Kiosk kiosk = kioskRepository.findById(
                report.getKiosk().getId()
        ).orElse(null);

        if (kiosk == null) {
            throw new ApiException("Kiosk not found");
        }

        Campaign campaign = campaignRepository.findCampaignById(
                report.getCampaign().getId()
        );

        if (campaign == null) {
            throw new ApiException("Campaign not found");
        }

        if (report.getReminderSubmitted() == null) {
            report.setReminderSubmitted(false);
        }

        report.setPilgrim(pilgrim);
        report.setKiosk(kiosk);
        report.setCampaign(campaign);

        if (report.getCaseNumber() == null
                || report.getCaseNumber().isBlank()) {
            report.setCaseNumber(generateUniqueCaseNumber());
        }

        report.setStatus(ReportStatus.OPEN);

        AssistanceReport savedReport =
                assistanceReportRepository.save(report);

        notifyCampaignSupervisors(savedReport, campaign);
    }

    // إنشاء بلاغ من جهاز الخدمة الذاتية باستخدام رقم البطاقة ومعرّف الكشك
    @Transactional
    public AssistanceReport createSelfServiceReport(
            SelfServiceReportDTO dto) {

        if (dto == null) {
            throw new ApiException("Request data is required");
        }

        if (dto.getCardNumber() == null
                || dto.getCardNumber().isBlank()) {
            throw new ApiException("Card number is required");
        }

        if (dto.getKioskId() == null) {
            throw new ApiException("Kiosk ID is required");
        }

        // التعرف على الحاج والتحقق من أن بطاقته نشطة
        Pilgrim pilgrim =
                pilgrimCardService.identifyPilgrimByCardNumber(
                        dto.getCardNumber()
                );

        // التحقق من الكشك وحالته
        Kiosk kiosk = kioskRepository.findById(
                dto.getKioskId()
        ).orElse(null);

        if (kiosk == null) {
            throw new ApiException("Kiosk not found");
        }

        if (!"ACTIVE".equals(kiosk.getStatus())) {
            throw new ApiException("Kiosk is not active");
        }

        // استخراج الحملة من بيانات الحاج، وليس من طلب الفرونت إند
        Campaign campaign = pilgrim.getCampaign();

        if (campaign == null) {
            throw new ApiException(
                    "No campaign is associated with this pilgrim"
            );
        }

        // إنشاء البلاغ وربطه بالبيانات الصحيحة
        AssistanceReport report = new AssistanceReport();

        report.setCaseNumber(generateUniqueCaseNumber());
        report.setPilgrim(pilgrim);
        report.setKiosk(kiosk);
        report.setCampaign(campaign);
        report.setStatus(ReportStatus.OPEN);

        AssistanceReport savedReport =
                assistanceReportRepository.save(report);

        // إشعار مشرفي الحملة باستخدام الخدمة الحالية
        notifyCampaignSupervisors(savedReport, campaign);

        return savedReport;
    }

    // توليد رقم بلاغ فريد
    private String generateUniqueCaseNumber() {

        String caseNumber;

        do {
            caseNumber = "ONS-" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12)
                    .toUpperCase();
        } while (
                assistanceReportRepository.existsByCaseNumber(caseNumber)
        );

        return caseNumber;
    }

    // إشعار مشرفي الحملة النشطين
    private void notifyCampaignSupervisors(
            AssistanceReport report,
            Campaign campaign) {

        List<AuthorizedStaff> supervisors =
                authorizedStaffRepository
                        .findAuthorizedStaffByCampaignAndStaffRole(
                                campaign,
                                AuthorizedStaffRole.SUPERVISOR
                        );

        for (AuthorizedStaff supervisor : supervisors) {

            if (supervisor.getUser() == null) {
                continue;
            }

            if (!supervisor.getUser().isActive()) {
                continue;
            }

            String email = supervisor.getUser().getEmail();

            if (email == null || email.isBlank()) {
                continue;
            }

            notificationService.notifyNewReport(report, email);
        }
    }

    // جلب جميع البلاغات
    public List<AssistanceReport> getAllReports() {

        List<AssistanceReport> reports =
                assistanceReportRepository.findAll();

        if (reports.isEmpty()) {
            throw new ApiException(
                    "There are no reports in the system"
            );
        }

        return reports;
    }

    // جلب بلاغ بواسطة المعرّف
    public AssistanceReport getReportById(Long id) {

        AssistanceReport report = assistanceReportRepository
                .findAssistanceReportByReportId(id);

        if (report == null) {
            throw new ApiException("Report not found");
        }

        return report;
    }
    @Transactional(readOnly = true)
    public AssistanceReport getReportById(User user, Long id) {

        if (user == null
                || user.getId() == null
                || !user.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }

        AssistanceReport report = getReportById(id);

        if ("ADMIN".equals(user.getRole())) {
            return report;
        }

        if ("CAMPAIGN_OWNER".equals(user.getRole())) {

            if (report.getCampaign() == null
                    || report.getCampaign().getUser() == null
                    || !user.getId().equals(
                    report.getCampaign().getUser().getId())) {
                throw new AccessDeniedException(
                        "This report does not belong to your campaign"
                );
            }

            return report;
        }

        if ("AuthorizedStaff".equals(user.getRole())) {

            AuthorizedStaff staff = authorizedStaffRepository
                    .findAuthorizedStaffById(user.getId());

            if (staff == null
                    || staff.getCampaign() == null
                    || report.getCampaign() == null
                    || !staff.getCampaign().getId().equals(
                    report.getCampaign().getId())) {
                throw new AccessDeniedException(
                        "This report does not belong to your campaign"
                );
            }

            return report;
        }

        if ("FIELDـSTAFF".equals(user.getRole())
                && report.getFieldStaff() != null
                && user.getId().equals(report.getFieldStaff().getId())) {
            return report;
        }

        throw new AccessDeniedException(
                "You cannot access this report"
        );
    }

    // تحديث البلاغ
    @Transactional
    public void updateReport(
            Long id,
            AssistanceReport updatedReport) {

        if (updatedReport == null
                || updatedReport.getPilgrim() == null
                || updatedReport.getPilgrim().getId() == null
                || updatedReport.getKiosk() == null
                || updatedReport.getKiosk().getId() == null
                || updatedReport.getCampaign() == null
                || updatedReport.getCampaign().getId() == null) {

            throw new ApiException(
                    "Pilgrim, kiosk and campaign IDs are required"
            );
        }

        AssistanceReport report = getReportById(id);

        Pilgrim pilgrim = pilgrimRepository.findById(
                updatedReport.getPilgrim().getId()
        ).orElse(null);

        if (pilgrim == null) {
            throw new ApiException("Pilgrim not found");
        }

        Kiosk kiosk = kioskRepository.findById(
                updatedReport.getKiosk().getId()
        ).orElse(null);

        if (kiosk == null) {
            throw new ApiException("Kiosk not found");
        }

        Campaign campaign = campaignRepository.findCampaignById(
                updatedReport.getCampaign().getId()
        );

        if (campaign == null) {
            throw new ApiException("Campaign not found");
        }

        report.setPilgrim(pilgrim);
        report.setKiosk(kiosk);
        report.setCampaign(campaign);
        report.setPickupTime(updatedReport.getPickupTime());

        assistanceReportRepository.save(report);
    }

    // حذف البلاغ
    public void deleteReport(Long id) {
        AssistanceReport report = getReportById(id);
        assistanceReportRepository.delete(report);
    }

    // عدد البلاغات حسب الحالة
    public long countReportByStatus(ReportStatus status) {
        return assistanceReportRepository.countByStatus(status);
    }

    // عدد بلاغات حملة معينة حسب الحالة
    @Transactional(readOnly = true)
    public long countReportByStatusForCampaign(
            User user,
            ReportStatus status,
            Integer campaignId) {

        if (user == null
                || user.getId() == null
                || !user.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }

        if ("CAMPAIGN_OWNER".equals(user.getRole())) {

            Campaign campaign = campaignRepository
                    .findCampaignById(campaignId);

            if (campaign == null
                    || campaign.getUser() == null
                    || !user.getId().equals(campaign.getUser().getId())) {
                throw new AccessDeniedException(
                        "You cannot access another campaign"
                );
            }

        } else if ("AuthorizedStaff".equals(user.getRole())) {

            AuthorizedStaff staff = authorizedStaffRepository
                    .findAuthorizedStaffById(user.getId());

            if (staff == null
                    || staff.getCampaign() == null
                    || !staff.getCampaign().getId().equals(campaignId)) {
                throw new AccessDeniedException(
                        "You cannot access another campaign"
                );
            }

        } else if (!"ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException("You are not authorized");
        }

        return assistanceReportRepository
                .countByStatusAndCampaign_Id(status, campaignId);
    }

    // دمج البلاغات وإسنادها إلى الموظف المخول
    @Transactional
    public void mergeReports(
            User user,
            Long reportId,
            MergeReportsDTO dto) {

        if (user == null
                || user.getId() == null
                || !user.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }

        if (!"ADMIN".equals(user.getRole())
                && !"CAMPAIGN_OWNER".equals(user.getRole())
                && !"AuthorizedStaff".equals(user.getRole())) {
            throw new AccessDeniedException(
                    "You are not authorized to assign campaign reports"
            );
        }

        if (dto == null
                || dto.getStaffId() == null
                || dto.getReportIds() == null) {
            throw new ApiException(
                    "Staff ID and report IDs are required"
            );
        }

        AuthorizedStaff staff = authorizedStaffRepository
                .findAuthorizedStaffById(dto.getStaffId());

        if (staff == null) {
            throw new ApiException("Staff not found");
        }

        if (staff.getUser() == null
                || !staff.getUser().isActive()) {
            throw new ApiException("Staff account is inactive");
        }

        if (staff.getCampaign() == null) {
            throw new ApiException("Staff has no campaign");
        }

        if ("CAMPAIGN_OWNER".equals(user.getRole())) {

            if (staff.getCampaign().getUser() == null
                    || !user.getId().equals(
                    staff.getCampaign().getUser().getId())) {
                throw new AccessDeniedException(
                        "You can only assign reports within your own campaign"
                );
            }

        } else if ("AuthorizedStaff".equals(user.getRole())) {

            AuthorizedStaff supervisor = authorizedStaffRepository
                    .findAuthorizedStaffById(user.getId());

            if (supervisor == null
                    || supervisor.getStaffRole()
                    != AuthorizedStaffRole.SUPERVISOR
                    || supervisor.getCampaign() == null) {
                throw new AccessDeniedException(
                        "Only supervisors can assign campaign reports"
                );
            }

            if (!supervisor.getCampaign().getId().equals(
                    staff.getCampaign().getId())) {
                throw new AccessDeniedException(
                        "You can only assign reports within your own campaign"
                );
            }
        }

        Set<Long> reportIds = new TreeSet<>(dto.getReportIds());
        reportIds.add(reportId);

        if (reportIds.size() < 2) {
            throw new ApiException(
                    "Select at least two different reports"
            );
        }

        List<AssistanceReport> reports = assistanceReportRepository
                .findReportsForUpdate(reportIds);

        if (reports.size() != reportIds.size()) {
            throw new ApiException(
                    "One or more reports were not found"
            );
        }

        for (AssistanceReport report : reports) {

            if (report.getCampaign() == null
                    || !report.getCampaign().getId().equals(
                    staff.getCampaign().getId())) {
                throw new ApiException(
                        "All reports must belong to the staff campaign"
                );
            }

            if (report.getAuthorizedStaff() != null) {
                throw new ApiException(
                        "Report " + report.getReportId()
                                + " is already assigned"
                );
            }

            if (report.getStatus() != ReportStatus.OPEN) {
                throw new ApiException(
                        "Report " + report.getReportId()
                                + " is not open"
                );
            }
        }

        for (AssistanceReport report : reports) {
            report.setAuthorizedStaff(staff);
            changeReportStatus(report, ReportStatus.ASSIGNED);
        }

        assistanceReportRepository.saveAll(reports);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        for (AssistanceReport report : reports) {
                            notificationService.notifyReportAssigned(
                                    report,
                                    staff.getUser().getPhoneNumber()
                            );
                        }
                    }
                }
        );
    }

    // البلاغات المتأخرة لحملة معينة
    @Transactional(readOnly = true)
    public List<AssistanceReport> getCampaignOverdueReports(
            User user,
            Integer campaignId) {

        if (user == null
                || user.getId() == null
                || !user.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }

        Campaign campaign = campaignRepository.findCampaignById(campaignId);

        if (campaign == null) {
            throw new ApiException("Campaign not found");
        }

        if ("CAMPAIGN_OWNER".equals(user.getRole())) {

            if (campaign.getUser() == null
                    || !user.getId().equals(campaign.getUser().getId())) {
                throw new AccessDeniedException(
                        "You cannot access another campaign"
                );
            }

        } else if ("AuthorizedStaff".equals(user.getRole())) {

            AuthorizedStaff staff = authorizedStaffRepository
                    .findAuthorizedStaffById(user.getId());

            if (staff == null
                    || staff.getCampaign() == null
                    || !staff.getCampaign().getId().equals(campaignId)) {
                throw new AccessDeniedException(
                        "You cannot access another campaign"
                );
            }

        } else if (!"ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException("You are not authorized");
        }

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);

        return assistanceReportRepository
                .findByCampaign_IdAndStatusInAndCreatedAtBefore(
                        campaignId,
                        List.of(
                                ReportStatus.OPEN,
                                ReportStatus.ASSIGNED,
                                ReportStatus.IN_PROGRESS
                        ),
                        cutoff
                );
    }

    // جميع البلاغات المتأخرة
    public List<AssistanceReport> getAllOverdueReports() {

        LocalDateTime cutoff =
                LocalDateTime.now().minusMinutes(15);

        return assistanceReportRepository
                .findByStatusInAndCreatedAtBefore(
                        List.of(
                                ReportStatus.OPEN,
                                ReportStatus.ASSIGNED,
                                ReportStatus.IN_PROGRESS
                        ),
                        cutoff
                );
    }

    @Transactional
    public void updateReportStatus(Long reportId, ReportStatus newStatus) {
        if (newStatus == null) {
            throw new ApiException("Report status is required");
        }

        AssistanceReport report = getReportById(reportId);
        changeReportStatus(report, newStatus);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void sendOpenReportReminders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(7);

        List<AssistanceReport> reports =
                assistanceReportRepository
                        .findByStatusAndReminderSubmittedFalseAndCreatedAtLessThanEqual(
                                ReportStatus.OPEN,
                                cutoff
                        );

        for (AssistanceReport report : reports) {
            try {
                List<AuthorizedStaff> supervisors =
                        authorizedStaffRepository
                                .findAuthorizedStaffByCampaignAndStaffRole(
                                        report.getCampaign(),
                                        AuthorizedStaffRole.SUPERVISOR
                                );

                boolean submitted = false;

                for (AuthorizedStaff supervisor : supervisors) {
                    if (supervisor.getUser() != null
                            && supervisor.getUser().isActive()
                            && supervisor.getUser().getEmail() != null
                            && !supervisor.getUser().getEmail().isBlank()) {

                        notificationService.notifyOpenReportReminder(
                                report,
                                supervisor.getUser().getEmail()
                        );

                        submitted = true;
                    }
                }

                if (submitted) {
                    report.setReminderSubmitted(true);
                    assistanceReportRepository.save(report);
                }

            } catch (ApiException e) {
                log.warn(
                        "Could not submit reminder for report {}: {}",
                        report.getReportId(),
                        e.getMessage()
                );
            }
        }
    }


    @Transactional
    public void assignReport(Long reportId, Integer fieldStaffId) {

        if (fieldStaffId == null) {
            throw new ApiException("Field staff ID is required");
        }

        AssistanceReport report = getReportById(reportId);

        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ApiException("Only open reports can be assigned");
        }

        if (report.getFieldStaff() != null) {
            throw new ApiException("Report is already assigned");
        }

        FieldStaff staff = fieldStaffRepository.findById(fieldStaffId)
                .orElseThrow(() -> new ApiException("Field staff not found"));

        if (staff.getUser() == null || !staff.getUser().isActive()) {
            throw new ApiException("Field staff account is inactive");
        }

        report.setFieldStaff(staff);
        changeReportStatus(report, ReportStatus.ASSIGNED);

        assistanceReportRepository.save(report);

        // إشعار الموظف بعد حفظ الإسناد
        if (staff.getUser().getPhoneNumber() != null
                && !staff.getUser().getPhoneNumber().isBlank()) {

            notificationService.notifyReportAssigned(
                    report,
                    staff.getUser().getPhoneNumber()
            );
        }
    }


    @Transactional
    public void reassignReport(Long reportId, Integer fieldStaffId) {

        if (fieldStaffId == null) {
            throw new ApiException("Field staff ID is required");
        }

        AssistanceReport report = getReportById(reportId);

        if (report.getFieldStaff() == null) {
            throw new ApiException(
                    "Cannot reassign a report that has no field staff"
            );
        }

        if (report.getStatus() != ReportStatus.ASSIGNED) {
            throw new ApiException(
                    "Only assigned reports can be reassigned"
            );
        }

        FieldStaff staff = fieldStaffRepository.findById(fieldStaffId)
                .orElseThrow(() -> new ApiException("Field staff not found"));

        if (staff.getUser() == null || !staff.getUser().isActive()) {
            throw new ApiException("Field staff account is inactive");
        }

        if (Objects.equals(report.getFieldStaff().getId(), fieldStaffId)) {
            throw new ApiException(
                    "Report is already assigned to this field staff"
            );
        }

        report.setFieldStaff(staff);
        assistanceReportRepository.save(report);

        if (staff.getUser().getPhoneNumber() != null
                && !staff.getUser().getPhoneNumber().isBlank()) {

            notificationService.notifyReportAssigned(
                    report,
                    staff.getUser().getPhoneNumber()
            );
        }
    }


    @Transactional(readOnly = true)
    public ReportTrackingDTO getReportTrackingHistory(Long reportId) {
        AssistanceReport report = getReportById(reportId);
        List<ReportTrackingDTO.StatusChange> history = reportStatusHistoryRepository
                .findByReport_ReportIdOrderByChangedAtDesc(reportId)
                .stream()
                .map(change -> new ReportTrackingDTO.StatusChange(
                        change.getOldStatus(), change.getNewStatus(),
                        change.getChangedAt(), change.getDurationMinutes()))
                .toList();

        String assigneeName = null;
        if (report.getAuthorizedStaff() != null && report.getAuthorizedStaff().getUser() != null) {
            assigneeName = report.getAuthorizedStaff().getUser().getName();
        } else if (report.getFieldStaff() != null && report.getFieldStaff().getUser() != null) {
            assigneeName = report.getFieldStaff().getUser().getName();
        }
        return new ReportTrackingDTO(
                report.getReportId(), report.getCaseNumber(),
                report.getStatus(), report.getCreatedAt(), assigneeName, history);
    }

    @Transactional(readOnly = true)
    public AssistanceReport acceptReport(Long reportId) {

        AssistanceReport report = getReportById(reportId);

        if (report.getStatus() != ReportStatus.ASSIGNED) {
            throw new ApiException(
                    "Only assigned reports can be accepted"
            );
        }

        if (report.getFieldStaff() == null
                && report.getAuthorizedStaff() == null) {
            throw new ApiException(
                    "Report must have an assigned staff member"
            );
        }

        /*
         * لا توجد حالة ACCEPTED في ReportStatus.
         * لذلك تتحقق هذه الدالة من صلاحية البلاغ للقبول،
         * دون تغيير الحالة أو الادعاء بتسجيل قبول مستقل.
         */
        return report;
    }


    @Transactional
    public void startReport(Long reportId) {

        AssistanceReport report = getReportById(reportId);

        if (report.getFieldStaff() == null
                && report.getAuthorizedStaff() == null) {
            throw new ApiException(
                    "Report must be assigned before it can be started"
            );
        }

        changeReportStatus(report, ReportStatus.IN_PROGRESS);
        assistanceReportRepository.save(report);
    }


    @Transactional
    public void resolveReport(Long reportId) {

        AssistanceReport report = getReportById(reportId);

        if (report.getStatus() != ReportStatus.IN_PROGRESS) {
            throw new ApiException(
                    "Only in-progress reports can be resolved"
            );
        }

        changeReportStatus(report, ReportStatus.RESOLVED);
        assistanceReportRepository.save(report);
    }


    @Transactional
    public void cancelReport(Long reportId) {

        AssistanceReport report = getReportById(reportId);

        if (report.getStatus() == ReportStatus.RESOLVED
                || report.getStatus() == ReportStatus.CANCELLED) {
            throw new ApiException(
                    "Resolved or cancelled reports cannot be cancelled"
            );
        }

        changeReportStatus(report, ReportStatus.CANCELLED);
        assistanceReportRepository.save(report);
    }


    @Transactional
    public void reopenReport(Long reportId) {

        AssistanceReport report = getReportById(reportId);

        if (report.getStatus() != ReportStatus.RESOLVED
                && report.getStatus() != ReportStatus.CANCELLED) {
            throw new ApiException(
                    "Only resolved or cancelled reports can be reopened"
            );
        }

        report.setFieldStaff(null);
        report.setAuthorizedStaff(null);

        changeReportStatus(report, ReportStatus.OPEN);
        assistanceReportRepository.save(report);
    }


    private void changeReportStatus(
            AssistanceReport report,
            ReportStatus newStatus) {

        ReportStatus oldStatus = report.getStatus();

        if (oldStatus == newStatus) {
            throw new ApiException(
                    "Report already has this status"
            );
        }

        boolean validTransition = switch (newStatus) {
            case OPEN ->
                    oldStatus == ReportStatus.RESOLVED
                            || oldStatus == ReportStatus.CANCELLED;

            case ASSIGNED ->
                    oldStatus == ReportStatus.OPEN;

            case IN_PROGRESS ->
                    oldStatus == ReportStatus.ASSIGNED;

            case RESOLVED ->
                    oldStatus == ReportStatus.IN_PROGRESS;

            case CANCELLED ->
                    oldStatus == ReportStatus.OPEN
                            || oldStatus == ReportStatus.ASSIGNED
                            || oldStatus == ReportStatus.IN_PROGRESS;
        };

        if (!validTransition) {
            throw new ApiException(
                    "Invalid status transition from "
                            + oldStatus + " to " + newStatus
            );
        }

        LocalDateTime now = LocalDateTime.now();

        ReportStatusHistory history = new ReportStatusHistory();
        history.setReport(report);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedAt(now);

        ReportStatusHistory previousHistory =
                reportStatusHistoryRepository
                        .findTopByReport_ReportIdOrderByChangedAtDesc(
                                report.getReportId()
                        );

        LocalDateTime previousTime = previousHistory != null
                ? previousHistory.getChangedAt()
                : report.getCreatedAt();

        if (previousTime != null) {
            history.setDurationMinutes(
                    java.time.Duration.between(previousTime, now).toMinutes()
            );
        }

        reportStatusHistoryRepository.save(history);
        report.setStatus(newStatus);
    }


    @Transactional(readOnly = true)
    public List<ReportStatusHistory> getReportHistory(
            User user,
            Long reportId) {

        getReportById(user, reportId);

        return reportStatusHistoryRepository
                .findByReport_ReportIdOrderByChangedAtDesc(reportId);
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getReportAssignees(
            User user,
            Long reportId) {

        AssistanceReport report = getReportById(user, reportId);

        Map<String, Object> result = new LinkedHashMap<>();

        FieldStaff fieldStaff = report.getFieldStaff();

        if (fieldStaff != null && fieldStaff.getUser() != null) {

            Map<String, Object> staffInfo = new LinkedHashMap<>();

            staffInfo.put("id", fieldStaff.getId());
            staffInfo.put("name", fieldStaff.getUser().getName());
            staffInfo.put("email", fieldStaff.getUser().getEmail());
            staffInfo.put(
                    "phoneNumber",
                    fieldStaff.getUser().getPhoneNumber()
            );

            result.put("fieldStaff", staffInfo);

        } else {
            result.put("fieldStaff", null);
        }

        AuthorizedStaff authorizedStaff = report.getAuthorizedStaff();

        if (authorizedStaff != null
                && authorizedStaff.getUser() != null) {

            Map<String, Object> staffInfo = new LinkedHashMap<>();

            staffInfo.put("id", authorizedStaff.getId());
            staffInfo.put("name", authorizedStaff.getUser().getName());
            staffInfo.put("email", authorizedStaff.getUser().getEmail());
            staffInfo.put(
                    "staffRole",
                    authorizedStaff.getStaffRole().name()
            );

            result.put("authorizedStaff", staffInfo);

        } else {
            result.put("authorizedStaff", null);
        }

        return result;
    }


    @Transactional(readOnly = true)
    public List<Notification> getReportNotifications(
            User user,
            Long reportId) {

        getReportById(user, reportId);

        return notificationRepository
                .findByReport_ReportIdOrderBySentAtDesc(reportId);
    }


    @Transactional(readOnly = true)
    public Map<String, Long> getReportsSummary() {

        Map<String, Long> summary = new LinkedHashMap<>();

        long open = assistanceReportRepository
                .countByStatus(ReportStatus.OPEN);

        long assigned = assistanceReportRepository
                .countByStatus(ReportStatus.ASSIGNED);

        long inProgress = assistanceReportRepository
                .countByStatus(ReportStatus.IN_PROGRESS);

        long resolved = assistanceReportRepository
                .countByStatus(ReportStatus.RESOLVED);

        long cancelled = assistanceReportRepository
                .countByStatus(ReportStatus.CANCELLED);

        summary.put("total", open + assigned + inProgress + resolved + cancelled);
        summary.put("open", open);
        summary.put("assigned", assigned);
        summary.put("inProgress", inProgress);
        summary.put("resolved", resolved);
        summary.put("cancelled", cancelled);

        return summary;
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getReportsStatistics() {

        Map<String, Object> statistics = new LinkedHashMap<>();

        statistics.put("summary", getReportsSummary());

        statistics.put(
                "overdueReportsCount",
                (long) getAllOverdueReports().size()
        );

        return statistics;
    }


    @Transactional(readOnly = true)
    public String exportReports() {

        List<AssistanceReport> reports =
                assistanceReportRepository.findAll();

        StringBuilder csv = new StringBuilder();

        csv.append('\uFEFF');

        csv.append(
                "Report ID,Case Number,Status,Pilgrim ID,Kiosk ID,Campaign ID,Created At,Pickup Time\n"
        );

        for (AssistanceReport report : reports) {

            csv.append(report.getReportId()).append(',');
            csv.append(csvValue(report.getCaseNumber())).append(',');
            csv.append(report.getStatus().name()).append(',');

            csv.append(
                    report.getPilgrim() != null
                            ? report.getPilgrim().getId()
                            : ""
            ).append(',');

            csv.append(
                    report.getKiosk() != null
                            ? report.getKiosk().getId()
                            : ""
            ).append(',');

            csv.append(
                    report.getCampaign() != null
                            ? report.getCampaign().getId()
                            : ""
            ).append(',');

            csv.append(
                    report.getCreatedAt() != null
                            ? report.getCreatedAt()
                            : ""
            ).append(',');

            csv.append(
                    report.getPickupTime() != null
                            ? report.getPickupTime()
                            : ""
            ).append('\n');
        }

        return csv.toString();
    }

    private String csvValue(String value) {

        if (value == null) {
            return "";
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
