package org.example;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.Data;

@Data
public class PageView {
    private String project;
    private String article;
    private String granularity;
    private String timestamp;
    private String access;
    private String agent;
    private long views;
    public LocalDate getDate() {
        return LocalDate.parse(
                timestamp.substring(0, 8),
                DateTimeFormatter.BASIC_ISO_DATE
        );
    }
}