package org.example.ons.Service;

import lombok.RequiredArgsConstructor;
import org.example.ons.Api.ApiException;
import org.example.ons.DTO.AuthorizedStaffDTO;
import org.example.ons.DTO.EmployeeUpdateDTO;
import org.example.ons.DTO.GoogleRoutesDTO;
import org.example.ons.Enum.AuthorizedStaffRole;
import org.example.ons.Enum.ReportStatus;
import org.example.ons.Model.*;
import org.example.ons.Repository.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthorizedStaffService {
    private final AuthorizedStaffRepository authorizedStaffRepository;
    private final CampaignRepository campaignRepository;
    private final AuthRepository authRepository;
    private final AssistanceReportRepository assistanceReportRepository;
    private final ReportStatusHistoryRepository reportStatusHistoryRepository;
    private final MapService mapService;
    private final NotificationService notificationService;

    @Transactional
    public void createStaff(
            User actor,
            AuthorizedStaffDTO dto,
            Integer campaignId) {

        if (actor == null
                || actor.getId() == null
                || !actor.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }

        if (!"ADMIN".equals(actor.getRole())
                && !"CAMPAIGN_OWNER".equals(actor.getRole())) {
            throw new AccessDeniedException(
                    "Only admins and campaign owners can create supervisors"
            );
        }

        Campaign campaign =
                campaignRepository.findCampaignById(campaignId);

        if (campaign == null) {
            throw new ApiException("campaign not found");
        }

        if ("CAMPAIGN_OWNER".equals(actor.getRole())
                && (campaign.getUser() == null
                || !actor.getId().equals(
                campaign.getUser().getId()
        ))) {
            throw new AccessDeniedException(
                    "You can only create supervisors for your own campaign"
            );
        }

        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPassword(
                new BCryptPasswordEncoder().encode(dto.getPassword())
        );
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setRole("AuthorizedStaff");
        user.setActive(dto.isActive());

        User savedUser = authRepository.save(user);

        AuthorizedStaff staff = new AuthorizedStaff();
        staff.setStaffRole(AuthorizedStaffRole.SUPERVISOR);
        staff.setCampaign(campaign);
        staff.setUser(savedUser);

        authorizedStaffRepository.save(staff);
    }

    @Transactional(readOnly = true)
    public List<AuthorizedStaff> getAllStaff(User actor) {

        if (actor == null
                || actor.getId() == null
                || !actor.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }

        List<AuthorizedStaff> staffList;

        if ("ADMIN".equals(actor.getRole())) {

            staffList = authorizedStaffRepository.findAll();

        } else if ("CAMPAIGN_OWNER".equals(actor.getRole())) {

            Campaign campaign =
                    campaignRepository.findCampaignById(actor.getId());

            if (campaign == null
                    || campaign.getUser() == null
                    || !actor.getId().equals(
                    campaign.getUser().getId()
            )) {
                throw new AccessDeniedException(
                        "You are not linked to a campaign"
                );
            }

            staffList = authorizedStaffRepository
                    .findByCampaign_Id(campaign.getId());

        } else {
            throw new AccessDeniedException(
                    "Only admins and campaign owners can access this endpoint"
            );
        }

        if (staffList.isEmpty()) {
            throw new ApiException("there is no staff in the campaign or system");
        }

        return staffList;
    }

    public AuthorizedStaff getStaffById(Integer id) {

        AuthorizedStaff staff = authorizedStaffRepository.findById(id)
                .orElseThrow(() -> new ApiException("staff not found"));

        return staff;
    }

    @Transactional
    public void updateStaff(
            User actor,
            Integer id,
            EmployeeUpdateDTO dto) {

        if (actor == null
                || actor.getId() == null
                || !actor.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }

        if (!"ADMIN".equals(actor.getRole())
                && !"CAMPAIGN_OWNER".equals(actor.getRole())) {
            throw new AccessDeniedException(
                    "Only admins and campaign owners can update staff"
            );
        }

        AuthorizedStaff staff = authorizedStaffRepository.findById(id)
                .orElseThrow(() -> new ApiException("staff not found"));

        if ("CAMPAIGN_OWNER".equals(actor.getRole())
                && (staff.getCampaign() == null
                || staff.getCampaign().getUser() == null
                || !actor.getId().equals(
                staff.getCampaign().getUser().getId()))) {
            throw new AccessDeniedException(
                    "You can only update staff in your own campaign"
            );
        }

        User user = staff.getUser();

        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setActive(dto.isActive());

        authRepository.save(user);
    }

    @Transactional
    public void deleteStaff(User actor, Integer id) {

        if (actor == null
                || actor.getId() == null
                || !actor.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }

        if (!"ADMIN".equals(actor.getRole())
                && !"CAMPAIGN_OWNER".equals(actor.getRole())) {
            throw new AccessDeniedException(
                    "Only admins and campaign owners can delete staff"
            );
        }

        AuthorizedStaff staff = authorizedStaffRepository.findById(id)
                .orElseThrow(() -> new ApiException("staff not found"));

        if ("CAMPAIGN_OWNER".equals(actor.getRole())
                && (staff.getCampaign() == null
                || staff.getCampaign().getUser() == null
                || !actor.getId().equals(
                staff.getCampaign().getUser().getId()))) {
            throw new AccessDeniedException(
                    "You can only delete staff in your own campaign"
            );
        }

        User user = staff.getUser();

        authorizedStaffRepository.delete(staff);
        authRepository.delete(user);
    }
    public List<AuthorizedStaff> getEmployees(Integer id) {
        AuthorizedStaff authorizedStaff =
                authorizedStaffRepository.findAuthorizedStaffById(id);

        if (authorizedStaff == null) {
            throw new ApiException("user not found");
        }

        if (authorizedStaff.getStaffRole() != AuthorizedStaffRole.SUPERVISOR) {
            throw new ApiException("you are not authorized");
        }

        Campaign campaign = authorizedStaff.getCampaign();

        if (campaign == null) {
            throw new ApiException("supervisor has no campaign");
        }

        List<AuthorizedStaff> employees =
                authorizedStaffRepository.findAuthorizedStaffByCampaignAndStaffRole(
                        campaign,
                        AuthorizedStaffRole.REGULAR_EMPLOYEE
                );

        if (employees.isEmpty()) {
            throw new ApiException("there are no employees in the campaign");
        }

        return employees;
    }


    @Transactional
    public void addEmployee(User actor, AuthorizedStaffDTO dto) {

        if (actor == null
                || actor.getId() == null
                || !actor.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }

        Campaign campaign;
        AuthorizedStaff supervisor = null;

        if ("CAMPAIGN_OWNER".equals(actor.getRole())) {

            campaign = campaignRepository.findCampaignById(actor.getId());

            if (campaign == null
                    || campaign.getUser() == null
                    || !actor.getId().equals(campaign.getUser().getId())) {
                throw new AccessDeniedException(
                        "You are not linked to a campaign"
                );
            }

            if (dto.getSupervisorId() == null) {
                throw new ApiException("Supervisor is required when adding an employee");
            }
            supervisor = authorizedStaffRepository
                    .findAuthorizedStaffById(dto.getSupervisorId());
            if (supervisor == null
                    || supervisor.getStaffRole() != AuthorizedStaffRole.SUPERVISOR
                    || supervisor.getCampaign() == null
                    || !campaign.getId().equals(supervisor.getCampaign().getId())
                    || supervisor.getUser() == null
                    || !supervisor.getUser().isActive()) {
                throw new AccessDeniedException(
                        "Select an active supervisor from your campaign"
                );
            }

        } else if ("AuthorizedStaff".equals(actor.getRole())) {

            supervisor = authorizedStaffRepository
                    .findAuthorizedStaffById(actor.getId());

            if (supervisor == null
                    || supervisor.getStaffRole()
                    != AuthorizedStaffRole.SUPERVISOR) {
                throw new AccessDeniedException(
                        "Only supervisors can add employees"
                );
            }

            if (dto.getSupervisorId() != null
                    && !actor.getId().equals(dto.getSupervisorId())) {
                throw new AccessDeniedException(
                        "Supervisors can only add employees to their own team"
                );
            }

            campaign = supervisor.getCampaign();

            if (campaign == null) {
                throw new AccessDeniedException(
                        "You are not linked to a campaign"
                );
            }

        } else {
            throw new AccessDeniedException(
                    "Only campaign owners and supervisors can add employees"
            );
        }

        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPassword(
                new BCryptPasswordEncoder().encode(dto.getPassword())
        );
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setRole("AuthorizedStaff");
        user.setActive(dto.isActive());

        User savedUser = authRepository.save(user);

        AuthorizedStaff employee = new AuthorizedStaff();
        employee.setStaffRole(AuthorizedStaffRole.REGULAR_EMPLOYEE);
        employee.setCampaign(campaign);
        employee.setSupervisor(supervisor);
        employee.setUser(savedUser);

        authorizedStaffRepository.save(employee);
    }


    @Transactional
    public void updateEmployee(
            User actor,
            Integer employeeId,
            EmployeeUpdateDTO dto) {

        if (actor == null
                || actor.getId() == null
                || !actor.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }

        Campaign campaign;

        if ("CAMPAIGN_OWNER".equals(actor.getRole())) {

            campaign = campaignRepository.findCampaignById(actor.getId());

            if (campaign == null
                    || campaign.getUser() == null
                    || !actor.getId().equals(campaign.getUser().getId())) {
                throw new AccessDeniedException(
                        "You are not linked to a campaign"
                );
            }

        } else if ("AuthorizedStaff".equals(actor.getRole())) {

            AuthorizedStaff supervisor = authorizedStaffRepository
                    .findAuthorizedStaffById(actor.getId());

            if (supervisor == null
                    || supervisor.getStaffRole()
                    != AuthorizedStaffRole.SUPERVISOR) {
                throw new AccessDeniedException(
                        "Only supervisors can update employees"
                );
            }

            campaign = supervisor.getCampaign();

            if (campaign == null) {
                throw new AccessDeniedException(
                        "You are not linked to a campaign"
                );
            }

        } else {
            throw new AccessDeniedException(
                    "Only campaign owners and supervisors can update employees"
            );
        }

        AuthorizedStaff employee = authorizedStaffRepository
                .findAuthorizedStaffById(employeeId);

        if (employee == null) {
            throw new ApiException("employee not found");
        }

        if (employee.getStaffRole()
                != AuthorizedStaffRole.REGULAR_EMPLOYEE) {
            throw new ApiException(
                    "This user is not a regular employee"
            );
        }

        if (employee.getCampaign() == null
                || !campaign.getId().equals(
                employee.getCampaign().getId())) {
            throw new AccessDeniedException(
                    "Employee does not belong to your campaign"
            );
        }

        User user = employee.getUser();

        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setActive(dto.isActive());

        authRepository.save(user);
    }

    @Transactional
    public void deleteEmployee(
            User actor,
            Integer employeeId) {

        if (actor == null
                || actor.getId() == null
                || !actor.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }

        Campaign campaign;

        if ("CAMPAIGN_OWNER".equals(actor.getRole())) {

            campaign = campaignRepository.findCampaignById(actor.getId());

            if (campaign == null
                    || campaign.getUser() == null
                    || !actor.getId().equals(campaign.getUser().getId())) {
                throw new AccessDeniedException(
                        "You are not linked to a campaign"
                );
            }

        } else if ("AuthorizedStaff".equals(actor.getRole())) {

            AuthorizedStaff supervisor = authorizedStaffRepository
                    .findAuthorizedStaffById(actor.getId());

            if (supervisor == null
                    || supervisor.getStaffRole()
                    != AuthorizedStaffRole.SUPERVISOR) {
                throw new AccessDeniedException(
                        "Only supervisors can delete employees"
                );
            }

            campaign = supervisor.getCampaign();

            if (campaign == null) {
                throw new AccessDeniedException(
                        "You are not linked to a campaign"
                );
            }

        } else {
            throw new AccessDeniedException(
                    "Only campaign owners and supervisors can delete employees"
            );
        }

        AuthorizedStaff employee = authorizedStaffRepository
                .findAuthorizedStaffById(employeeId);

        if (employee == null) {
            throw new ApiException("employee not found");
        }

        if (employee.getStaffRole()
                != AuthorizedStaffRole.REGULAR_EMPLOYEE) {
            throw new ApiException(
                    "This user is not a regular employee"
            );
        }

        if (employee.getCampaign() == null
                || !campaign.getId().equals(
                employee.getCampaign().getId())) {
            throw new AccessDeniedException(
                    "Employee does not belong to your campaign"
            );
        }

        User user = employee.getUser();

        authorizedStaffRepository.delete(employee);
        authRepository.delete(user);
    }




    // قبول حاج ضائع
    @Transactional
    public void acceptPilgrim(Integer staffId, Integer pilgrimId, long reportId) {

    AuthorizedStaff staff = authorizedStaffRepository.findAuthorizedStaffById(staffId);

        if (staff == null) {
        throw new ApiException("Authorized staff not found");
    }

        if(!staff.getUser().isActive()){
            throw new ApiException("the authorized staff not active");
        }

    AssistanceReport report = assistanceReportRepository.findAssistanceReportByReportId(reportId);
        if (report == null) {
        throw new ApiException("Assistance report not found");
    }
    // التأكد أن التقرير تابع للحاج المطلوب
        if (report.getPilgrim() == null ||
                !report.getPilgrim().getId().equals(pilgrimId)) {
        throw new ApiException("This report does not belong to this pilgrim");
    }

    // التأكد أن الطلب لم تتم معالجته سابقًا
        if (report.getStatus() != ReportStatus.OPEN) {
        throw new ApiException(
                "This report is no longer available for acceptance"
        );
    }

        if (staff.getCampaign() == null ||
                report.getCampaign() == null ||
                !staff.getCampaign().getId()
                        .equals(report.getCampaign().getId())) {

            throw new ApiException(
                    "This report does not belong to your campaign"
            );
        }

        if (report.getAuthorizedStaff() != null) {
            throw new ApiException("This report is already assigned");
        }
        report.setAuthorizedStaff(staff);
        updateReportStatus(report, ReportStatus.ASSIGNED);
        notificationService.notifyReportAssigned(
                report,
                staff.getUser().getPhoneNumber()
        );
}




    private void updateReportStatus(AssistanceReport report, ReportStatus newStatus) {

        LocalDateTime now = LocalDateTime.now();

        ReportStatusHistory previous = reportStatusHistoryRepository.findTopByReport_ReportIdOrderByChangedAtDesc(report.getReportId());

        long durationMinutes = 0;

        if (previous != null) {
            durationMinutes = java.time.Duration.between(
                    previous.getChangedAt(),
                    now
            ).toMinutes();
        }

        ReportStatusHistory history = new ReportStatusHistory();
        history.setReport(report);
        history.setOldStatus(report.getStatus());
        history.setNewStatus(newStatus);
        history.setChangedAt(now);
        history.setDurationMinutes(durationMinutes);

        report.setStatus(newStatus);

        assistanceReportRepository.save(report);
        reportStatusHistoryRepository.save(history);
    }



    @Transactional
    public void advanceReportStatus(Integer staffId, long reportId) {

        AuthorizedStaff staff = authorizedStaffRepository.findAuthorizedStaffById(staffId);

        if (staff == null) {
            throw new ApiException("Authorized staff not found");
        }

        AssistanceReport report = assistanceReportRepository.findAssistanceReportByReportId(reportId);

        if (report == null) {
            throw new ApiException("Assistance report not found");
        }

        if (report.getAuthorizedStaff() == null ||
                !report.getAuthorizedStaff().getId().equals(staffId)) {
            throw new ApiException(
                    "This report is not assigned to this staff member");
        }

        ReportStatus nextStatus;

        switch (report.getStatus()) {
            case ASSIGNED:
                nextStatus = ReportStatus.IN_PROGRESS;
                break;

            case IN_PROGRESS:
                nextStatus = ReportStatus.RESOLVED;
                break;

            default:
                throw new ApiException(
                        "This report cannot move to the next status");
        }
        updateReportStatus(report, nextStatus);
    }


    public List<AssistanceReport> getStaffReports(Integer staff_id){
        List<AssistanceReport> assistanceReports =
                assistanceReportRepository.findAssistanceReportByAuthorizedStaff_Id(staff_id);

        if(assistanceReports.isEmpty()){
            throw  new ApiException("you dont have reports");
        }
        return assistanceReports;
    }


    public List<AssistanceReport> getStaffReportsByStatus(Integer staff_id,ReportStatus reportStatus){
        List<AssistanceReport> assistanceReports =
                assistanceReportRepository.findAssistanceReportByAuthorizedStaff_IdAndStatus(staff_id,reportStatus);

        if(assistanceReports.isEmpty()){
            throw  new ApiException("you dont have reports");
        }
        return assistanceReports;
    }

    public List<AssistanceReport> getNearbyReports(
            Integer staffId,
            Double latitude,
            Double longitude) {

        if (!mapService.validCoordinates(latitude, longitude)) {
            throw new ApiException("Invalid latitude or longitude");
        }

        AuthorizedStaff staff =
                authorizedStaffRepository.findAuthorizedStaffById(staffId);

        if (staff == null) {
            throw new ApiException("Authorized staff not found");
        }

        if (!staff.getUser().isActive()) {
            throw new ApiException("Staff account is inactive");
        }

        if (staff.getCampaign() == null) {
            throw new ApiException("Staff has no campaign");
        }

        List<AssistanceReport> reports =
                assistanceReportRepository
                        .findByCampaignAndStatusAndAuthorizedStaffIsNull(
                                staff.getCampaign(),
                                ReportStatus.OPEN
                        );

        List<AssistanceReport> results = new ArrayList<>();

        Map<Integer, GoogleRoutesDTO.Route> routesByKiosk =
                new HashMap<>();

        for (AssistanceReport report : reports) {

            Kiosk kiosk = report.getKiosk();

            if (kiosk == null || !mapService.validCoordinates(
                    kiosk.getLatitude(),
                    kiosk.getLongitude())) {
                continue;
            }

            if (!routesByKiosk.containsKey(kiosk.getId())) {

                GoogleRoutesDTO response = mapService.getWalkingRoutes(
                        latitude,
                        longitude,
                        kiosk.getLatitude(),
                        kiosk.getLongitude()
                );

                GoogleRoutesDTO.Route shortest = null;

                if (response.getRoutes() != null) {
                    for (GoogleRoutesDTO.Route route : response.getRoutes()) {

                        if (route == null || route.getDuration() == null) {
                            throw new ApiException(
                                    "Invalid Google Routes response"
                            );
                        }

                        if (shortest == null ||
                                route.getDistanceMeters()
                                        < shortest.getDistanceMeters()) {
                            shortest = route;
                        }
                    }
                }

                routesByKiosk.put(kiosk.getId(), shortest);
            }

            if (routesByKiosk.get(kiosk.getId()) != null) {
                results.add(report);
            }
        }

        results.sort(
                Comparator.comparingLong(
                        (AssistanceReport report) -> routesByKiosk
                                .get(report.getKiosk().getId())
                                .getDistanceMeters()
                ).thenComparing(AssistanceReport::getReportId)
        );

        return results;
    }

}
