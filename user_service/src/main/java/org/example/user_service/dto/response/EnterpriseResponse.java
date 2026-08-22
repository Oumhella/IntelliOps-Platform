package org.example.user_service.dto.response;
import org.example.user_service.entity.Enterprise;
public record EnterpriseResponse(Long id,String companyName,String activityType,String legalName,String legalIdentifier,
 String taxIdentifier,String contactEmail,String contactPhone,String website,String addressLine1,String addressLine2,
 String city,String postalCode,String countryCode,String currencyCode,String timezone) {
 public static EnterpriseResponse from(Enterprise e){return new EnterpriseResponse(e.getId(),e.getCompanyName(),e.getActivityType(),e.getLegalName(),e.getLegalIdentifier(),e.getTaxIdentifier(),e.getContactEmail(),e.getContactPhone(),e.getWebsite(),e.getAddressLine1(),e.getAddressLine2(),e.getCity(),e.getPostalCode(),e.getCountryCode(),e.getCurrencyCode(),e.getTimezone());}
}
