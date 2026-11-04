package com.groupeisi.loan.dto;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.*;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class UserResponseDto {
    private Long id;
    private String name;
    private String email;
    private String role;
}