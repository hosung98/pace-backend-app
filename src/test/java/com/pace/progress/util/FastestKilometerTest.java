package com.pace.progress.util;

import com.pace.run.vo.RoutePoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FastestKilometerTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private RoutePoint point(double meters, double seconds) {
        return new RoutePoint(START.plusNanos(Math.round(seconds * 1e9)), 0.0,
                Math.toDegrees(meters / 6371000), null, 5.0, null);
    }

    private List<RoutePoint> uniform(double meters, double seconds) {
        var points = new ArrayList<RoutePoint>();
        for (int step = 0; step <= 10; step++) points.add(point(meters * step / 10, seconds * step / 10));
        return points;
    }

    @Test
    void exact_boundary_and_short_routes() {
        assertThat(FastestKilometer.seconds(uniform(1000, 250)).orElseThrow()).isCloseTo(250, within(1e-8));
        assertThat(FastestKilometer.seconds(uniform(999, 250))).isEmpty();
        assertThat(FastestKilometer.seconds(uniform(999.9999999, 250)).orElseThrow())
                .isCloseTo(250, within(1e-7));
        assertThat(FastestKilometer.seconds(uniform(999.99999, 250))).isEmpty();
        assertThat(FastestKilometer.seconds(List.of())).isEmpty();
    }

    @Test
    void stationary_start_uses_latest_time_and_stationary_end_uses_earliest_time() {
        var points = new ArrayList<RoutePoint>();
        points.add(point(0, 0));
        points.add(point(0, 20));
        for (int index = 1; index <= 10; index++) points.add(point(index * 100, 20 + index * 25));
        points.add(point(1000, 290));
        assertThat(FastestKilometer.seconds(points).orElseThrow()).isCloseTo(250, within(1e-7));
    }

    @Test
    void end_vertex_candidate_interpolates_a_start_between_irregular_vertices() {
        var points = new ArrayList<RoutePoint>();
        points.add(point(0, 0));
        points.add(point(120, 30));
        // Best interval ends at the fast section's final vertex, with its start inside the slow section.
        for (int index = 1; index <= 7; index++) points.add(point(120 + index * 140, 30 + index * 20));
        points.add(point(1220, 200));
        assertThat(FastestKilometer.seconds(points).orElseThrow()).isCloseTo(145, within(1e-7));
    }

    @Test
    void start_vertex_candidate_interpolates_the_end_and_internal_pause_counts() {
        var points = new ArrayList<RoutePoint>();
        points.add(point(0, 0));
        points.add(point(120, 30));
        for (int index = 1; index <= 7; index++) points.add(point(120 + index * 140, 30 + index * 20));
        points.add(point(1220, 200));
        // Reversing elapsed distance makes the optimum a start vertex instead.
        var reversed = new ArrayList<RoutePoint>();
        for (int index = points.size() - 1; index >= 0; index--) {
            RoutePoint source = points.get(index);
            double distance = Math.toRadians(source.longitude()) * 6371000;
            double elapsed = java.time.Duration.between(START, source.recordedAt()).toNanos() / 1e9;
            reversed.add(point(1220 - distance, 200 - elapsed));
        }
        assertThat(FastestKilometer.seconds(reversed).orElseThrow()).isCloseTo(145, within(1e-7));
        var pause = new ArrayList<RoutePoint>();
        for (int index = 0; index <= 10; index++) {
            pause.add(point(index * 100, index * 25 + (index > 5 ? 20 : 0)));
            if (index == 5) pause.add(point(500, 145));
        }
        assertThat(FastestKilometer.seconds(pause).orElseThrow()).isCloseTo(270, within(1e-7));
    }

    @Test
    void gaps_nonincreasing_time_and_impossible_speed_break_continuity() {
        for (String failure : List.of("gap", "duplicate", "speed", "accuracy", "coordinate")) {
            var points = new ArrayList<>(uniform(1000, 250));
            RoutePoint previous = points.get(4);
            switch (failure) {
                case "gap" -> {
                    for (int index = 5; index < points.size(); index++) {
                        RoutePoint original = points.get(index);
                        points.set(index, new RoutePoint(original.recordedAt().plusSeconds(31), 0.0,
                                original.longitude(), null, 5.0, null));
                    }
                }
                case "duplicate" -> points.set(5, point(500, 100));
                case "speed" -> points.set(5, point(500, 101));
                case "accuracy" -> points.set(5, new RoutePoint(START.plusSeconds(125), 0.0,
                        point(500, 125).longitude(), null, 36.0, null));
                case "coordinate" -> points.set(5, new RoutePoint(START.plusSeconds(125), Double.NaN,
                        previous.longitude(), null, null, null));
            }
            assertThat(FastestKilometer.seconds(points)).as(failure).isEmpty();
        }
    }
}
