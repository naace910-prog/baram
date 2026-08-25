package com.wind.guild.web.dto;

import com.wind.guild.domain.RaidTarget;
import jakarta.validation.constraints.NotBlank;

public class TargetDto {

    public record UpsertRequest(
            @NotBlank String name,
            @NotBlank String dropItemName,
            String memo) {}

    public record View(Long id, String name, String dropItemName, String memo) {
        public static View of(RaidTarget t) {
            return new View(t.getId(), t.getName(), t.getDropItemName(), t.getMemo());
        }
    }
}
