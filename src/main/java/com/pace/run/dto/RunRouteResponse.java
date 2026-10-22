package com.pace.run.dto;

import com.pace.run.vo.RouteBounds;
import com.pace.run.vo.RoutePoint;
import java.util.List;

public record RunRouteResponse(String type, List<List<Double>> coordinates, RouteBounds bounds,
                               List<RoutePoint> points) {}
