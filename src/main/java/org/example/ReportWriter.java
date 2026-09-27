package org.example;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Renders one readable A4 page, including Unicode article titles, into a PDF. */
public class ReportWriter {
    private static final int WIDTH = 1240;
    private static final int HEIGHT = 1754;
    private static final Color INK = new Color(28, 41, 58);
    private static final Color BLUE = new Color(25, 91, 170);
    private static final Color MUTED = new Color(91, 104, 119);

    public void write(AnalysisResult result, Path chart, Path output) throws IOException {
        BufferedImage pageImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = pageImage.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(BLUE);
            g.fillRect(82, 74, 13, 89);
            g.setColor(INK);
            g.setFont(new Font("SansSerif", Font.BOLD, 48));
            g.drawString("Wikipedia interest", 118, 124);
            g.setFont(new Font("SansSerif", Font.PLAIN, 27));
            g.setColor(MUTED);
            g.drawString("Monthly pageview analysis", 119, 160);

            int y = 225;
            g.setColor(INK);
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            y = drawWrapped(g, "Topic: " + result.article(), 84, y, 1070, 37);
            g.setFont(new Font("SansSerif", Font.PLAIN, 24));
            g.setColor(MUTED);
            y = drawWrapped(g, "Languages: " + String.join(", ", result.languages().stream()
                    .map(AnalysisResult.LanguageResult::language).toList())
                    + "   |   Period: " + result.startDate() + " to " + result.endDate(), 84, y + 8, 1070, 34);
            y += 45;

            g.setColor(INK);
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            g.drawString("Key metrics", 84, y);
            y += 46;
            g.setColor(new Color(237, 243, 250));
            g.fillRoundRect(80, y - 31, 1080, 45, 12, 12);
            g.setColor(INK);
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.drawString("WIKI", 101, y);
            g.drawString("TOTAL", 210, y);
            g.drawString("AVG / MONTH", 430, y);
            g.drawString("GROWTH", 690, y);
            g.drawString("TREND", 850, y);
            g.drawString("RELIABILITY", 1030, y);
            y += 46;
            for (AnalysisResult.LanguageResult language : result.languages()) {
                PageViewAnalyzer.Statistics stats = language.statistics();
                g.setColor(INK);
                g.setFont(new Font("SansSerif", Font.BOLD, 21));
                g.drawString(language.language(), 101, y);
                g.setFont(new Font("SansSerif", Font.PLAIN, 20));
                g.drawString(String.format(Locale.ROOT, "%,d", stats.totalViews()), 210, y);
                g.drawString(stats.averageMonthlyViews() == null ? "n/a"
                        : String.format(Locale.ROOT, "%,.1f", stats.averageMonthlyViews()), 430, y);
                g.drawString(stats.growthPercent() == null ? "n/a"
                        : String.format(Locale.ROOT, "%+.1f%%", stats.growthPercent()), 690, y);
                g.drawString(stats.trend().equals("insufficient_data") ? "n/a" : stats.trend(), 850, y);
                g.drawString(stats.reliabilityScore() + "/100", 1030, y);
                g.setColor(new Color(227, 232, 238));
                g.drawLine(82, y + 16, 1158, y + 16);
                y += 56;
            }
            y += 18;
            BufferedImage chartImage = ImageIO.read(chart.toFile());
            if (chartImage == null) {
                throw new IOException("Chart PNG could not be read");
            }
            int chartHeight = 558;
            g.drawImage(chartImage, 80, y, 1080, chartHeight, null);
            y += chartHeight + 42;

            g.setColor(INK);
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            g.drawString("What the data says", 84, y);
            y += 39;
            g.setFont(new Font("SansSerif", Font.PLAIN, 21));
            for (String conclusion : result.conclusions().stream().limit(3).toList()) {
                g.setColor(BLUE);
                g.fillOval(88, y - 13, 8, 8);
                g.setColor(INK);
                y = drawWrapped(g, conclusion, 111, y, 1030, 27) + 12;
            }

            y += 13;
            g.setColor(INK);
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            g.drawString("Limits of this analysis", 84, y);
            y += 35;
            g.setFont(new Font("SansSerif", Font.PLAIN, 20));
            g.setColor(MUTED);
            for (String limitation : result.limitations()) {
                y = drawWrapped(g, "• " + limitation, 86, y, 1060, 26) + 6;
            }
            if (y > HEIGHT - 70) {
                throw new IOException("Report content exceeds one page; reduce language count or text");
            }
        } finally {
            g.dispose();
        }

        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(pdf, pageImage);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            }
            pdf.save(output.toFile());
        }
    }

    private int drawWrapped(Graphics2D g, String text, int x, int y, int width, int lineHeight) {
        FontMetrics metrics = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && metrics.stringWidth(candidate) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        for (String item : lines) {
            g.drawString(item, x, y);
            y += lineHeight;
        }
        return y;
    }
}
