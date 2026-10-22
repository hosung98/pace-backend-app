package com.pace.crew.service;

import com.pace.crew.dto.*;
import java.util.List;

public interface CrewService {
    List<CrewRankingResponse> rankings(String subject, String period);
    List<CrewSearchResponse> search(String subject, String query, String sido, String sigungu);
    MyCrewResponse myCrew(String subject);
    CrewDetailResponse detail(String subject, String crewId, String period);
    CrewIdResponse create(String subject, CreateCrewRequest request);
    CrewIdResponse joinByCode(String subject, String code);
    void requestJoin(String subject, String crewId);
    void cancelMyRequest(String subject);
    void leave(String subject, String crewId);
    InviteCodeResponse regenerateInviteCode(String subject, String crewId);
    List<CrewJoinRequestResponse> joinRequests(String subject, String crewId);
    void approve(String subject, String crewId, String userId);
    void reject(String subject, String crewId, String userId);
    void transferLeadership(String subject, String crewId, String userId);
}
