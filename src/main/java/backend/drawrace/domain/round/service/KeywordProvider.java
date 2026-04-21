package backend.drawrace.domain.round.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// TODO : 나중에 AI가 추천하는 제시어로 변경 예정
@Component
public class KeywordProvider {

    private final List<String> keywords = List.of(
            "사과", "자동차", "고양이", "비행기", "의자"
    );

    public String getRandomKeyword() {
        int index = ThreadLocalRandom.current().nextInt(keywords.size());
        return keywords.get(index);
    }
}