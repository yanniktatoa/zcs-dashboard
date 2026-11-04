package com.groupeisi.user.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
public class UserRequestDto {
    @NotBlank
    private String name;
    @Email
    @NotBlank
    private String email;
    @NotBlank
    private String role;
}
