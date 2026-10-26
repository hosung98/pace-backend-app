package com.pace.run.entity;

import com.pace.run.dto.CreateRunRequest;
import com.pace.run.dto.RunSummaryResponse;
import com.pace.run.util.RouteGeometry;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "runs")
public class Run {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String publicId;
    private Long userId;
    private String clientRunId;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(columnDefinition = "char(64)", length = 64)
    private String requestHash;
    private String title;
    private Instant startedAt;
    private Instant endedAt;
    private int durationSeconds;
    private int distanceMeters;
    private Integer averagePaceSecondsPerKm;
    private int routePointCount;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant deletedAt;

    protected Run() {}
    public Run(long userId, CreateRunRequest request, String hash) {
        publicId = UlidCreator.getUlid().toString();
        this.userId = userId;
        clientRunId = request.clientRunId();
        requestHash = hash;
        // DB의 마이크로초 정밀도에 맞춰 커서 경계의 오차를 방지합니다.
        startedAt = request.startedAt().truncatedTo(ChronoUnit.MICROS);
        endedAt = request.endedAt().truncatedTo(ChronoUnit.MICROS);
        durationSeconds = request.durationSeconds();
        distanceMeters = request.distanceMeters();
        averagePaceSecondsPerKm = RouteGeometry.pace(durationSeconds, distanceMeters);
        routePointCount = request.routePoints().size();
    }
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Long getUserId() { return userId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public int getDurationSeconds() { return durationSeconds; }
    public int getDistanceMeters() { return distanceMeters; }
    public String getRequestHash() { return requestHash; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setTitle(String value) { title = value; }
    public void delete() { if (deletedAt == null) deletedAt = Instant.now(); }
    public void restore() { deletedAt = null; }
    public RunSummaryResponse summary() {
        return new RunSummaryResponse(publicId, clientRunId, title, startedAt, endedAt, durationSeconds,
                distanceMeters, averagePaceSecondsPerKm, routePointCount);
    }
}
