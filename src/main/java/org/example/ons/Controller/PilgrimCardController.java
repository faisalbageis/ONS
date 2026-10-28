package org.example.ons.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ons.Api.ApiResponse;
import org.example.ons.Model.PilgrimCard;
import org.example.ons.Model.User;
import org.example.ons.Service.PilgrimCardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pilgrim-cards")
@RequiredArgsConstructor
public class PilgrimCardController {

    private final PilgrimCardService pilgrimCardService;

    @PostMapping("/add")
    public ResponseEntity<?> createPilgrimCard(
            @AuthenticationPrincipal User user,
            @RequestParam Integer pilgrimId,
            @Valid @RequestBody PilgrimCard pilgrimCard) {

        pilgrimCardService.createPilgrimCard(
                user,
                pilgrimId,
                pilgrimCard
        );

        return ResponseEntity.ok(
                new ApiResponse(
                        "Pilgrim card created successfully"
                )
        );
    }

    @GetMapping("/pilgrim/{pilgrimId}")
    public ResponseEntity<?> getCardForPilgrim(
            @AuthenticationPrincipal User user,
            @PathVariable Integer pilgrimId) {
        return ResponseEntity.ok(pilgrimCardService.getCardForPilgrim(user, pilgrimId));
    }

    @GetMapping("/get")
    public ResponseEntity<?> getAllPilgrimCards(
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(
                pilgrimCardService.getAllPilgrimCards(user)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<?> getPilgrimCardById(
            @AuthenticationPrincipal User user,
            @RequestParam Integer cardId) {

        return ResponseEntity.ok(
                pilgrimCardService.getPilgrimCardById(
                        user,
                        cardId
                )
        );
    }

    @PutMapping("/update")
    public ResponseEntity<?> updatePilgrimCard(
            @AuthenticationPrincipal User user,
            @RequestParam Integer cardId,
            @Valid @RequestBody PilgrimCard pilgrimCard) {

        pilgrimCardService.updatePilgrimCard(
                user,
                cardId,
                pilgrimCard
        );

        return ResponseEntity.ok(
                new ApiResponse(
                        "Pilgrim card updated successfully"
                )
        );
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deletePilgrimCard(
            @AuthenticationPrincipal User user,
            @RequestParam Integer cardId) {

        pilgrimCardService.deletePilgrimCard(user, cardId);

        return ResponseEntity.ok(
                new ApiResponse(
                        "Pilgrim card deleted successfully"
                )
        );
    }
}