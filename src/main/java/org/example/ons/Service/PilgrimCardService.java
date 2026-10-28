package org.example.ons.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.example.ons.Api.ApiException;
import org.example.ons.DTO.VerifyPilgrimDTO;
import org.example.ons.DTO.PilgrimCardLookupDTO;
import org.example.ons.Enum.AuthorizedStaffRole;
import org.example.ons.Model.AuthorizedStaff;
import org.example.ons.Model.Pilgrim;
import org.example.ons.Model.PilgrimCard;
import org.example.ons.Model.User;
import org.example.ons.Repository.AuthorizedStaffRepository;
import org.example.ons.Repository.PilgrimCardRepository;
import org.example.ons.Repository.PilgrimRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PilgrimCardService {

    private final PilgrimCardRepository pilgrimCardRepository;
    private final PilgrimRepository pilgrimRepository;
    private final AuthorizedStaffRepository authorizedStaffRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void createPilgrimCard(
            User user,
            Integer pilgrimId,
            PilgrimCard pilgrimCard) {

        requireActiveUser(user);

        Pilgrim pilgrim =
                pilgrimRepository.findPilgrimById(pilgrimId);

        if (pilgrim == null) {
            throw new ApiException(
                    "Pilgrim not found with ID: " + pilgrimId
            );
        }

        requireCampaignAccess(user, pilgrim, true);

        if (pilgrim.getPilgrimCard() != null) {
            throw new ApiException(
                    "This pilgrim already has a card"
            );
        }

        // New cards always receive a database-generated identifier.
        pilgrimCard.setId(null);

        pilgrimCard.setPilgrim(pilgrim);

        // Insert a new card; never merge over an existing card.
        entityManager.persist(pilgrimCard);
    }

    @Transactional(readOnly = true)
    public PilgrimCardLookupDTO getCardForPilgrim(User user, Integer pilgrimId) {
        requireActiveUser(user);
        Pilgrim pilgrim = pilgrimRepository.findPilgrimById(pilgrimId);
        if (pilgrim == null) throw new ApiException("Pilgrim not found");
        // Check campaign ownership before revealing whether a card exists.
        requireCampaignAccess(user, pilgrim, false);
        PilgrimCard card = pilgrim.getPilgrimCard();
        if (card == null) return new PilgrimCardLookupDTO(pilgrimId, false, null);
        return new PilgrimCardLookupDTO(pilgrimId, true,
                new PilgrimCardLookupDTO.CardDetails(card.getId(), card.getCardNumber(),
                        card.getCardType(), card.getStatus(), card.getImageReference()));
    }

    public List<PilgrimCard> getAllPilgrimCards(User user) {

        requireActiveUser(user);

        if (!"ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException(
                    "Only admins can view all pilgrim cards"
            );
        }

        return pilgrimCardRepository.findAll();
    }

    public PilgrimCard getPilgrimCardById(
            User user,
            Integer cardId) {

        requireActiveUser(user);

        PilgrimCard card = getRequiredCard(cardId);

        requireCampaignAccess(
                user,
                card.getPilgrim(),
                false
        );

        return card;
    }

    @Transactional
    public void updatePilgrimCard(
            User user,
            Integer cardId,
            PilgrimCard updatedCard) {

        requireActiveUser(user);

        PilgrimCard card = getRequiredCard(cardId);

        requireCampaignAccess(
                user,
                card.getPilgrim(),
                true
        );

        card.setCardNumber(updatedCard.getCardNumber());
        card.setCardType(updatedCard.getCardType());
        card.setStatus(updatedCard.getStatus());
        card.setImageReference(updatedCard.getImageReference());

        pilgrimCardRepository.save(card);
    }

    @Transactional
    public void deletePilgrimCard(
            User user,
            Integer cardId) {

        requireActiveUser(user);

        PilgrimCard card = getRequiredCard(cardId);
        Pilgrim pilgrim = card.getPilgrim();

        requireCampaignAccess(user, pilgrim, true);

        pilgrim.setPilgrimCard(null);

        pilgrimCardRepository.delete(card);
        pilgrimCardRepository.flush();
    }

    public Pilgrim identifyPilgrimByCardNumber(
            String cardNumber) {

        if (cardNumber == null || cardNumber.isBlank()) {
            throw new ApiException(
                    "Card number is required"
            );
        }

        PilgrimCard card =
                pilgrimCardRepository.findPilgrimCardByCardNumber(
                        cardNumber.trim()
                );

        if (card == null) {
            throw new ApiException(
                    "Pilgrim card not found"
            );
        }

        if (!"ACTIVE".equals(card.getStatus())) {
            throw new ApiException(
                    "Pilgrim card is not active"
            );
        }

        if (card.getPilgrim() == null) {
            throw new ApiException(
                    "No pilgrim is associated with this card"
            );
        }

        return card.getPilgrim();
    }

    public Pilgrim verifyPilgrimIdentity(
            VerifyPilgrimDTO dto) {

        if (dto == null
                || dto.getVisaNumber() == null
                || dto.getVisaNumber().isBlank()
                || dto.getCardNumber() == null
                || dto.getCardNumber().isBlank()) {

            throw new ApiException(
                    "Visa number and card number are required"
            );
        }

        Pilgrim pilgrim = identifyPilgrimByCardNumber(
                dto.getCardNumber()
        );

        if (!pilgrim.getVisaNumber().equals(
                dto.getVisaNumber().trim()
        )) {
            throw new ApiException(
                    "Visa number does not match the card holder"
            );
        }

        return pilgrim;
    }

    private PilgrimCard getRequiredCard(Integer cardId) {

        PilgrimCard card =
                pilgrimCardRepository.findPilgrimCardById(
                        cardId
                );

        if (card == null) {
            throw new ApiException(
                    "Pilgrim card not found with ID: " + cardId
            );
        }

        return card;
    }

    private void requireActiveUser(User user) {

        if (user == null || !user.isActive()) {
            throw new AccessDeniedException(
                    "An active authenticated account is required"
            );
        }
    }

    private void requireCampaignAccess(
            User user,
            Pilgrim pilgrim,
            boolean writing) {

        requireActiveUser(user);
        if ("CAMPAIGN_OWNER".equals(user.getRole())) {

            if (user.getId() == null
                    || pilgrim == null
                    || pilgrim.getCampaign() == null
                    || pilgrim.getCampaign().getUser() == null
                    || !user.getId().equals(
                    pilgrim.getCampaign().getUser().getId())) {
                throw new AccessDeniedException(
                        "This pilgrim does not belong to your campaign"
                );
            }

            return;
        }

        if (!writing && "ADMIN".equals(user.getRole())) {
            return;
        }

        if (!"AuthorizedStaff".equals(user.getRole())) {
            throw new AccessDeniedException(
                    "Only campaign staff can access this card"
            );
        }

        AuthorizedStaff staff = authorizedStaffRepository
                .findById(user.getId())
                .orElseThrow(
                        () -> new AccessDeniedException(
                                "Campaign staff account not found"
                        )
                );

        if (writing
                && staff.getStaffRole()
                != AuthorizedStaffRole.SUPERVISOR) {

            throw new AccessDeniedException(
                    "Only supervisors can manage pilgrim cards"
            );
        }

        if (staff.getCampaign() == null
                || pilgrim == null
                || pilgrim.getCampaign() == null
                || !staff.getCampaign().getId().equals(
                pilgrim.getCampaign().getId()
        )) {

            throw new AccessDeniedException(
                    "This pilgrim does not belong to your campaign"
            );
        }
    }
}