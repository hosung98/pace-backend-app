package com.pace.crew.dto;

import java.util.List;

public record CrewDetailResponse(String id, String name, String intro, CrewRegion region, String leaderNickname,
                                 long memberCount, int maxMembers, String period, Integer rank,
                                 long distanceMeters, List<CrewMemberStatResponse> members,
                                 String membership, String inviteCode, long pendingRequestCount) {}
