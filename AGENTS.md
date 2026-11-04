# AGENTS.md

## 1. What this repo is

This is an IBM i (AS/400) order-management sample: articles, customers, providers, orders, VAT, and parameters. Legacy source is RPG III/IV, SQLRPGLE, COBOL, CL, and DDS under `ATU_SRC/`. It is being modernised to Java in `modern/`.

Do not try to compile or run the legacy code here. `iproj.json` builds with Elias (`elias compile {branch}`) into an IBM i library. That toolchain is not in this environment.

## 2. Hard rules

- `ATU_SRC/` is read-only reference. Never edit, move, or reformat anything under it.
- Every migrated rule needs a characterisation test that cites the legacy `file:line` it comes from.
- Preserve legacy behaviour by default. If you suspect a legacy bug, raise it as a question to the human with a worked example. Never fix it silently.
- Money and quantities use `BigDecimal`, never `double` or `float`.

## 3. Target stack

Java 21, Spring Boot 3, Spring Data JDBC, Flyway, H2, JUnit 5. Package root: `com.atu.modern`.

## 4. Build and test

```bash
cd modern && ./mvnw -q test
```

## 5. Layout

Put new Java only under `modern/`.

- `modern/src/main/java/com/atu/modern/` — application class.
- `modern/src/main/java/com/atu/modern/<domain>/` — one package per aggregate (article, customer, provider, order, vat, parameter).
- `*Controller.java` in that package — HTTP only. No business rules.
- `*Service.java` — migrated rules.
- `*Repository.java` — Spring Data JDBC. No rule logic.
- `modern/src/main/resources/application.yml` — H2 in-memory, Flyway on, H2 console on.
- `modern/src/main/resources/db/migration/` — Flyway scripts. One table per file: `V<n>__<table>.sql`. Column names match the legacy DDS names (`ARID`, not `ar_id`).
- `modern/src/test/java/com/atu/modern/` — JUnit 5. Mirror the main package.

## 6. Naming map

Only `SAMREF.PF` fields that ARTICLE, ARTIPROV, VATDEF, CUSTOMER, PROVIDER, ORDER, or DETORD actually reference. A blank DDS type with no decimals is character (`A`). A blank type with decimals is zoned (`S`). `P` is packed.

Identifiers (`5P 0`, `6P 0`, `4P 0`, `ODLINE`) are `Integer`. Prices, the VAT rate, and `QUANTITY` are `BigDecimal` (scale as in DDS). Do not map these to `double`.

Columns defined only on the physical file are not in this table. That includes native dates (`L`), timestamps (`Z`), `ORDATE` / `ORDATDEL` / `ORDATCLO` (`8 0`), `CULIMCRE` / `CUCREDIT` (`9 2`), `CULASTORD` (`8 0`), `VATDESC`, and `APREF`.

| SAMREF | DDS | Java | Java type | Used as |
| --- | --- | --- | --- | --- |
| ARID | 6A | arId | String | ARTICLE.ARID, ARTIPROV.APARID, DETORD.ODARID |
| ARDESC | 50A | arDesc | String | ARTICLE.ARDESC |
| FAID | 3A | faId | String | ARTICLE.ARTIFA |
| UNITPRICE | 7P 2 | unitPrice | BigDecimal | ARTICLE.ARSALEPR, ARTICLE.ARWHSPR, ARTIPROV.APPRICE, DETORD.ODPRICE |
| QUANTITY | 5S 0 | quantity | BigDecimal | ARTICLE.ARSTOCK, ARMINQTY, ARCUSQTY, ARPURQTY, DETORD.ODQTY, ODQTYLIV |
| VATCODE | 1A | vatCode | String | ARTICLE.ARVATCD, VATDEF.VATCODE |
| VATRATE | 4S 2 | vatRate | BigDecimal | VATDEF.VATRATE |
| DLCODE | 1A | dlCode | String | ARTICLE.ARDEL, ARTIPROV.APDEL, VATDEF.VATDEL, CUSTOMER.CUDEL, PROVIDER.PRDEL |
| CUID | 5P 0 | cuId | Integer | CUSTOMER.CUID, ORDER.ORCUID |
| CUSTNM | 30A | custNm | String | CUSTOMER.CUSTNM |
| PHONE | 15A | phone | String | CUSTOMER.CUPHONE, PROVIDER.PRPHONE |
| VATNUM | 12A | vatNum | String | CUSTOMER.CUVAT, PROVIDER.PRVAT |
| EMAIL | 50A | email | String | CUSTOMER.CUMAIL, PROVIDER.PRMAIL |
| ADRLINE | 50A | adrLine | String | CUSTOMER.CULINE1–3, PROVIDER.PRLINE1–3 |
| ZIPCOD | 10A | zipCod | String | CUSTOMER.CUZIP, PROVIDER.PRZIP |
| CITY | 30A | city | String | CUSTOMER.CUCITY, PROVIDER.PRCITY |
| COID | 2A | coId | String | CUSTOMER.CUCOUN, PROVIDER.PRCOUN |
| PRID | 5P 0 | prId | Integer | PROVIDER.PRID, ARTIPROV.APPRID |
| PROVNM | 30A | provNm | String | PROVIDER.PROVNM |
| ORID | 6P 0 | orId | Integer | ORDER.ORID, DETORD.ODORID |
| YEAR | 4P 0 | year | Integer | ORDER.ORYEAR, DETORD.ODYEAR |
| ODLINE | 5P 0 | odLine | Integer | DETORD.ODLINE |
| TOTPRICE | 9P 2 | totPrice | BigDecimal | DETORD.ODTOT, DETORD.ODTOTVAT |

`DLCODE` value `X` means soft-deleted (`SAMREF.PF`). Do not drop the column.
