package com.groupeisi.loan.mapping;

import com.groupeisi.loan.dto.LoanResponseDto;
import com.groupeisi.loan.entities.LoanEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LoanMapper {
    LoanResponseDto toDto(LoanEntity entity);
}
