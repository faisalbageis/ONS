package org.example.ons.DTO;

public record PilgrimCardLookupDTO(Integer pilgrimId, boolean hasCard, CardDetails card) {
    public record CardDetails(Integer id, String cardNumber, String cardType,
                              String status, String imageReference) {}
}
