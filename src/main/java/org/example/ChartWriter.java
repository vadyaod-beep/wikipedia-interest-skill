package org.example;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class ChartWriter {
    private static final Color[] COLORS = {
            new Color(25, 91, 170), new Color(213, 83, 48), new Color(29, 142, 120),
            new Color(129, 83, 177), new Color(189, 139, 22)
    };

    public void write(AnalysisResult result, Path output) throws IOException {
        int width = 1200;
        int height = 620;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(25, 39, 60));
            g.setFont(new Font("SansSerif", Font.BOLD, 29));
            g.drawString("Monthly Wikipedia pageviews", 70, 62);
            g.setFont(new Font("SansSerif", Font.PLAIN, 17));
            g.setColor(new Color(93, 105, 122));
            g.drawString("Complete months only · all access · all agents", 70, 92);

            TreeSet<String> monthSet = new TreeSet<>();
            long maximum = 0;
            List<Map<String, Long>> series = new ArrayList<>();
            for (AnalysisResult.LanguageResult language : result.languages()) {
                Map<String, Long> values = new HashMap<>();
                for (PageViewAnalyzer.MonthlyViews month : language.statistics().monthlyViews()) {
                    if (month.complete() && month.views() != null) {
                        monthSet.add(month.month());
                        values.put(month.month(), month.views());
                        maximum = Math.max(maximum, month.views());
                    }
                }
                series.add(values);
            }
            List<String> months = new ArrayList<>(monthSet);
            if (months.isEmpty()) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 25));
                g.drawString("No complete month is available for a comparable chart.", 70, 300);
                ImageIO.write(image, "png", output.toFile());
                return;
            }

            int left = 105;
            int top = 145;
            int right = 1140;
            int bottom = 495;
            double yMax = maximum == 0 ? 1 : maximum * 1.1;
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            for (int tick = 0; tick <= 4; tick++) {
                int y = bottom - (bottom - top) * tick / 4;
                g.setColor(new Color(224, 231, 239));
                g.drawLine(left, y, right, y);
                g.setColor(new Color(85, 98, 113));
                String label = compact(Math.round(yMax * tick / 4));
                g.drawString(label, left - 12 - g.getFontMetrics().stringWidth(label), y + 5);
            }

            int labelStep = Math.max(1, (int) Math.ceil(months.size() / 8.0));
            for (int i = 0; i < months.size(); i++) {
                if (i % labelStep != 0 && i != months.size() - 1) {
                    continue;
                }
                int x = xPosition(i, months.size(), left, right);
                String label = months.get(i);
                g.setColor(new Color(85, 98, 113));
                g.drawString(label, x - g.getFontMetrics().stringWidth(label) / 2, bottom + 27);
            }

            for (int s = 0; s < series.size(); s++) {
                Map<String, Long> values = series.get(s);
                Color color = COLORS[s % COLORS.length];
                g.setColor(color);
                g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int previousX = -1;
                int previousY = -1;
                int previousIndex = -2;
                for (int i = 0; i < months.size(); i++) {
                    Long value = values.get(months.get(i));
                    if (value == null) {
                        continue;
                    }
                    int x = xPosition(i, months.size(), left, right);
                    int y = bottom - (int) Math.round(value / yMax * (bottom - top));
                    if (previousIndex == i - 1) {
                        g.drawLine(previousX, previousY, x, y);
                    }
                    g.fillOval(x - 4, y - 4, 8, 8);
                    previousX = x;
                    previousY = y;
                    previousIndex = i;
                }
            }

            int legendX = 70;
            int legendY = 565;
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            for (int i = 0; i < result.languages().size(); i++) {
                g.setColor(COLORS[i % COLORS.length]);
                g.fillRoundRect(legendX, legendY - 13, 25, 15, 5, 5);
                g.setColor(new Color(35, 49, 65));
                String label = result.languages().get(i).language();
                g.drawString(label, legendX + 35, legendY);
                legendX += 35 + g.getFontMetrics().stringWidth(label) + 30;
            }
            ImageIO.write(image, "png", output.toFile());
        } finally {
            g.dispose();
        }
    }

    private int xPosition(int index, int size, int left, int right) {
        return size == 1 ? (left + right) / 2 : left + index * (right - left) / (size - 1);
    }

    private String compact(long value) {
        if (value >= 1_000_000) {
            return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 1_000) {
            return String.format(java.util.Locale.ROOT, "%.0fk", value / 1_000.0);
        }
        return Long.toString(value);
    }
}
