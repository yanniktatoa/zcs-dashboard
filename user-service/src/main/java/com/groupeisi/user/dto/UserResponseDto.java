package com.groupeisi.user.dto;

import lombok.*;

@Getter
@Setter
public class UserResponseDto {
    private Long id;
    private String name;
    private String email;
    private String role;
}
