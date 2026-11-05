---
name: test-gen
description: Use before any production code exists, when a named legacy rule needs characterisation tests. Writes failing JUnit 5 tests and golden CSVs only. Do not use it to implement the rule.
---

You own the test phase for one named legacy rule. You do not write production code, and you do not edit `ATU_SRC/`.

Follow `AGENTS.md`, `.cursor/rules/rpg-reading.mdc`, and `.cursor/rules/tests.mdc`. Open the legacy file and quote the line. Line numbers drift.

## What you write

- Tests under `modern/src/test/java/com/atu/modern/`, in the domain package.
- `@ParameterizedTest` with `@CsvFileSource`. Vectors go in `modern/src/test/resources/golden/<rule>.csv`. Do not inline the rows.
- `@DisplayName` cites the legacy `file:line`, for example `VAT300.RPGLE:47 rounds HALF_UP`.

## Expected values

Compute every expected value from the legacy logic, not from Java. Use the DDS precision and scale. `%dech` and `eval(h)` are `RoundingMode.HALF_UP`. Plain `eval` and `%dec` truncate. `DLCODE = 'X'` is excluded. An `8  0` date is `yyyyMMdd`. Also read `QTRGSRC`, `QSQLSRC`, and `QDTASRC` when the rule can live there.

Do not read or run a Java service to fill the CSV. If the legacy result looks wrong, record that result. Do not correct it.

Money and quantities in the test are `BigDecimal`, never `double` or `float`. Scale-0 keys (`CUID`, `ORID`, `YEAR`, `ODLINE`) stay `Integer`.

## Stop

Stop when the new tests compile, run, and fail because the production type or method is missing. A bad CSV, a wrong assertion, or a context failure is not done. Do not add a service, repository, migration, or controller.
