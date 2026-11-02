package com.fincore.platform.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Customer self-service profile update request")
public class UpdateCustomerRequest {

    @Pattern(
            regexp = "^\\+?[1-9]\\d{1,14}$",
            message = "Phone number must follow E.164 international standard format"
    )
    @Schema(description = "Updated phone number", example = "+919876543210")
    private String phoneNumber;

    @Schema(description = "Updated address line 1", example = "108 Brigade Gateway")
    private String addressLine1;

    @Schema(description = "Updated address line 2", example = "Rajajinagar")
    private String addressLine2;

    @Schema(description = "Updated city", example = "Bengaluru")
    private String city;

    @Schema(description = "Updated state", example = "Karnataka")
    private String state;

    @Schema(description = "Updated postal code", example = "560055")
    private String postalCode;

    @Schema(description = "Updated country", example = "India")
    private String country;

    public UpdateCustomerRequest() {}

    public UpdateCustomerRequest(String phoneNumber, String addressLine1, String city, String state, String postalCode) {
        this.phoneNumber = phoneNumber;
        this.addressLine1 = addressLine1;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
    }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
