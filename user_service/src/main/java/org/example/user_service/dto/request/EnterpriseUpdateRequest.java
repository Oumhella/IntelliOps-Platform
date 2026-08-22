package org.example.user_service.dto.request;
import jakarta.validation.constraints.*;
public record EnterpriseUpdateRequest(
 @NotBlank @Size(max=160) String companyName, @NotBlank @Size(max=120) String activityType,
 @Size(max=160) String legalName, @Size(max=80) String legalIdentifier, @Size(max=80) String taxIdentifier,
 @Email @Size(max=160) String contactEmail, @Size(max=40) String contactPhone, @Size(max=255) String website,
 @Size(max=255) String addressLine1, @Size(max=255) String addressLine2, @Size(max=100) String city,
 @Size(max=30) String postalCode, @Pattern(regexp="[A-Z]{2}") String countryCode,
 @Pattern(regexp="[A-Z]{3}") String currencyCode, @Size(max=80) String timezone) {}
