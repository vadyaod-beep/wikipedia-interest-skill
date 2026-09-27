package org.example;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutputWriterTest {
    @TempDir
    Path temp;

    @Test
    void writesPngAndSinglePagePdfForUnicodeTopicAndFiveLanguages() throws Exception {
        var months = List.of(
                new PageViewAnalyzer.MonthlyViews("2024-01", 310L, 31, 31, true),
                new PageViewAnalyzer.MonthlyViews("2024-02", 580L, 29, 29, true),
                new PageViewAnalyzer.MonthlyViews("2024-03", 620L, 31, 31, true));
        var stats = new PageViewAnalyzer.Statistics(1510, 503.3, 93.5,
                "first_half_vs_second_half_complete_months", "growing",
                50, 3, 91, 91, 0, months);
        List<AnalysisResult.LanguageResult> languages = new ArrayList<>();
        for (String code : List.of("uk", "pl", "cs", "de", "en")) {
            languages.add(new AnalysisResult.LanguageResult(code, "Астрономія", stats));
        }
        AnalysisResult result = new AnalysisResult("Астрономія", "uk", "2024-01-01", "2024-03-31",
                "Wikimedia Pageviews API", languages,
                List.of("uk: 1,510 observed views; growing trend.", "pl: 1,510 observed views; growing trend.",
                        "cs: 1,510 observed views; growing trend.", "de: 1,510 observed views; growing trend.",
                        "en: 1,510 observed views; growing trend."),
                List.of("Pageviews indicate attention, not purchasing demand.",
                        "Cross-language audiences differ; partial months are excluded.",
                        "Reliability is a heuristic, not a confidence interval."));
        Path chart = temp.resolve("chart.png");
        Path pdf = temp.resolve("report.pdf");

        new ChartWriter().write(result, chart);
        new ReportWriter().write(result, chart, pdf);

        assertNotNull(ImageIO.read(chart.toFile()));
        assertTrue(Files.size(pdf) > 1_000);
        try (var document = Loader.loadPDF(pdf.toFile())) {
            assertEquals(1, document.getNumberOfPages());
        }
    }
}
