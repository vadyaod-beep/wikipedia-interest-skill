---
name: wikipedia-interest-skill
description: Analyze Wikimedia pageviews for one Wikipedia topic across languages, producing monthly trends, a chart, and a PDF. Use for evidence about public attention to a topic over time, not for purchase-demand forecasts.
---

# Wikipedia interest analysis

Use this skill when a user asks how interest in a Wikipedia topic changed over time or wants to compare its pageviews across language editions. It requires Java 21, Maven for building, and network access to Wikimedia APIs. Do not use it as a measure of sales, market size, conversion, or purchasing intent.

## Run

Build once with `mvn package` in this directory. Run:

```bash
java -jar target/wikipedia-interest-skill.jar analyze --article "Astronomy" --languages uk,pl,cs --start 20240101 --end 20260901 --output ./output
```

`--article` is the exact title on the source Wikipedia. The default `--source-language` is `en`; supply another code if the title is from another edition. `--languages` is a comma-separated list of 1–5 Wikipedia language codes. Dates use `YYYYMMDD`, are inclusive, and must be on or after 2015-07-01. `--output` is a writable directory. For Wikimedia API identification, set `WIKIMEDIA_USER_AGENT` to a descriptive application name with contact details when deploying this tool.

The CLI resolves interlanguage article links before fetching each language's daily pageviews. If an edition has no linked article or the API fails, report the failure and ask for a different source article or language set. Never substitute the source title in another language without verifying that it is the same page.

## Read the results

The output directory contains `analysis.json`, `chart.png`, and `report.pdf`. The JSON is authoritative for precise values; the chart and PDF are presentation artifacts. Each `languages[]` entry records its resolved `articleTitle` and `statistics`:

- `totalViews` sums observed daily views in the whole requested period.
- `monthlyViews[]` has `month`, `views`, `observedDays`, `expectedDays`, and `complete`. A null `views` means no day was observed; it does not mean zero views. A complete month contains every calendar day and lies entirely within the requested period.
- `averageMonthlyViews` averages complete months only. `growthPercent` compares early and recent complete-month averages: with at least six complete months, the first three versus the last three; with two to five, the first `floor(n/2)` versus the remaining months. It is `(recentAverage - baselineAverage) / baselineAverage × 100`. `growthMethod` records which rule was used, or why growth is unavailable. Growth is null with fewer than two complete months or a zero baseline.
- `trend` is `growing` above +5%, `declining` below -5%, `stable` between those thresholds, or `insufficient_data` when growth is unavailable.
- `reliabilityScore` is a 0–100 **heuristic reliability score**: round `100 × coverage × history × outlierFactor × stabilityFactor`. Here `coverage = observedDays/expectedDays`, `history = min(completeMonths/6, 1)`, `outlierFactor = 1 - 0.5 × outlierMonths/completeMonths` (or 1 with no complete months), and `stabilityFactor = 1/(1 + CV)`. `CV` is the population standard deviation of complete monthly totals divided by their mean, or 0 for an all-zero/empty series. A strong outlier is a complete month above 2.5 times or below 0.4 times the median (checked with at least four complete months). A high score indicates a complete, relatively stable series; it is not a statistical confidence interval or probability. Inspect `completeMonths`, `observedDays`, `expectedDays`, and `outlierMonths` too.

The chart compares only complete months. The report includes short conclusions calculated from the observations and states its limits. Cite the language, resolved title, dates, and exact JSON metric when answering; do not invent missing observations.
Growth compares raw monthly totals, so month length and one-off events can still affect the percentage, even with three-month windows.

## Follow-up requests

For a follow-up about the same article, languages, and period, read the existing `analysis.json` and answer from it. Rerun the CLI if the user changes the topic, source language, target languages, dates, or asks for fresher data. If a title is ambiguous, ask which Wikipedia article the user means. Explain that language editions have different audience sizes, so raw counts are not population-normalized. Wikipedia pageviews show attention to an article, not buyer demand.
