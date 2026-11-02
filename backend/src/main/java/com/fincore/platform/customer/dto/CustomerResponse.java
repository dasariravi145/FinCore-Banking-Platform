package com.fincore.platform.customer.dto;

import com.fincore.platform.customer.entity.Customer;
import com.fincore.platform.customer.entity.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Customer profile representation")
public class CustomerResponse {

    @Schema(description = "Customer UUID", example = "3c983a47-c0e6-42d4-b903-ea25595304b4")
    private UUID id;

    @Schema(description = "Formatted synthetic Customer Number", example = "CUST-98214301")
    private String customerNumber;

    @Schema(description = "First name", example = "Aarav")
    private String firstName;

    @Schema(description = "Last name", example = "Sharma")
    private String lastName;

    @Schema(description = "Registered email", example = "aarav.sharma@example.com")
    private String email;

    @Schema(description = "Contact phone number", example = "+919876543210")
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
    private String country;

    @Schema(description = "Masked National Identification Number", example = "SYNTH-PAN-*****")
    private String nationalId;

    @Schema(description = "Customer account status", example = "ACTIVE")
    private CustomerStatus status;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    public CustomerResponse() {}

    public static CustomerResponse fromEntity(Customer customer) {
        CustomerResponse res = new CustomerResponse();
        res.setId(customer.getId());
        res.setCustomerNumber(customer.getCustomerNumber());
        res.setFirstName(customer.getFirstName());
        res.setLastName(customer.getLastName());
        res.setEmail(customer.getEmail());
        res.setPhoneNumber(customer.getPhoneNumber());
        res.setDateOfBirth(customer.getDateOfBirth());
        res.setAddressLine1(customer.getAddressLine1());
        res.setAddressLine2(customer.getAddressLine2());
        res.setCity(customer.getCity());
        res.setState(customer.getState());
        res.setPostalCode(customer.getPostalCode());
        res.setCountry(customer.getCountry());
        res.setStatus(customer.getStatus());
        res.setCreatedAt(customer.getCreatedAt());

        // Mask National ID for privacy
        if (customer.getNationalId() != null && customer.getNationalId().length() > 4) {
            String raw = customer.getNationalId();
            res.setNationalId(raw.substring(0, Math.min(raw.length(), 6)) + "****");
        } else {
            res.setNationalId(customer.getNationalId());
        }

        return res;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCustomerNumber() { return customerNumber; }
    public void setCustomerNumber(String customerNumber) { this.customerNumber = customerNumber; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
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
    public CustomerStatus getStatus() { return status; }
    public void setStatus(CustomerStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

