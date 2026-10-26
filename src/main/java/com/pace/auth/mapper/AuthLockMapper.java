package com.pace.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthLockMapper {
    // 최초 가입의 중복 생성을 여러 서버에서도 막는 트랜잭션 단위 잠금입니다.
    @Select("SELECT true FROM pg_advisory_xact_lock(hashtextextended(#{subject}, 0))")
    boolean lockGoogleSubject(@Param("subject") String subject);

    @Select("SELECT true FROM pg_advisory_xact_lock(hashtextextended(#{provider} || ':' || #{subject}, 0))")
    boolean lockSocialSubject(@Param("provider") String provider, @Param("subject") String subject);
}
