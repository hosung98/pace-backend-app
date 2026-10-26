package com.pace.run.entity;

import jakarta.persistence.*;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.pace.run.vo.RoutePoint;
import com.pace.run.vo.RouteBounds;
import com.pace.run.dto.RunRouteResponse;
import com.pace.run.util.RouteGeometry;

@Entity
@Table(name = "run_routes")
public class RunRoute {
    @Id
    private Long runId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<RoutePoint> points;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<List<Double>> simplifiedPoints;
    private Double minLat;
    private Double maxLat;
    private Double minLng;
    private Double maxLng;

    protected RunRoute() {}
    public Long getRunId() { return runId; }
    public List<RoutePoint> getPoints() { return List.copyOf(points); }
    public RunRoute(long runId, List<RoutePoint> points) {
        this.runId = runId;
        this.points = points;
        simplifiedPoints = RouteGeometry.simplify(points);
        RouteBounds bounds = RouteGeometry.bounds(points);
        if (bounds != null) {
            minLat = bounds.minLat(); maxLat = bounds.maxLat();
            minLng = bounds.minLng(); maxLng = bounds.maxLng();
        }
    }
    public RunRouteResponse response(String mode) {
        boolean full = mode.equals("full");
        return new RunRouteResponse(mode, full ? points.stream().map(p -> List.of(p.latitude(), p.longitude())).toList()
                : simplifiedPoints, minLat == null ? null : new RouteBounds(minLat, maxLat, minLng, maxLng),
                full ? points : null);
    }
}
