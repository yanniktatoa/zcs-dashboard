package com.groupeisi.user.mapping;

import com.groupeisi.user.dto.UserRequestDto;
import com.groupeisi.user.dto.UserResponseDto;
import com.groupeisi.user.entities.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "id", ignore = true)
    UserEntity toEntity(UserRequestDto dto);

    UserResponseDto toDto(UserEntity entity);
}
