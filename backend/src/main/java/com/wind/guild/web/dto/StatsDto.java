package com.wind.guild.web.dto;

import java.util.List;

public class StatsDto {

    public record Overview(
            long totalMembers,
            long plannedRaids,
            long doneRaids,
            long totalRevenue,
            long unpaidTotal
    ) {}

    public record MemberStat(
            Long memberId,
            String nickname,
            long attendCount,
            long totalShare,
            long unpaidShare,
            long unpaidCount
    ) {}

    public record TargetStat(
            Long targetId,
            String name,
            String dropItemName,
            long killCount,
            long totalSoldPrice,
            long avgSoldPrice
    ) {}

    public record Result(
            Overview overview,
            List<MemberStat> members,
            List<TargetStat> targets
    ) {}
}
