        package org.example.ons.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Pilgrim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Pilgrim name is required")
    @Size(max = 120, message = "Name must not exceed 120 characters")
    @Column(nullable = false)
    private String fullName;

    @NotBlank(message = "Nationality is required")
    @Size(max = 60, message = "Nationality must not exceed 60 characters")
    @Column(nullable = false)
    private String nationality;

    @Pattern(
            regexp = "^\\+?[0-9]{8,15}$",
            message = "Invalid phone number"
    )
    @Column(nullable = false)
    private String phoneNumber;

    @NotBlank(message = "Visa number is required")
    @Column(unique = true, nullable = false)
    private String visaNumber;

    @ManyToOne
    @JoinColumn(nullable = false)
    @JsonIgnore
    private Campaign campaign;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Integer getCampaignId() {
        return campaign == null ? null : campaign.getId();
    }

    @ManyToOne
    @JoinColumn
    private Accommodation accommodation;

    @OneToOne(mappedBy = "pilgrim")
    @JsonIgnore
    private PilgrimCard pilgrimCard;
}