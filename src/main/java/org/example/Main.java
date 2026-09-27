package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Main {
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Analysis failed: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] args) throws Exception {
        if (args.length == 0 || (args.length == 1 && "--help".equals(args[0]))) {
            printUsage();
            return;
        }
        if (!"analyze".equals(args[0])) {
            throw new IllegalArgumentException("Expected 'analyze' command. Run with --help for usage.");
        }
        Map<String, String> options = parseOptions(args);
        String article = required(options, "--article").trim();
        if (article.isEmpty()) {
            throw new IllegalArgumentException("--article must not be blank");
        }
        String sourceLanguage = options.getOrDefault("--source-language", "en").toLowerCase(Locale.ROOT);
        checkLanguage(sourceLanguage);
        Set<String> languages = new LinkedHashSet<>();
        for (String item : required(options, "--languages").split(",", -1)) {
            String language = item.trim().toLowerCase(Locale.ROOT);
            checkLanguage(language);
            if (!languages.add(language)) {
                throw new IllegalArgumentException("Duplicate language: " + language);
            }
        }
        if (languages.size() > 5) {
            throw new IllegalArgumentException("The one-page report supports at most five languages");
        }
        LocalDate start = parseDate(required(options, "--start"), "--start");
        LocalDate end = parseDate(required(options, "--end"), "--end");
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("--start must not be after --end");
        }
        if (start.isBefore(LocalDate.of(2015, 7, 1))) {
            throw new IllegalArgumentException("Wikimedia pageview data starts on 2015-07-01");
        }
        Path output = Path.of(required(options, "--output"));

        WikimediaClient client = new WikimediaClient();
        PageViewAnalyzer analyzer = new PageViewAnalyzer();
        Map<String, String> titles = client.resolveArticleTitles(sourceLanguage, article, languages);
        List<AnalysisResult.LanguageResult> results = new ArrayList<>();
        for (String language : languages) {
            String localizedTitle = titles.get(language);
            List<PageView> daily = client.getPageViews(language, localizedTitle,
                    start.format(API_DATE), end.format(API_DATE));
            results.add(new AnalysisResult.LanguageResult(language, localizedTitle,
                    analyzer.analyze(daily, start, end)));
        }

        List<String> conclusions = new ArrayList<>();
        for (AnalysisResult.LanguageResult language : results) {
            PageViewAnalyzer.Statistics stats = language.statistics();
            String summary = language.language() + " (" + language.articleTitle() + "): "
                    + String.format(Locale.ROOT, "%,d", stats.totalViews()) + " observed views";
            if (stats.growthPercent() == null) {
                summary += "; not enough comparable full months for a growth estimate.";
            } else {
                summary += "; " + stats.trend() + " trend ("
                        + String.format(Locale.ROOT, "%+.1f%%", stats.growthPercent())
                        + " in recent vs baseline complete-month averages).";
            }
            conclusions.add(summary);
        }
        List<String> limitations = List.of(
                "Wikipedia pageviews indicate attention, not purchasing demand or intent.",
                "Growth compares early and recent complete-month averages. Month length and language audience size differ; partial months are excluded.",
                "Reliability is a simple data-quality heuristic, not a statistical confidence interval."
        );
        AnalysisResult result = new AnalysisResult(article, sourceLanguage, start.toString(), end.toString(),
                "Wikimedia Pageviews API, daily, all-access, all-agents", List.copyOf(results),
                List.copyOf(conclusions), limitations);

        Files.createDirectories(output);
        Path json = output.resolve("analysis.json");
        Path chart = output.resolve("chart.png");
        Path report = output.resolve("report.pdf");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(json.toFile(), result);
        new ChartWriter().write(result, chart);
        new ReportWriter().write(result, chart, report);
        System.out.println("Created " + json.toAbsolutePath());
        System.out.println("Created " + chart.toAbsolutePath());
        System.out.println("Created " + report.toAbsolutePath());
    }

    private static Map<String, String> parseOptions(String[] args) {
        if ((args.length - 1) % 2 != 0) {
            throw new IllegalArgumentException("Each option needs a value. Run with --help for usage.");
        }
        Set<String> allowed = Set.of("--article", "--languages", "--source-language", "--start", "--end", "--output");
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            String key = args[i];
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown option: " + key);
            }
            if (options.putIfAbsent(key, args[i + 1]) != null) {
                throw new IllegalArgumentException("Duplicate option: " + key);
            }
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option: " + name);
        }
        return value;
    }

    private static LocalDate parseDate(String text, String name) {
        try {
            return LocalDate.parse(text, API_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be a valid YYYYMMDD date", e);
        }
    }

    private static void checkLanguage(String language) {
        if (!language.matches("[a-z][a-z0-9-]{0,19}") || language.endsWith("-") || language.contains("--")) {
            throw new IllegalArgumentException("Invalid Wikipedia language code: " + language);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar wikipedia-interest-skill.jar analyze "
                + "--article \"Astronomy\" --languages uk,pl,cs "
                + "--start 20240101 --end 20260901 --output ./output "
                + "[--source-language en]");
        System.out.println("The article title belongs to --source-language (default: en). "
                + "The date range is inclusive.");
    }
}
