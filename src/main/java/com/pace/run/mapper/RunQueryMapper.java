package com.pace.run.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RunQueryMapper {
    List<RunRow> list(@Param("userId") long userId, @Param("afterStart") Instant afterStart,
                            @Param("afterId") Long afterId, @Param("limit") int limit);
}
