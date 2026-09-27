package org.example;

import java.util.List;

public record AnalysisResult(
        String article,
        String sourceLanguage,
        String startDate,
        String endDate,
        String dataSource,
        List<LanguageResult> languages,
        List<String> conclusions,
        List<String> limitations
) {
    public record LanguageResult(
            String language,
            String articleTitle,
            PageViewAnalyzer.Statistics statistics
    ) {
    }
}
