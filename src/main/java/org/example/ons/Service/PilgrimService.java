package org.example.ons.Service;

import lombok.AllArgsConstructor;
import org.example.ons.Api.ApiException;
import org.example.ons.DTO.PilgrimResponseDTO;
import org.example.ons.DTO.VerifyPilgrimDTO;
import org.example.ons.Enum.AuthorizedStaffRole;
import org.example.ons.Model.Accommodation;
import org.example.ons.Model.AssistanceReport;
import org.example.ons.Model.AuthorizedStaff;
import org.example.ons.Model.Campaign;
import org.example.ons.Model.Pilgrim;
import org.example.ons.Model.User;
import org.example.ons.Repository.AccommodationRepository;
import org.example.ons.Repository.AssistanceReportRepository;
import org.example.ons.Repository.CampaignRepository;
import org.example.ons.Repository.PilgrimRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class PilgrimService {

    private final PilgrimRepository pilgrimRepository;
    private final CampaignRepository campaignRepository;
    private final AssistanceReportRepository assistanceReportRepository;
    private final AccommodationRepository accommodationRepository;
    private final AuthorizedStaffService authorizedStaffService;
    private final PilgrimCardService pilgrimCardService;

    @Transactional
    public void createPilgrim(User user, Pilgrim pilgrim) {

        Campaign campaign = getSupervisorCampaign(user);

        Accommodation accommodation = getCampaignAccommodation(
                campaign.getId(),
                pilgrim.getAccommodation()
        );

        pilgrim.setId(null);
        pilgrim.setCampaign(campaign);
        pilgrim.setAccommodation(accommodation);

        pilgrimRepository.save(pilgrim);
    }

    public List<Pilgrim> getAllPilgrims(User user) {

        requireActiveUser(user);

        if ("ADMIN".equals(user.getRole())) {
            return pilgrimRepository.findAll();
        }

        Integer campaignId;
        if ("CAMPAIGN_OWNER".equals(user.getRole())) {
            campaignId = getSupervisorCampaign(user).getId();
        } else if ("AuthorizedStaff".equals(user.getRole())) {
            campaignId = getCampaignStaff(user).getCampaign().getId();
        } else {
            throw new AccessDeniedException(
                    "Only admins and campaign members can view pilgrim lists"
            );
        }

        return pilgrimRepository.findByCampaign_Id(campaignId);
    }

    public Pilgrim getPilgrimById(
            User user,
            Integer pilgrimId) {

        return getAccessiblePilgrim(user, pilgrimId);
    }

    public Campaign getPilgrimCampaign(
            User user,
            Integer pilgrimId) {

        Pilgrim pilgrim = getAccessiblePilgrim(user, pilgrimId);

        if (pilgrim.getCampaign() == null) {
            throw new ApiException(
                    "No campaign associated with this pilgrim"
            );
        }

        return pilgrim.getCampaign();
    }

    public Accommodation getPilgrimAccommodation(
            User user,
            Integer pilgrimId) {

        Pilgrim pilgrim = getAccessiblePilgrim(user, pilgrimId);

        if (pilgrim.getAccommodation() == null) {
            throw new ApiException(
                    "No accommodation associated with this pilgrim"
            );
        }

        return pilgrim.getAccommodation();
    }

    public List<AssistanceReport> getPilgrimReports(
            User user,
            Integer pilgrimId) {

        getAccessiblePilgrim(user, pilgrimId);

        List<AssistanceReport> reports =
                assistanceReportRepository.findByPilgrim_Id(pilgrimId);

        if ("FIELDـSTAFF".equals(user.getRole())) {

            return reports.stream()
                    .filter(report ->
                            report.getFieldStaff() != null
                                    && user.getId().equals(
                                    report.getFieldStaff().getId()
                            )
                    )
                    .toList();
        }

        if ("AuthorizedStaff".equals(user.getRole())
                || "CAMPAIGN_OWNER".equals(user.getRole())) {

            Integer campaignId;

            if ("CAMPAIGN_OWNER".equals(user.getRole())) {

                campaignId = getSupervisorCampaign(user).getId();

            } else {

                campaignId = getCampaignStaff(user)
                        .getCampaign().getId();
            }

            return reports.stream()
                    .filter(report ->
                            report.getCampaign() != null
                                    && campaignId.equals(
                                    report.getCampaign().getId()
                            )
                    )
                    .toList();
        }

        return reports;
    }

    @Transactional
    public void updatePilgrim(
            User user,
            Integer pilgrimId,
            Pilgrim updatedPilgrim) {

        Campaign campaign = getSupervisorCampaign(user);

        Pilgrim existingPilgrim = getRequiredPilgrim(pilgrimId);

        requirePilgrimCampaign(
                campaign.getId(),
                existingPilgrim
        );

        Accommodation accommodation = getCampaignAccommodation(
                campaign.getId(),
                updatedPilgrim.getAccommodation()
        );

        existingPilgrim.setFullName(updatedPilgrim.getFullName());
        existingPilgrim.setNationality(updatedPilgrim.getNationality());
        existingPilgrim.setPhoneNumber(updatedPilgrim.getPhoneNumber());
        existingPilgrim.setVisaNumber(updatedPilgrim.getVisaNumber());
        existingPilgrim.setCampaign(campaign);
        existingPilgrim.setAccommodation(accommodation);

        pilgrimRepository.save(existingPilgrim);
    }

    @Transactional
    public void deletePilgrim(
            User user,
            Integer pilgrimId) {

        Campaign campaign = getSupervisorCampaign(user);

        Pilgrim pilgrim = getRequiredPilgrim(pilgrimId);

        requirePilgrimCampaign(campaign.getId(), pilgrim);

        pilgrimRepository.delete(pilgrim);
    }

    public Object searchPilgrim(
            User user,
            String visaNumber,
            String fullName) {

        requireActiveUser(user);

        Integer campaignId = null;

        if ("CAMPAIGN_OWNER".equals(user.getRole())) {

            campaignId = getSupervisorCampaign(user).getId();

        } else if (!"ADMIN".equals(user.getRole())) {

            campaignId = getCampaignStaff(user)
                    .getCampaign().getId();
        }

        if (visaNumber != null && !visaNumber.isBlank()) {

            Pilgrim pilgrim =
                    pilgrimRepository.findPilgrimByVisaNumber(
                            visaNumber.trim()
                    );

            if (pilgrim == null) {
                throw new ApiException("Pilgrim not found");
            }

            if (campaignId != null) {
                requirePilgrimCampaign(campaignId, pilgrim);
            }

            return pilgrim;
        }

        if (fullName != null && !fullName.isBlank()) {

            List<Pilgrim> pilgrims =
                    pilgrimRepository.findByFullNameContainingIgnoreCase(
                            fullName.trim()
                    );

            if (campaignId == null) {
                return pilgrims;
            }

            Integer staffCampaignId = campaignId;

            return pilgrims.stream()
                    .filter(pilgrim ->
                            pilgrim.getCampaign() != null
                                    && staffCampaignId.equals(
                                    pilgrim.getCampaign().getId()
                            )
                    )
                    .toList();
        }

        throw new ApiException(
                "Provide visaNumber or fullName to search"
        );
    }

    public PilgrimResponseDTO getPilgrimResponseById(
            Integer pilgrimId) {
        Pilgrim pilgrim = pilgrimRepository.findPilgrimById(pilgrimId);
        if (pilgrim==null){
            throw new ApiException("pilgrim not found");
        }
        Campaign campaign = pilgrim.getCampaign();

        if (campaign == null) {
            throw new ApiException(
                    "No campaign linked to this pilgrim"
            );
        }

        return new PilgrimResponseDTO(
                pilgrim.getFullName(),
                pilgrim.getNationality(),
                pilgrim.getPhoneNumber(),
                pilgrim.getVisaNumber(),
                campaign.getId(),
                campaign.getUser().getName()
        );
    }

    public Pilgrim identifyPilgrim(String cardNumber) {

        return pilgrimCardService.identifyPilgrimByCardNumber(
                cardNumber
        );
    }

    public Map<String, Object> verifyPilgrimIdentity(
            VerifyPilgrimDTO dto) {

        Pilgrim pilgrim =
                pilgrimCardService.verifyPilgrimIdentity(dto);

        return Map.of(
                "message", "Pilgrim identity verified successfully",
                "pilgrimId", pilgrim.getId(),
                "fullName", pilgrim.getFullName(),
                "visaNumber", pilgrim.getVisaNumber()
        );
    }

    private void requireActiveUser(User user) {

        if (user == null || !user.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }
    }

    private AuthorizedStaff getCampaignStaff(User user) {

        requireActiveUser(user);

        if (!"AuthorizedStaff".equals(user.getRole())) {
            throw new AccessDeniedException(
                    "Only campaign staff can access this endpoint"
            );
        }

        AuthorizedStaff staff =
                authorizedStaffService.getStaffById(user.getId());

        if (staff.getCampaign() == null) {
            throw new AccessDeniedException(
                    "You are not linked to a campaign"
            );
        }

        return staff;
    }

    private Campaign getSupervisorCampaign(User user) {

        requireActiveUser(user);

        if ("CAMPAIGN_OWNER".equals(user.getRole())) {

            Campaign campaign = campaignRepository
                    .findCampaignById(user.getId());

            if (campaign == null
                    || campaign.getUser() == null
                    || !user.getId().equals(
                    campaign.getUser().getId())) {
                throw new AccessDeniedException(
                        "You are not linked to a campaign"
                );
            }

            return campaign;
        }

        AuthorizedStaff staff = getCampaignStaff(user);

        if (staff.getStaffRole()
                != AuthorizedStaffRole.SUPERVISOR) {
            throw new AccessDeniedException(
                    "Only supervisors can manage pilgrims"
            );
        }

        Campaign campaign = campaignRepository.findCampaignById(
                staff.getCampaign().getId()
        );

        if (campaign == null) {
            throw new ApiException("Campaign not found");
        }

        return campaign;
    }

    private Pilgrim getRequiredPilgrim(Integer pilgrimId) {

        Pilgrim pilgrim =
                pilgrimRepository.findPilgrimById(pilgrimId);

        if (pilgrim == null) {
            throw new ApiException(
                    "Pilgrim not found with ID: " + pilgrimId
            );
        }

        return pilgrim;
    }

    private void requirePilgrimCampaign(
            Integer campaignId,
            Pilgrim pilgrim) {

        if (pilgrim.getCampaign() == null
                || !campaignId.equals(
                pilgrim.getCampaign().getId()
        )) {

            throw new AccessDeniedException(
                    "This pilgrim does not belong to your campaign"
            );
        }
    }

    private Pilgrim getAccessiblePilgrim(
            User user,
            Integer pilgrimId) {

        requireActiveUser(user);

        Pilgrim pilgrim = getRequiredPilgrim(pilgrimId);

        if ("ADMIN".equals(user.getRole())) {
            return pilgrim;
        }

        if ("CAMPAIGN_OWNER".equals(user.getRole())) {

            Campaign campaign = getSupervisorCampaign(user);

            requirePilgrimCampaign(campaign.getId(), pilgrim);

            return pilgrim;
        }

        if ("AuthorizedStaff".equals(user.getRole())) {

            Integer campaignId =
                    getCampaignStaff(user).getCampaign().getId();

            requirePilgrimCampaign(campaignId, pilgrim);

            return pilgrim;
        }

        if ("FIELDـSTAFF".equals(user.getRole())) {

            boolean assigned = assistanceReportRepository
                    .findByPilgrim_Id(pilgrimId)
                    .stream()
                    .anyMatch(report ->
                            report.getFieldStaff() != null
                                    && user.getId().equals(
                                    report.getFieldStaff().getId()
                            )
                    );

            if (assigned) {
                return pilgrim;
            }
        }

        throw new AccessDeniedException(
                "You cannot access this pilgrim"
        );
    }

    private Accommodation getCampaignAccommodation(
            Integer campaignId,
            Accommodation requestedAccommodation) {

        if (requestedAccommodation == null) {
            return null;
        }

        if (requestedAccommodation.getId() == null) {
            throw new ApiException("Accommodation ID is required");
        }

        Accommodation accommodation = accommodationRepository
                .findById(requestedAccommodation.getId())
                .orElseThrow(
                        () -> new ApiException("Accommodation not found")
                );

        if (accommodation.getCampaign() == null
                || !campaignId.equals(
                accommodation.getCampaign().getId()
        )) {

            throw new AccessDeniedException(
                    "This accommodation does not belong to your campaign"
            );
        }

        return accommodation;
    }
}