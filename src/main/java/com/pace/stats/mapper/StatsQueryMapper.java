package com.pace.stats.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StatsQueryMapper {
    List<DailyStatsRow> daily(@Param("userId") long userId, @Param("zone") String zone,
                                 @Param("start") Instant start, @Param("end") Instant end);
    AggregateStatsRow allTime(@Param("userId") long userId);
}
