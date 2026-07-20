package com.cardio.dto;

import lombok.Data;

@Data
public class ChangePasswordRequestDTO {
    private String username;
    private String oldPassword;
    private String newPassword;
}