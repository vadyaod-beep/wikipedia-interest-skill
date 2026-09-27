# Wikipedia Interest Skill

## What it does

Analyzes public attention to a topic through Wikipedia article pageviews, as one input to B2C product research. It is not a measure of purchasing demand.

## Features

- Compare up to five language editions; resolve matching articles through Wikipedia langlinks.
- Aggregate Wikimedia daily pageviews into months and analyze growth and trend.
- Show a transparent heuristic reliability score.
- Write `analysis.json`, a monthly `chart.png`, and a one-page `report.pdf`.
- Provide an Agent Skill in [`SKILL.md`](SKILL.md) for agent-driven use and follow-up questions.

## Requirements

Java 21, Maven, and network access to Wikimedia APIs. Set `WIKIMEDIA_USER_AGENT` to an identifiable application name with contact details for deployed use.

## Build

```bash
mvn clean package
```

## Usage

Run from the project directory:

```bash
java -jar target/wikipedia-interest-skill.jar analyze \
  --article "Astronomy" \
  --languages uk,pl,cs \
  --start 20240101 \
  --end 20260901 \
  --output ./output
```

`--article` is a title on the source Wikipedia (`en` by default). Use `--source-language uk`, for example, for a Ukrainian source title. Dates are inclusive and use `YYYYMMDD`; Wikimedia pageview data begins on 2015-07-01. For Windows PowerShell, put the command on one line or use PowerShell's backtick for line continuation.

## Output

- `analysis.json`: exact metrics, resolved article titles, monthly observations, conclusions, and limitations.
- `chart.png`: monthly pageviews for complete months only.
- `report.pdf`: one-page summary with metrics, chart, brief data-driven conclusions, and limits. Its page is rasterized, so text is not selectable.

## Analysis methodology

Daily pageviews are summed into calendar months. `totalViews` includes all observed days in the selected period. A month is `complete` only when the selected period covers the entire calendar month and Wikimedia provided every day. Incomplete months are excluded from the monthly average, growth, trend, and chart; missing days are never filled with zero.

Growth compares averages of complete months: with at least six, the first three versus the last three; with two to five, the first `floor(n/2)` versus the remaining months. `growthPercent = (recentAverage - baselineAverage) / baselineAverage × 100`, rounded to one decimal. Growth is unavailable with fewer than two complete months or a zero baseline. `growthMethod` in the JSON identifies the rule used. A result above +5% is `growing`, below −5% is `declining`, otherwise `stable`.

Strong outliers are complete months above 2.5 times or below 0.4 times the median, detected only with at least four complete months. `reliabilityScore` is a 0–100 **heuristic reliability score**, not a statistical confidence interval or probability. It is the rounded product of `100 × coverage × history × outlierFactor × stabilityFactor`, where `coverage = observedDays / expectedDays`, `history = min(completeMonths / 6, 1)`, `outlierFactor = 1 - 0.5 × outlierMonths / completeMonths` (or 1 when there are no complete months), and `stabilityFactor = 1 / (1 + CV)`. `CV` is the population standard deviation of complete monthly totals divided by their mean; an all-zero or empty series uses `CV = 0`. A high score means the series is sufficiently complete and relatively stable, not that the conclusion is statistically certain.

## Limitations

- Wikipedia pageviews are an interest proxy, **not purchase intent**, market size, or a sales forecast.
- Language editions differ in audience size; raw counts are not population-normalized.
- External events can create spikes; even three-month averages can be affected by a very large event.
- Article choice and interlanguage linking can affect which topic is actually measured.
- Month lengths differ, and the growth calculation uses monthly totals rather than per-day rates.
- `reliabilityScore` is heuristic, not a confidence interval. A strong genuine trend may also lower the stability component.

## AI-assisted development

The initial Wikimedia API client, JSON parsing, and basic analysis were implemented manually. An AI coding assistant (Codex) helped accelerate the CLI, extended analytics, chart and PDF generation, tests, and subsequent improvements. AI-generated changes were checked through code review, unit tests, Maven builds, and live Wikimedia API smoke tests. A cheap-model end-to-end agent test has **not** yet been performed.

## Future improvements

- Local caching and parallel or batched requests for larger research tasks.
- Configurable decision criteria and seasonality detection.
- Stronger statistical trend methods and investigation of events or anomalies.
- Representing a topic with multiple related articles.
- Normalization or context for different language-edition audience sizes.
- Larger-scale comparisons.
