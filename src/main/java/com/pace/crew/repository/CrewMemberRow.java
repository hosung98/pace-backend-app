package com.pace.crew.repository;

public interface CrewMemberRow {
    String getUserId();
    String getNickname();
    String getRole();
    long getDistanceMeters();
    long getRunCount();
    boolean getIsMe();
}
