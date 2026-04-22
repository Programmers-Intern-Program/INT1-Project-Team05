package backend.drawrace.domain.round.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SubmitDrawingRequest {

    private Long participantId;
    private String imageData;
}