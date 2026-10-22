package com.pace.run.util;

import com.pace.common.exception.BusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.pace.run.dto.CreateRunRequest;
import com.pace.run.vo.RoutePoint;
import com.pace.run.vo.RouteBounds;

public final class RouteGeometry {
    private RouteGeometry() {}

    public static void validate(CreateRunRequest request) {
        if (request.routePoints().size() > 20000) {
            throw new BusinessException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE", "경로 포인트는 20,000개를 초과할 수 없습니다.");
        }
        Instant future = Instant.now().plusSeconds(300);
        if (request.endedAt().isBefore(request.startedAt())
                || request.endedAt().isAfter(future) || request.startedAt().isAfter(future)
                || request.durationSeconds() > java.time.Duration.between(request.startedAt(), request.endedAt()).getSeconds()) {
            throw BusinessException.invalid("러닝 시작·종료 시각 또는 소요 시간이 올바르지 않습니다.");
        }
        Instant previous = request.startedAt();
        for (RoutePoint point : request.routePoints()) {
            if (point.recordedAt().isBefore(previous) || point.recordedAt().isAfter(request.endedAt())) {
                throw BusinessException.invalid("경로 시각은 러닝 시간 안에 있어야 하며 오름차순이어야 합니다.");
            }
            if (!Double.isFinite(point.latitude()) || !Double.isFinite(point.longitude())
                    || !finite(point.altitudeMeters()) || !finite(point.accuracyMeters())
                    || !finite(point.speedMetersPerSecond())) {
                throw BusinessException.invalid("경로에는 유한한 숫자만 사용할 수 있습니다.");
            }
            previous = point.recordedAt();
        }
    }

    private static boolean finite(Double value) { return value == null || Double.isFinite(value); }

    public static List<List<Double>> simplify(List<RoutePoint> points) {
        if (points.size() <= 400) return points.stream().map(p -> List.of(p.latitude(), p.longitude())).toList();
        var result = new ArrayList<List<Double>>(400);
        for (int i = 0; i < 400; i++) {
            RoutePoint point = points.get((int) ((long) i * (points.size() - 1) / 399));
            result.add(List.of(point.latitude(), point.longitude()));
        }
        return result;
    }

    public static RouteBounds bounds(List<RoutePoint> points) {
        if (points.isEmpty()) return null;
        double minLat = 90, maxLat = -90, minLng = 180, maxLng = -180;
        for (RoutePoint point : points) {
            minLat = Math.min(minLat, point.latitude());
            maxLat = Math.max(maxLat, point.latitude());
            minLng = Math.min(minLng, point.longitude());
            maxLng = Math.max(maxLng, point.longitude());
        }
        return new RouteBounds(minLat, maxLat, minLng, maxLng);
    }

    public static double distance(List<RoutePoint> points) {
        double meters = 0;
        for (int i = 1; i < points.size(); i++) {
            RoutePoint a = points.get(i - 1), b = points.get(i);
            double dLat = Math.toRadians(b.latitude() - a.latitude());
            double dLng = Math.toRadians(b.longitude() - a.longitude());
            double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(a.latitude()))
                    * Math.cos(Math.toRadians(b.latitude())) * Math.pow(Math.sin(dLng / 2), 2);
            meters += 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
        }
        return meters;
    }

    public static Integer pace(long duration, long distance) {
        return distance == 0 ? null : Math.toIntExact(Math.round(duration * 1000.0 / distance));
    }
}
