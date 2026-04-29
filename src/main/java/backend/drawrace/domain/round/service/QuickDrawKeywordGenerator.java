package backend.drawrace.domain.round.service;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.mode", havingValue = "quickdraw")
public class QuickDrawKeywordGenerator implements KeywordGenerator {

    @Override
    public String generateKeyword() {
        QuickDrawKeyword[] values = QuickDrawKeyword.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)].name();
    }
}
