package org.example.ons.DTO;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.ons.Enum.AuthorizedStaffRole;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthorizedStaffDTO {
    // User fields
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^05\\d{8}$",
            message = "Phone number must start with 05 and contain 10 digits"
    )
    private String phoneNumber;

    private AuthorizedStaffRole staffRole;

    private boolean active;

    @Positive(message = "Supervisor ID must be positive")
    private Integer supervisorId;



}
