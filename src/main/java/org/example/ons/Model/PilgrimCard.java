
package org.example.ons.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class PilgrimCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer Id;

    @NotBlank(message = "Card number is required")
    @Size(max = 50, message = "Card number must not exceed 50 characters")
    @Column(nullable = false, unique = true, length = 50)
    private String cardNumber;

    @NotBlank(message = "Card type is required")
    @Column(nullable = false, length = 30)
    private String cardType;

    @NotBlank(message = "Card status is required")
    @Pattern(
            regexp = "ACTIVE|INACTIVE|EXPIRED",
            message = "Invalid card status"
    )
    @Column(nullable = false, length = 20)
    private String status;

    @Size(max = 255, message = "Image reference must not exceed 255 characters")
    @Column(length = 255)
    private String imageReference;

    @OneToOne
    @JoinColumn(nullable = false, unique = true)
    @JsonIgnore
    private Pilgrim pilgrim;
}
