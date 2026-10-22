package com.pace.crew.repository;

public interface CrewListRow {
    String getId();
    String getName();
    String getIntro();
    String getSido();
    String getSigungu();
    long getMemberCount();
    long getDistanceMeters();
    boolean getIsMine();
}
