package com.fincore.platform.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Customer profile creation request")
public class CreateCustomerRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    @Schema(description = "Customer legal first name", example = "Aarav")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    @Schema(description = "Customer legal last name", example = "Sharma")
    private String lastName;

    @Pattern(
            regexp = "^\\+?[1-9]\\d{1,14}$",
            message = "Phone number must follow E.164 international standard format"
    )
    @Schema(description = "Customer phone number", example = "+919876543210")
    private String phoneNumber;

    @Schema(description = "Date of birth", example = "1992-05-15")
    private LocalDate dateOfBirth;

    @Schema(description = "Address line 1", example = "42 Residency Road")
    private String addressLine1;

    @Schema(description = "Address line 2", example = "MG Road Area")
    private String addressLine2;

    @Schema(description = "City", example = "Bengaluru")
    private String city;

    @Schema(description = "State", example = "Karnataka")
    private String state;

    @Schema(description = "Postal code", example = "560001")
    private String postalCode;

    @Schema(description = "Country", example = "India")
    private String country = "India";

    @Schema(description = "Synthetic Tax/National Identification Number", example = "SYNTH-PAN-12345")
    private String nationalId;

    public CreateCustomerRequest() {}

    public CreateCustomerRequest(String firstName, String lastName, String phoneNumber, LocalDate dateOfBirth,
                                 String addressLine1, String city, String state, String postalCode, String nationalId) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
        this.addressLine1 = addressLine1;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.nationalId = nationalId;
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
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
    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
}

