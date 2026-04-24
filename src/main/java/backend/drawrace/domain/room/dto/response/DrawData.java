package backend.drawrace.domain.room.dto.response;

import lombok.Builder;

@Builder
public record DrawData(
        double x,
        double y,
        String type, // START (눌렀을 때), MOVE (그릴 때), END (뗐을 때)
        String color,
        int penSize) {}
