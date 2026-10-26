package com.pace.progress.util;

import com.pace.run.vo.RoutePoint;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public final class FastestKilometer {
    private static final double EARTH_RADIUS_METERS = 6371000;
    private static final double TARGET_METERS = 1000;
    private static final double DISTANCE_EPSILON = 1e-6;

    private FastestKilometer() {}

    public static OptionalDouble seconds(List<RoutePoint> points) {
        List<Sample> continuous = new ArrayList<>();
        RoutePoint previous = null;
        double best = Double.POSITIVE_INFINITY;
        for (RoutePoint point : points) {
            if (!valid(point)) {
                best = Math.min(best, best(continuous));
                continuous.clear();
                previous = null;
                continue;
            }
            if (previous == null) {
                continuous.add(new Sample(0, 0));
            } else {
                Duration duration = Duration.between(previous.recordedAt(), point.recordedAt());
                double elapsed = duration.getSeconds() + duration.getNano() / 1e9;
                double meters = distance(previous, point);
                if (elapsed <= 0 || elapsed > 30 || meters / elapsed > 8) {
                    best = Math.min(best, best(continuous));
                    continuous.clear();
                    continuous.add(new Sample(0, 0));
                } else {
                    Sample last = continuous.getLast();
                    continuous.add(new Sample(last.distance() + meters, last.seconds() + elapsed));
                }
            }
            previous = point;
        }
        best = Math.min(best, best(continuous));
        return Double.isFinite(best) ? OptionalDouble.of(best) : OptionalDouble.empty();
    }

    private static double best(List<Sample> samples) {
        if (samples.isEmpty() || samples.getLast().distance() < TARGET_METERS - DISTANCE_EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        double total = samples.getLast().distance();
        // A piecewise-linear elapsed-time window reaches its minimum at a start or end vertex.
        for (Sample vertex : samples) {
            if (vertex.distance() + TARGET_METERS <= total + DISTANCE_EPSILON) {
                best = Math.min(best, timeAt(samples, Math.min(total, vertex.distance() + TARGET_METERS), false)
                        - timeAt(samples, vertex.distance(), true));
            }
            if (vertex.distance() >= TARGET_METERS - DISTANCE_EPSILON) {
                best = Math.min(best, timeAt(samples, vertex.distance(), false)
                        - timeAt(samples, Math.max(0, vertex.distance() - TARGET_METERS), true));
            }
        }
        return best;
    }

    private static double timeAt(List<Sample> samples, double distance, boolean start) {
        int low = 0;
        int high = samples.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            double value = samples.get(middle).distance();
            if (value < distance || start && value == distance) low = middle + 1;
            else high = middle;
        }
        if (start && low > 0 && samples.get(low - 1).distance() == distance) {
            return samples.get(low - 1).seconds();
        }
        if (low < samples.size() && samples.get(low).distance() == distance) {
            return samples.get(low).seconds();
        }
        Sample before = samples.get(low - 1);
        Sample after = samples.get(low);
        double fraction = (distance - before.distance()) / (after.distance() - before.distance());
        return before.seconds() + fraction * (after.seconds() - before.seconds());
    }

    private static boolean valid(RoutePoint point) {
        return point != null && point.recordedAt() != null
                && finite(point.latitude()) && Math.abs(point.latitude()) <= 90
                && finite(point.longitude()) && Math.abs(point.longitude()) <= 180
                && (point.accuracyMeters() == null || finite(point.accuracyMeters())
                    && point.accuracyMeters() >= 0 && point.accuracyMeters() <= 35);
    }

    private static boolean finite(Double value) { return value != null && Double.isFinite(value); }

    private static double distance(RoutePoint a, RoutePoint b) {
        double lat = Math.toRadians(b.latitude() - a.latitude());
        double lng = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.pow(Math.sin(lat / 2), 2) + Math.cos(Math.toRadians(a.latitude()))
                * Math.cos(Math.toRadians(b.latitude())) * Math.pow(Math.sin(lng / 2), 2);
        return EARTH_RADIUS_METERS * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
    }

    private record Sample(double distance, double seconds) {}
}
