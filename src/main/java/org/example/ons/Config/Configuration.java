package org.example.ons.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@org.springframework.context.annotation.Configuration
@EnableWebSecurity
public class Configuration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/kiosks/heartbeat/all"

                        ).permitAll()


                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/kiosks/get",
                                "/api/v1/pilgrims/etPilgrimResponse/{id}",
                                "/api/v1/reports/{reportId:\\d+}/history",
                                "/report/{reportId:\\d+}/history"
                        ).permitAll()


                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/map/kiosks/{kioskId}/nearest-place"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/reports/self-service",
                                "/report/self-service",
                                "/api/v1/campaigns/register",
                                "/api/v1/payments/campaign/{campaignId}",
                                "/api/v1/payments/campaign/{campaignId}/{paymentId}/verify",
                                "/api/v1/identification/qr/scan",
                                "/api/v1/identification/validate",
                                "/api/v1/pilgrims/identify",
                                "/api/v1/pilgrims/verify",
                                "/api/v1/pilgrims/manual-identification",
                                "/api/v1/identification/scan"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/campaigns/update"
                        ).hasAuthority("CAMPAIGN_OWNER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/authorized-staff/get/employees",
                                "/api/v1/accommodations/my-campaign"
                        ).hasAnyAuthority("AuthorizedStaff", "CAMPAIGN_OWNER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/accommodations/add"
                        ).hasAnyAuthority("AuthorizedStaff", "CAMPAIGN_OWNER")

                        .requestMatchers(
                                "/api/v1/authorized-staff/create/{campaignId}",
                                "/api/v1/authorized-staff/get-all",
                                "/api/v1/authorized-staff/update/{id}",
                                "/api/v1/authorized-staff/delete/{id}"
                        ).hasAnyAuthority("ADMIN", "CAMPAIGN_OWNER")

                        .requestMatchers(
                                "/api/v1/accommodations/update/{id}",
                                "/api/v1/accommodations/{accommodationId}/status",
                                "/api/v1/accommodations/delete/{id}",
                                "/api/v1/pilgrims/add",
                                "/api/v1/pilgrims/update/{id}",
                                "/api/v1/pilgrims/delete/{id}",
                                "/api/v1/pilgrim-cards/add",
                                "/api/v1/pilgrim-cards/update",
                                "/api/v1/pilgrim-cards/delete"
                        ).hasAnyAuthority("AuthorizedStaff", "CAMPAIGN_OWNER")

                        .requestMatchers(
                                "/notification/add",
                                "/notification/get",
                                "/notification/get/{id}",
                                "/notification/update/{id}",
                                "/notification/delete/{id}",
                                "/api/v1/dashboard/summary",
                                "/api/v1/dashboard/reports-by-status",
                                "/api/v1/dashboard/reports-trend",
                                "/api/v1/dashboard/response-time",
                                "/api/v1/dashboard/resolution-time",
                                "/api/v1/field-staff/create",
                                "/api/v1/field-staff/get-all",
                                "/api/v1/field-staff/get/{id}",
                                "/api/v1/field-staff/update/{id}",
                                "/api/v1/field-staff/delete/{id}",
                                "/api/v1/identification/requests/{requestId}",
                                "/api/v1/identification/requests/{requestId}/retry",
                                "/api/v1/identification/requests/{requestId}/manual-review",
                                "/api/v1/kiosks/add",
                                "/api/v1/kiosks/update/{id}",
                                "/api/v1/kiosks/delete/{id}",
                                "/api/v1/kiosks/update-status/{id}",
                                "/api/v1/kiosks/update-location/{kioskId}",
                                "/api/v1/kiosks/{kioskId}/reports",
                                "/api/v1/auth/get/all",
                                "/api/v1/auth/get/{id}",
                                "/api/v1/auth/activate/{id}",
                                "/api/v1/auth/deactivate/{id}",
                                "/api/v1/campaigns/get",
                                "/api/v1/campaigns/delete/{id}",
                                "/api/v1/accommodations/get",
                                "/api/v1/accommodations/{accommodationId}/pilgrims",
                                "/api/v1/accommodations/{accommodationId}/reports",
                                "/api/v1/accommodations/{accommodationId}/location",
                                "/api/v1/pilgrim-cards/get",
                                "/api/ai-test/qr",
                                "/api/ai-test/ocr",
                                "/api/v1/authorized-staff/get/{id}"
                        ).hasAuthority("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/accommodations/{id}"
                        ).hasAnyAuthority(
                                "ADMIN",
                                "AuthorizedStaff",
                                "CAMPAIGN_OWNER"
                        )

                        .requestMatchers(
                                "/api/v1/campaigns/me",
                                "/api/v1/campaigns/analytics",
                                "/api/v1/campaigns/analytics/email",
                                "/api/v1/authorized-staff/**",
                                "/api/v1/accommodations/**"
                        ).hasAnyAuthority("AuthorizedStaff", "CAMPAIGN_OWNER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/field-staff/availability",
                                "/api/v1/pilgrims/search",
                                "/api/v1/pilgrim-cards/me",
                                "/api/v1/pilgrim-cards/pilgrim/{pilgrimId}"
                        ).hasAnyAuthority("ADMIN", "AuthorizedStaff", "CAMPAIGN_OWNER")

                        .requestMatchers(HttpMethod.GET, "/api/v1/pilgrims/get")
                        .hasAnyAuthority("ADMIN", "AuthorizedStaff", "CAMPAIGN_OWNER")

                        .requestMatchers("/api/v1/routes")
                        .hasAnyAuthority("ADMIN", "AuthorizedStaff", "FIELDـSTAFF","CAMPAIGN_OWNER")

                        .requestMatchers(
                                "/api/v1/reports",
                                "/api/v1/reports/**",
                                "/report",
                                "/report/**",
                                "/api/v1/map/markers",
                                "/api/v1/map/nearest-kiosk",
                                "/api/v1/map/route",
                                "/api/v1/kiosks/get",
                                "/api/v1/kiosks/get/{id}",
                                "/api/v1/auth/change-password/{id}",
                                "/api/v1/pilgrims/me",
                                "/api/v1/pilgrims/{pilgrimId:\\d+}",
                                "/api/v1/pilgrims/{pilgrimId:\\d+}/campaign",
                                "/api/v1/pilgrims/{pilgrimId:\\d+}/accommodation",
                                "/api/v1/pilgrims/{pilgrimId:\\d+}/reports"
                        ).hasAnyAuthority(
                                "ADMIN", "AuthorizedStaff", "FIELDـSTAFF", "CAMPAIGN_OWNER"
                        )

                        .anyRequest().authenticated()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true)
                )
                .httpBasic(basic -> {})
                .build();
    }
}