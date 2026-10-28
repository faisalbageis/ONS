package org.example.ons.Controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.ons.Api.ApiResponse;
import org.example.ons.DTO.VerifyPilgrimDTO;
import org.example.ons.Model.Pilgrim;
import org.example.ons.Model.User;
import org.example.ons.Service.PilgrimService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/pilgrims")
@AllArgsConstructor
public class PilgrimController {

    private final PilgrimService pilgrimService;

    @PostMapping("/add")
    public ResponseEntity<?> createPilgrim(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody Pilgrim pilgrim) {

        pilgrimService.createPilgrim(user, pilgrim);

        return ResponseEntity.ok(
                new ApiResponse("Pilgrim created successfully")
        );
    }

    @GetMapping("/get")
    public ResponseEntity<?> getAllPilgrims(
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(
                pilgrimService.getAllPilgrims(user)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<?> getPilgrimById(
            @AuthenticationPrincipal User user,
            @RequestParam Integer pilgrimId) {

        return ResponseEntity.ok(
                pilgrimService.getPilgrimById(user, pilgrimId)
        );
    }

    @PostMapping("/identify")
    public ResponseEntity<?> identifyPilgrim(
            @RequestBody Map<String, String> request) {

        return ResponseEntity.ok(
                pilgrimService.identifyPilgrim(
                        request.get("cardNumber")
                )
        );
    }

    @GetMapping("/{pilgrimId:\\d+}")
    public ResponseEntity<?> getPilgrimByRequiredId(
            @AuthenticationPrincipal User user,
            @PathVariable Integer pilgrimId) {

        return ResponseEntity.ok(
                pilgrimService.getPilgrimById(user, pilgrimId)
        );
    }

    @GetMapping("/{pilgrimId:\\d+}/campaign")
    public ResponseEntity<?> getPilgrimCampaign(
            @AuthenticationPrincipal User user,
            @PathVariable Integer pilgrimId) {

        return ResponseEntity.ok(
                pilgrimService.getPilgrimCampaign(user, pilgrimId)
        );
    }

    @GetMapping("/{pilgrimId:\\d+}/accommodation")
    public ResponseEntity<?> getPilgrimAccommodation(
            @AuthenticationPrincipal User user,
            @PathVariable Integer pilgrimId) {

        return ResponseEntity.ok(
                pilgrimService.getPilgrimAccommodation(
                        user,
                        pilgrimId
                )
        );
    }

    @GetMapping("/{pilgrimId:\\d+}/reports")
    public ResponseEntity<?> getPilgrimReports(
            @AuthenticationPrincipal User user,
            @PathVariable Integer pilgrimId) {

        return ResponseEntity.ok(
                pilgrimService.getPilgrimReports(user, pilgrimId)
        );
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updatePilgrim(
            @AuthenticationPrincipal User user,
            @PathVariable Integer id,
            @Valid @RequestBody Pilgrim pilgrim) {

        pilgrimService.updatePilgrim(user, id, pilgrim);

        return ResponseEntity.ok(
                new ApiResponse("Pilgrim updated successfully")
        );
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deletePilgrim(
            @AuthenticationPrincipal User user,
            @PathVariable Integer id) {

        pilgrimService.deletePilgrim(user, id);

        return ResponseEntity.ok(
                new ApiResponse("Pilgrim deleted successfully")
        );
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPilgrimIdentity(
            @Valid @RequestBody VerifyPilgrimDTO dto) {

        return ResponseEntity.ok(
                pilgrimService.verifyPilgrimIdentity(dto)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchPilgrim(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String visaNumber,
            @RequestParam(required = false) String fullName) {

        return ResponseEntity.ok(
                pilgrimService.searchPilgrim(
                        user,
                        visaNumber,
                        fullName
                )
        );
    }

    @PostMapping("/manual-identification")
    public ResponseEntity<?> manualIdentification(
            @Valid @RequestBody VerifyPilgrimDTO dto) {

        return ResponseEntity.ok(
                pilgrimService.verifyPilgrimIdentity(dto)
        );
    }

    @GetMapping("/etPilgrimResponse/{id}")
    public ResponseEntity<?> getPilgrimResponseById(
            @PathVariable Integer id) {

        return ResponseEntity.ok(
                pilgrimService.getPilgrimResponseById( id)
        );
    }
}