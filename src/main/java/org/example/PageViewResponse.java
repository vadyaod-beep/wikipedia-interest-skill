package org.example;

import lombok.Data;

import java.util.List;

@Data
public class PageViewResponse {
    private List<PageView> items;
}