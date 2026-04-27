package backend.drawrace.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import backend.drawrace.domain.round.service.AiInferenceService;
import backend.drawrace.domain.round.service.KeywordGenerator;
import backend.drawrace.domain.round.service.MockAiInferenceService;
import backend.drawrace.domain.round.service.MockKeywordGenerator;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AppConfig {

    @Bean
    @ConditionalOnMissingBean(KeywordGenerator.class)
    public KeywordGenerator mockKeywordGenerator() {
        return new MockKeywordGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(AiInferenceService.class)
    public AiInferenceService mockAiInferenceService() {
        return new MockAiInferenceService();
    }
}
