# Business rules

Read from `ATU_SRC/`. Nothing in that tree was changed. Sizes come from `ATU_SRC/QDDSSRC/SAMREF.PF` unless the physical file defines the column itself.

Notation: `packed(p,s)`, `zoned(p,s)`, `char(n)`. A DDS type letter of blank with decimal places is zoned (`4  2` is `zoned(4,2)`). A blank type with no decimals is character. `8  0` is an 8-digit number that holds `yyyymmdd`; it is not a date type. Native `L` is a date. Native `Z` is a timestamp.

`%dech(value : p : s)` half-adjusts to precision `p` scale `s`: the first dropped digit of 5 or more moves the result away from zero. Plain `eval` and free-form assignment truncate extra decimal places toward zero to the result scale. There is no `eval(h)` in these members.

`DLCODE` is `char(1)`. `ATU_SRC/QDDSSRC/SAMREF.PF:26-27` says `X` means deleted.

`QTRGSRC` contains `ORD700A`, `ORD700D`, and `ORD700U`. There is no `ORD701` member there. `ORD701` is the SQL trigger `ATU_SRC/QSQLSRC/ORD701.SQLTRG`.

## VAT

### VAT-01 — VAT amount

The VAT amount is `(net * rate) / 100`, held at scale 4, then half-adjusted to precision 9 scale 2. The rate is a percent. A missing or cached row is still used. Soft delete is not tested.

Legacy: `ATU_SRC/QRPGLESRC/VAT300.RPGLE:38-48`. Prototype `ATU_SRC/QPROTOSRC/VAT.RPGLEINC:22-24`. Rate column `ATU_SRC/QDDSSRC/SAMREF.PF:61-62`, referenced by `ATU_SRC/QDDSSRC/VATDEF.PF:7`.

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| `P_VATCODE` | input | `char(1)` | `VAT300.RPGLE:40` (`1`, no type letter). File column `VATCODE` `char(1)` `SAMREF.PF:58` |
| `Net` | input | `zoned(9,2)` | `VAT300.RPGLE:41` |
| `VATRATE` | input, read from `VATDEF` | `zoned(4,2)` | `SAMREF.PF:61` |
| `tot` | intermediate | `zoned(11,4)` | `VAT300.RPGLE:43` |
| return | output | `zoned(9,2)` | `VAT300.RPGLE:39` |

Rounding: `tot = (net * vatrate) / 100` truncates to `zoned(11,4)` on assignment (`VAT300.RPGLE:46`). The return is `%dech(tot : 9 : 2)` (`VAT300.RPGLE:47`).

Edge cases:

- The code is not checked with `ExistVATRate`. A row with `VATDEL = 'X'` is still charged.
- If the chain does not find a row, `clear *all` has set `VATRATE` to 0, so the amount is 0.00. See VAT-04.
- A negative net half-adjusts away from zero.

Worked example. `VATRATE = 19.65`, `Net = 10.00`. Product `196.5000`, divided by 100 is `1.9650`, which fits in `zoned(11,4)`. `%dech` to scale 2 sees a dropped digit of 5, so the result is `1.97`. The same net with rate `19.64` yields `1.9640` and result `1.96`. `Net = 0.01` and rate `50.00` yield `0.0050`, which half-adjusts to `0.01`. `Net = -10.00` and rate `19.65` yield `-1.97`.

### VAT-02 — A VAT code is usable only when the row exists and is not soft-deleted

`ExistVATRate` is true only when `VATDEF` is found and `VATDEL <> 'X'`.

Legacy: `ATU_SRC/QRPGLESRC/VAT300.RPGLE:51-58`. Column `ATU_SRC/QDDSSRC/VATDEF.PF:15` references `DLCODE`.

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| `P_VATCODE` | input | `char(1)` | `VAT300.RPGLE:54` |
| `VATDEL` | input | `char(1)` | `VATDEF.PF:15`, `SAMREF.PF:26` |
| return | output | indicator | `VAT300.RPGLE:53` |

Rounding: none.

Edge cases:

- Not found is false, including after `clear *all` (`VAT300.RPGLE:70-71`).
- Found with `VATDEL = 'X'` is false.
- Any other `VATDEL`, including blank, is true when the row is found.
- The cached buffer is used (VAT-04). A rate deleted after the last successful read of that code still looks live.

Worked example. Code `'2'`, row found, `VATDEL = 'X'`, `VATRATE = 20.00`. `ExistVATRate('2')` is false. `ClcVAT('2', 10.00)` is still `2.00` (VAT-01). `GetVATRate('2')` is still `20.00` (VAT-03).

### VAT-03 — Rate and description ignore soft delete

`GetVATRate` returns the buffer's `VATRATE`. `GetVATDesc` returns `VATDESC`. Neither tests `%found` or `VATDEL`.

Legacy: `ATU_SRC/QRPGLESRC/VAT300.RPGLE:19-35`. Description length `ATU_SRC/QDDSSRC/VATDEF.PF:8`.

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| `P_VATCODE` | input | `char(1)` | `VAT300.RPGLE:21` and `:30` |
| `VATRATE` | output of `GetVATRate` | file `zoned(4,2)`, return `packed(4,2)` | `SAMREF.PF:61`, return `VAT300.RPGLE:20` |
| `VATDESC` | output of `GetVATDesc` | `char(20)` | `VATDEF.PF:8`, return `VAT300.RPGLE:29` |

Rounding: none. The zoned rate moves to a packed return of the same precision and scale.

Edge cases: not found returns `0.00` and a blank description, because the buffer was cleared. A soft-deleted row returns its rate and description.

Worked example. Code `'1'` found, `VATRATE = 5.50`, `VATDEL = ' '`. `GetVATRate` returns packed `5.50`. The same call after the row is marked `VATDEL = 'X'`, if the cache misses and the chain runs, still returns `5.50`.

### VAT-04 — VATDEF keeps the last record whose code matched

`chainVATDEF` opens `VATDEF` on first use. It chains only when `P_VATCODE <> VATCODE`. Otherwise it returns the buffer unchanged. Before a chain it clears the whole record.

Legacy: `ATU_SRC/QRPGLESRC/VAT300.RPGLE:61-74`.

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| `P_VATCODE` | input | `char(1)` | `VAT300.RPGLE:63` |
| `VATCODE` | buffer | `char(1)` | `SAMREF.PF:58` |
| `K_VATCODE` | key | `char(1)`, `LIKE(VATCODE)` | `VAT300.RPGLE:13` |

Rounding: none.

Edge cases:

- A successful read is cached. Later calls with the same code do not see updates or a new `VATDEL`.
- A miss is not cached. `clear *all` sets `VATCODE` to blank before the failed chain, so the next call with that code chains again.
- `closeVATDEF` (`VAT300.RPGLE:76-83`) drops the open file and the cache.

Worked example. `GetVATRate('1')` chains and loads `VATRATE = 5.50`, so the buffer code is `'1'`. The file is then updated to `6.00` and `VATDEL = 'X'`. `GetVATRate('1')` and `ExistVATRate('1')` do not chain. The rate is still `5.50` and existence is still true.

A call for missing code `'9'` clears the buffer, the chain fails, and `VATCODE` stays blank. The next `GetVATRate('9')` chains again.

## Reorder proposal

Article quantities are `zoned(5,0)` via `REFFLD(QUANTITY)`: `ARSTOCK`, `ARMINQTY`, `ARCUSQTY`, `ARPURQTY` (`ATU_SRC/QDDSSRC/ARTICLE.PF:17-31`, `ATU_SRC/QDDSSRC/SAMREF.PF:47`). `APPRICE` is `packed(7,2)` (`ARTIPROV.PF:9`, `SAMREF.PF:53`). `ARID` is `char(6)`. `APPRID` / `PRID` are `packed(5,0)`. `APREF` is `char(10)` (`ARTIPROV.PF:11`). `APDEL` and `ARDEL` are `char(1)`. Neither program tests them.

The two expressions do not describe the same shortage.

- Selection keeps a row when `ARSTOCK < ARMINQTY - ARCUSQTY + ARPURQTY`, which is `ARMINQTY - ARCUSQTY + ARPURQTY - ARSTOCK > 0`.
- The proposed quantity is `ARMINQTY + ARCUSQTY - ARPURQTY - ARSTOCK`.

They are equal only when `ARCUSQTY = ARPURQTY`. Otherwise the proposed quantity is larger than the selection gap by `2 * (ARCUSQTY - ARPURQTY)`.

### ROP-01 — PRO202 selects provider lines below a rearranged minimum

For one provider, a row is proposed when stock is strictly below `minimum - customer orders + purchase orders`, and the article id matches that provider link.

Legacy: `ATU_SRC/QRPGLESRC/PRO202.SQLRPGLE:85-92`.

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| `id` / `APPRID` | input | `packed(5,0)` | program parm `like(PRID)` `PRO202.SQLRPGLE:15`; column `ARTIPROV.PF:8` |
| `ARSTOCK`, `ARMINQTY`, `ARCUSQTY`, `ARPURQTY` | input | `zoned(5,0)` | `ARTICLE.PF:17-31` |
| `ARID`, `APARID` | join | `char(6)` | `SAMREF.PF:11`, `ARTIPROV.PF:7` |
| cursor row | output | the selected columns | `PRO202.SQLRPGLE:86-87` |

Rounding: none. The comparison is on scale 0.

Edge cases: `ARDEL` and `APDEL` are not filtered. `ARCUSQTY > 0` is not required. Equal stock and threshold is excluded (`<`, not `<=`).

Worked example. `ARMINQTY = 10`, `ARCUSQTY = 4`, `ARPURQTY = 1`, `ARSTOCK = 6`, and the provider matches. Threshold is `10 - 4 + 1 = 7`. `6 < 7`, so the row is selected. `ARSTOCK = 7` is not selected.

### ROP-02 — PRO202 proposed quantity

For each selected row, `NEWORD = ARMINQTY + ARCUSQTY - ARPURQTY - ARSTOCK`.

Legacy: `ATU_SRC/QRPGLESRC/PRO202.SQLRPGLE:104`. Display field `ATU_SRC/QDDSSRC/PRO202D.DSPF:26` is `NEWORD 5Y 0`.

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| four quantity fields | input | `zoned(5,0)` | `ARTICLE.PF:17-31` |
| `NEWORD` | output | DDS `5Y 0` (5 digits, 0 decimals) | `PRO202D.DSPF:26` |

Rounding: none. All four inputs are scale 0. Assignment truncates to 5 digits. No `%dech`.

Edge cases: the result can be negative while ROP-01 still selects the row. A positive result can belong to a row ROP-01 rejects.

Worked examples, using the same fields:

- Selected, and the quantity is not the selection gap. `10, 4, 1, 6` is selected (ROP-01). The gap in the `WHERE` is `7 - 6 = 1`. `NEWORD = 10 + 4 - 1 - 6 = 7`.
- Selected, negative quantity. `ARMINQTY = 10`, `ARCUSQTY = 0`, `ARPURQTY = 8`, `ARSTOCK = 12`. Threshold is `10 - 0 + 8 = 18`. `12 < 18`, so it is selected. `NEWORD = 10 + 0 - 8 - 12 = -10`.
- Not selected, positive quantity. `10, 9, 0, 8`. Threshold is `10 - 9 + 0 = 1`. `8 < 1` is false. The formula would have been `10 + 9 - 0 - 8 = 11`, and the row is not shown.

### ROP-03 — PRO203 selects the same threshold only when customer orders are positive

PRO203 uses the same stock test and also requires `ARCUSQTY > 0`. It joins every provider, not one id.

Legacy: `ATU_SRC/QRPGLESRC/PRO203.PGM.SQLRPGLE:59-67`.

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| quantity fields | input | `zoned(5,0)` on the file; host DS is `packed(5,0)` | file `ARTICLE.PF:17-31`; DS `PRO203.PGM.SQLRPGLE:13-16` |
| `PRID` | output | `packed(5,0)` | `PRO203.PGM.SQLRPGLE:17` |
| `TOT` | output, count of `ARTIPROV` rows for the article | `zoned(3,0)` | `PRO203.PGM.SQLRPGLE:21` and the scalar subquery at `:62` |

Rounding: none.

Edge cases: `ARDEL`, `APDEL`, and `PRDEL` are not filtered. The provider count includes every `ARTIPROV` row for the article. `TOT = 1` prints the article in the normal style; any other count uses the yellow style (`PRO203.PGM.SQLRPGLE:73-78`). That count is not part of the quantity.

Worked example. `ARMINQTY = 10`, `ARCUSQTY = 0`, `ARPURQTY = 0`, `ARSTOCK = 3`. PRO202 selects it (`3 < 10`) and proposes `7`. PRO203 rejects it because `ARCUSQTY > 0` is false.

`ARMINQTY = 20`, `ARCUSQTY = 5`, `ARPURQTY = 2`, `ARSTOCK = 10`. Threshold is `20 - 5 + 2 = 17`. `10 < 17` and `5 > 0`, so both programs select it.

### ROP-04 — PRO203 proposed quantity uses the same formula as PRO202

The spreadsheet cell is `ARMINQTY + ARCUSQTY - ARPURQTY - ARSTOCK`.

Legacy: `ATU_SRC/QRPGLESRC/PRO203.PGM.SQLRPGLE:84`.

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| four quantity fields | input | host `packed(5,0)` | `PRO203.PGM.SQLRPGLE:13-16` |
| cell | output | same integer expression, no named DDS field | `:84` |

Rounding: none.

Worked example. The row from ROP-03 that both programs select (`20, 5, 2, 10`) has `WHERE` gap `17 - 10 = 7` and proposed quantity `20 + 5 - 2 - 10 = 13`.

The purchase file name in both programs is built with `%dec(%date:*YMD)` (`PRO202.SQLRPGLE:149-150`, `PRO203.PGM.SQLRPGLE:30-31`). `*YMD` is `yymmdd`, not the `*ISO` `yyyymmdd` used for `ORDATE`. PRO202 also writes a fixed buyer block: name `SAMPLE Company`, address `55, rue Adrastee`, zip `F-74650`, city `Chavanod` (`PRO202.SQLRPGLE:154-158`).

## Stock reservation

`ODQTY` and `ODQTYLIV` are `zoned(5,0)` (`ATU_SRC/QDDSSRC/DETORD.PF:10-15`). `ODARID` is `char(6)`. `ARCUSQTY` is `zoned(5,0)`. `ODTOTVAT` is `packed(9,2)`. `CUCREDIT` is `zoned(9,2)` (`CUSTOMER.PF:19`). `CULASTORD` and `ORDATE` / `ORDATCLO` are `zoned(8,0)` (`CUSTOMER.PF:21`, `ORDER.PF:9-13`).

The three `QTRGSRC` members call program `ORD700` after insert, delete, and update of `DETORD`. The update trigger runs only when the row changes (`TRGUPDCND(*CHANGE)`, `ORD700U.SYSTRG:7`).

`UpdArt` adds the delta to `ARCUSQTY` and updates `ARTICLE`. A zero delta returns. A missing article returns without an update. `ARDEL` is not tested.

Legacy: `ATU_SRC/QRPGLESRC/ORD700.PGM.RPGLE:97-109`. Trigger bindings `ATU_SRC/QTRGSRC/ORD700A.SYSTRG:4-7`, `ORD700D.SYSTRG:4-7`, `ORD700U.SYSTRG:4-7`.

### STK-01 — Insert adds the ordered quantity, not the outstanding quantity

After insert, `ARCUSQTY += ODQTY`. `ODQTYLIV` is ignored.

Legacy: `ORD700.PGM.RPGLE:73-75`. Delta parameter `zoned(5,0)` at `:99`.

Worked example. `ARCUSQTY` is 0. The new line has `ODQTY = 8`, `ODQTYLIV = 2`. The trigger adds 8. `ARCUSQTY` becomes 8. ART801 would have used `8 - 2 = 6` (STK-06).

### STK-02 — Delete subtracts outstanding quantity

After delete, the delta is `-ODQTY + ODQTYLIV`.

Legacy: `ORD700.PGM.RPGLE:76-82`.

Worked example. The line had `ODQTY = 8`, `ODQTYLIV = 3`, and `ARCUSQTY` was 8 from STK-01's rule. Delta is `-8 + 3 = -5`. `ARCUSQTY` becomes 3.

### STK-03 — Update of the same article adjusts both order and delivery deltas

When `new.ODARID = old.ODARID`, the delta is `(new.ODQTY - old.ODQTY) - (new.ODQTYLIV - old.ODQTYLIV)`.

Legacy: `ORD700.PGM.RPGLE:86-89`.

Worked example. Old `(ODQTY, ODQTYLIV) = (8, 0)`, new `(8, 3)`, `ARCUSQTY = 8`. Delta is `(8 - 8) - (3 - 0) = -3`. `ARCUSQTY` becomes 5.

### STK-04 — Update that changes article moves the outstanding quantity

The old article gets `-old.ODQTY + old.ODQTYLIV`. The new article gets `new.ODQTY - new.ODQTYLIV`.

Legacy: `ORD700.PGM.RPGLE:90-92`.

Worked example. Old article `A`, `ODQTY = 8`, `ODQTYLIV = 0`, `ARCUSQTY = 8`. New article `B` starts at 0, with `ODQTY = 8`, `ODQTYLIV = 0`. `A` becomes 0. `B` becomes 8. If the new line already had `ODQTYLIV = 2`, `B` would become 6, which is not what STK-01 does on insert.

### STK-05 — ORD701 sets the customer's last order date on insert of the header

After insert of `ORDER`, `CULASTORD =` the new `ORDATE` for that `CUID`. Closed state, soft delete, and older orders are not considered.

Legacy: `ATU_SRC/QSQLSRC/ORD701.SQLTRG:5-15`.

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| `N.ORDATE` | input | `zoned(8,0)`, `yyyymmdd` | `ORDER.PF:9` |
| `N.ORCUID` | input | `packed(5,0)` | `ORDER.PF:8`, `SAMREF.PF:15` |
| `CULASTORD` | output | `zoned(8,0)` | `CUSTOMER.PF:21` |

Rounding: none.

Worked example. New order `ORDATE = 20261104`, `ORCUID = 15`. `CULASTORD` for customer 15 becomes `20261104` even if that customer already had `CULASTORD = 20261201`.

### STK-06 — ART801 replaces customer-order quantity from open lines only

`ARCUSQTY = SUM(ODQTY - ODQTYLIV)` for lines whose order has `ORDATCLO = 0` and the same `ARID`. The article is updated only when that open set is non-empty. The procedure sets `DECRESULT = (31, 31, 00)` (`ART801.SQLPRC:15`). That option is not the scale of `ARCUSQTY`. The sum is assigned to `zoned(5,0)`. There is no half-adjust in the statement.

Legacy: `ATU_SRC/QSQLSRC/ART801.SQLPRC:22-27`. Match is `ORID = ODORID` only. `ODYEAR` is not compared.

Worked example. One open line, `ODQTY = 8`, `ODQTYLIV = 2`, `ORDATCLO = 0`. The sum is 6, so `ARCUSQTY` is set to 6. A second line on a closed order (`ORDATCLO = 20261104`, quantity 4) is not in the sum.

If that open order is then closed, the `EXISTS` predicate is false and this statement does not set `ARCUSQTY` to 0. The previous value remains.

### STK-07 — ART801 replaces credit with the open-order VAT total

`CUCREDIT = SUM(ODTOTVAT)` for lines of orders with `ORDATCLO = 0` and the same customer. Same `EXISTS` guard as STK-06. `DECRESULT = (31, 31, 00)` is set and is not the scale of `CUCREDIT` `zoned(9,2)`.

Legacy: `ATU_SRC/QSQLSRC/ART801.SQLPRC:28-33`.

Worked example. Open lines `ODTOTVAT` `10.00` and `2.50`, plus a closed order line `99.00`. `CUCREDIT` becomes `12.50`. No trigger in `ORD700` or `ORD701` updates `CUCREDIT`.

### STK-08 — ART801 sets last order date from every order

`CULASTORD = MAX(ORDATE)` for the customer. `ORDATCLO` is not tested. The customer is updated only when at least one order exists.

Legacy: `ATU_SRC/QSQLSRC/ART801.SQLPRC:34-37`.

Worked example. Orders dated `20260115` (closed) and `20261104` (open). `CULASTORD` becomes `20261104`. A later insert of an order dated `20260101` would make ORD701 set `CULASTORD` to `20260101` (STK-05). ART801 would put it back to `20261104`.

## Order lifecycle

Screen options on `ORD200` are `2=Edit`, `4=Delete`, `5=Display`, `6=Print`, `7=Close`, `8=Deliver` (`ATU_SRC/QDDSSRC/ORD200D.DSPF:59-83`). `datBlank` is the date `1940-01-01` (`ORD200.PGM.SQLRPGLE:74`).

### ORD-01 — The order list shows ISO numbers as dates, and zero as 1940-01-01

The list reads `ORDERCUS` for the customer and converts `ORDATE`, `ORDATDEL`, and `ORDATCLO` with `ISOTODATE40`. Value `0` becomes `1940-01-01`. `*HIVAL` (`99999999` on this `zoned(8,0)`) becomes `2039-12-31`. Any other value must pass `test(de) *iso` or the result is null.

Legacy: cursor `ATU_SRC/QRPGLESRC/ORD200.PGM.SQLRPGLE:105-113`. Conversion `ATU_SRC/QRPGLESRC/DAT002.PGM.RPGLE:45-55`. View `ATU_SRC/QSQLSRC/ORDERCUS.VIEW:15-21`. Display dates are native `L` with `DATFMT(*JOB)` (`ORD200D.DSPF:23-26`).

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| `ORCUID` | input | `packed(5,0)` | `ORDER.PF:8` |
| `ORDATE`, `ORDATDEL`, `ORDATCLO` | input | `zoned(8,0)` | `ORDER.PF:9-13` |
| `DATORD`, `DATLIV`, `DATCLO` | output | date `L`, `DATFMT(*JOB)` | `ORD200D.DSPF:23-27` |
| `TOTVAL` / `SUMORD` | output | sum of `ODTOTVAT` `packed(9,2)`, else 0; screen `11Y 2` | `ORDERCUS.VIEW:17-19`, `ORD200D.DSPF:28` |

Rounding: the view sums `packed(9,2)` values. It does not apply `%dech`. The screen field has 11 digits and scale 2.

Edge cases: the view joins on `ORCUID = CUID` and does not test `CUDEL`. The sum matches `ORID` only, not `ODYEAR`, and includes lines of a closed order. `0` and a stored close date of `1940-01-01` both become the date `1940-01-01`. `datclo > datBlank` is false for an order that was never closed. `DATLIV` and `DATCLO` map `1940-01-01` to blank on the screen (`ORD200D.DSPF:25` and `:27`). `DATORD` does not. The comparison uses the program date, so a blank delivery or close on the screen is still `1940-01-01` in the test. The screen format is the job date format. The file value stays `yyyymmdd`.

Worked example. `ORDATCLO = 0`. The screen close date is `1940-01-01`, which is not greater than `datBlank`, so the order is still treated as open by ORD-02. `ORDATCLO = 20261104` displays as 4 November 2026 and is greater than `datBlank`.

### ORD-02 — Which options are rejected

`AND` binds tighter than `OR`.

- Options `1`, `3`, and anything above `8` are invalid (`ORD200.PGM.SQLRPGLE:168`).
- Option `7` is rejected when `datclo > datBlank`. Option `8` is rejected when `datliv > datBlank` (`:177-178`).
- The edit/delete test is `opt01 = 2 or opt01 = 4 and datclo > datBlank` (`:187`), which is `opt01 = 2` or (`opt01 = 4` and the order is closed). Option `2` is rejected on every order. Option `4` is rejected only when closed.
- Option `4` is also rejected when any line of that `ORID` has `ODQTYLIV > 0` (`:196-211`).

Worked example. An open order, `ORDATCLO = 0`, so `datclo` is `1940-01-01`. Option `2` still fails `:187` and the subfile shows indicator 36, message "Closed order can not be edited or deleted" (`ORD200D.DSPF:41-42`). Option `7` is allowed. Option `8` is allowed when `ORDATDEL = 0`. A second close or a second delivery sets indicator 35, message "Invalid Option" (`ORD200D.DSPF:40`), not the closed-order message.

An open order with one line `ODQTYLIV = 1` rejects option `4` with indicator 37, message "Order whith deliveries can not be deleted" (`ORD200D.DSPF:43-44`). The line scan is `setll`/`reade` on `orid` against `DETORD1` (`ORD200.PGM.SQLRPGLE:197-198`). That file's key is `ODORID`, then `ODLINE` (`ATU_SRC/QDDSSRC/DETORD1.LF:7-8`). `ODYEAR` is not in the key.

### ORD-03 — Close stamps the close date and fills a missing delivery date

Option `7` chains the order. If `ORDATDEL = 0`, it sets `ORDATDEL` to `%dec(%date():*iso)` (`yyyymmdd`). It always sets `ORDATCLO` to that same number. Line quantities are not changed. `ORD700` does not run, because `DETORD` is not updated.

Legacy: `ORD200.PGM.SQLRPGLE:244-251`.

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| `ORDATDEL`, `ORDATCLO` | output | `zoned(8,0)` | `ORDER.PF:11-13` |
| today | input | `%dec(%date():*iso)`, 8 digits | `:247` and `:250` |

Worked example. On 4 November 2026, `ORDATDEL = 0`, `ORDATCLO = 0`, one line `ODQTY = 8`, `ODQTYLIV = 0`, `ARCUSQTY = 8`. After option `7`, `ORDATDEL` and `ORDATCLO` are `20261104`. `ODQTYLIV` stays 0. `ARCUSQTY` stays 8.

### ORD-04 — Deliver stamps the delivery date and fills only lines with no delivery yet

Option `8` sets `ORDATDEL` to today as `yyyymmdd`. It then reads `DETORD1` by `orid`. For each row, if `ODQTYLIV = 0` it sets `ODQTYLIV = ODQTY`. If `ODQTYLIV` is already non-zero, the line is unlocked and left as it is. The order is not closed.

Legacy: `ORD200.PGM.SQLRPGLE:255-270`. File `detord1` at `:8`. Key `ATU_SRC/QDDSSRC/DETORD1.LF:7-8` (`ODORID`, `ODLINE`). Each updated line fires STK-03.

Worked example. Two lines, both `ODQTY = 5`. Line 1 has `ODQTYLIV = 0`. Line 2 has `ODQTYLIV = 2`. After option `8`, line 1 has `ODQTYLIV = 5` and its `ARCUSQTY` delta is `(5 - 5) - (5 - 0) = -5`. Line 2 stays `ODQTYLIV = 2` and its `ARCUSQTY` does not change. `ORDATCLO` stays 0.

### ORD-05 — Delete removes the header and then the lines of that order number

Option `4` deletes `ORDER` by `ORID`, then deletes `DETORD1` rows by that `ORID` until a delete is not found. Each deleted line fires STK-02. `ORD701` does not run on delete. The detail key used here is the logical file's `ODORID` (`DETORD1.LF:7`), not `ODYEAR`.

Legacy: `ORD200.PGM.SQLRPGLE:229-233`. File `detord1` at `:8`.

Worked example. One open line `ODQTY = 8`, `ODQTYLIV = 0`, `ARCUSQTY = 8`. After a delete that gets past ORD-02, the header and the line are gone and `ARCUSQTY` becomes 0. `CULASTORD` is unchanged.

### ORD-06 — Line edit checks compare each new quantity with the other old quantity

Option `2` opens the line. The check rejects the screen when `DSQTYLIV > ODQTY` (new delivered against the file's ordered quantity) or `DSQTY < ODQTYLIV` (new ordered against the file's delivered quantity). The messages say the two quantities should be compared with each other. The code does not compare `DSQTYLIV` with `DSQTY`.

Legacy: `ATU_SRC/QRPGLESRC/ORD101.PGM.RPGLE:248-257`. `DSQTY` is `ORD101D.DSPF:119`. `DSQTYLIV` is `:137`. `ErrQtyLiv` is indicator 37 and `ErrQty` is indicator 38 (`ORD101.PGM.RPGLE:51-52`). Messages `ATU_SRC/QMSGFSRC/SAMMSGF.MSGF:21-26`: `ERR1001` on the delivered field (`ORD101D.DSPF:138`), `ERR1002` on the ordered field (`:120`).

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| `DSQTY`, `ODQTY` | new and old ordered | `zoned(5,0)` | `DETORD.PF:10`, screen `REFFLD` `ORD101D.DSPF:119` |
| `DSQTYLIV`, `ODQTYLIV` | new and old delivered | `zoned(5,0)` | `DETORD.PF:13`, `ORD101D.DSPF:137` |

Rounding: none.

Edge cases: both tests can fire. The net and VAT are still recalculated after the tests (`ORD101.PGM.RPGLE:258-260`). A changed screen sets indicator 27 (`ORD101D.DSPF:95`) and `S02act` does not write the line until a later enter with that indicator off (`ORD101.PGM.RPGLE:264-270`).

Worked example. File line `ODQTY = 10`, `ODQTYLIV = 4`. The user enters `DSQTY = 6`, `DSQTYLIV = 8`. `8 > 10` is false, and `6 < 4` is false, so both checks pass. The saved line can have delivered quantity 8 and ordered quantity 6.

The other way: file `ODQTY = 10`, `ODQTYLIV = 8`, user enters `DSQTY = 9` and `DSQTYLIV = 9`. `9 > 10` is false, but `9 < 8` is false too, so `DSQTY < ODQTYLIV` does not fire. `9 < 8` is the test, and 9 is not lower than 8. A cut of ordered quantity from 10 to 7 with delivered left at 8 fails, because `7 < 8`.

### ORD-07 — Line net is quantity times price, truncated; VAT is VAT-01

On the edit check, `ODTOT = DSQTY * DSPRICE`, then `VAT = ClcVAT(GetArtVatCode(ODARID), ODTOT)`, then `ODTOTVAT = ODTOT + VAT`. The same VAT call is made when the line is first shown, using the file `ODTOT` (`ORD101.PGM.RPGLE:224-226`).

`GetArtVatCode` returns `ARVATCD` and does not test `ARDEL` (`ATU_SRC/QRPGLESRC/ART300.RPGLE:83-90`).

| Name | Role | Type | Defined |
| --- | --- | --- | --- |
| `DSQTY` | input | `zoned(5,0)` | `DETORD.PF:10` |
| `DSPRICE` | input | `packed(7,2)` | `DETORD.PF:16`, `SAMREF.PF:53` |
| `ODTOT` | output | `packed(9,2)` | `DETORD.PF:17`, `SAMREF.PF:50` |
| `VAT` | output | `zoned(9,2)` from VAT-01, shown through `REFFLD(ODTOTVAT)` | `VAT300.RPGLE:39`, `ORD101D.DSPF:124` |
| `ODTOTVAT` | output | `packed(9,2)` | `DETORD.PF:18` |
| `ARVATCD` | input to VAT-01 | `char(1)` | `ARTICLE.PF:32`, `SAMREF.PF:58` |

Rounding: the product of `zoned(5,0)` and `packed(7,2)` has scale 2 and is assigned to `packed(9,2)` with no `%dech`. The VAT step is VAT-01 (half-adjust). The addition of net and VAT has scale 2 and is assigned to `packed(9,2)` with no `%dech`.

Worked example. `DSQTY = 10`, `DSPRICE = 10.00`, article VAT code `'1'`, `VATRATE = 19.65`. `ODTOT = 100.00` with no half-adjust. VAT-01 gives `19.65` exactly (`100.00 * 19.65 / 100 = 19.6500`, half-adjust stays `19.65`). `ODTOTVAT = 119.65`.

`DSQTY = 1`, `DSPRICE = 10.00`, rate `19.65`. `ODTOT = 10.00`. VAT-01 returns `1.97`, not `1.96`. `ODTOTVAT = 11.97`.

### ORD-08 — A delivered line cannot be deleted from the line screen

Option `4` is rejected when `ODQTYLIV > 0`. Otherwise the line is deleted, which fires STK-02, and the screen totals subtract `ODTOT` and `ODTOTVAT`.

Legacy: check `ORD101.PGM.RPGLE:164-171`. Delete `ORD101.PGM.RPGLE:188-198`, keyed `(id:odline)` on `DETORD1` (`:191`). That key is `ODORID`, `ODLINE` (`DETORD1.LF:7-8`). Message `ORD101D.DSPF:40-41`, "Line with delivery can not be deleted."

Worked example. `ODQTY = 5`, `ODQTYLIV = 1`, `ODTOT = 50.00`. Option `4` is rejected and the line stays. The same line with `ODQTYLIV = 0` is deleted. If `ARCUSQTY` was 5, STK-02 sets it to 0.

Option `6` is labeled `6=Deliver` (`ORD101D.DSPF:79`) and is accepted by the check (`ORD101.PGM.RPGLE:155`). `s01act` has no branch for option `6`.

## Suspected legacy bugs

These are questions. The behaviour above is what the source does.

1. Should PRO202 and PRO203 propose `ARMINQTY - ARCUSQTY + ARPURQTY - ARSTOCK`, which is the gap the `WHERE` measures, instead of `ARMINQTY + ARCUSQTY - ARPURQTY - ARSTOCK`? For `(minimum, customer orders, purchase orders, stock) = (10, 4, 1, 6)` the row is selected and `NEWORD` is 7, while the `WHERE` gap is 1. For `(10, 0, 8, 12)` the row is selected and `NEWORD` is `-10`.

2. Should PRO203 require `ARCUSQTY > 0` when PRO202 does not? `(10, 0, 0, 3)` is a proposal of 7 in PRO202 and is absent from PRO203.

3. On insert, should `ORD700` add `ODQTY - ODQTYLIV` rather than `ODQTY`? A new line `(8, 2)` raises `ARCUSQTY` by 8. ART801 would store 6 for that open line.

4. When an order is closed, should the outstanding quantity leave `ARCUSQTY`? Option `7` does not update `DETORD`, so `ORD700` does not run. ART801 also leaves `ARCUSQTY` unchanged when the article has no remaining open line, because of the `EXISTS` guard. If another open line remains, ART801 drops the closed order's quantity and the trigger does not. Which of those is the reservation rule?

5. Should `CULASTORD` move backward? ORD701 assigns the new order's `ORDATE` even when it is earlier than the current `CULASTORD`. ART801 uses `MAX(ORDATE)` and will not move backward.

6. Is `CUCREDIT` supposed to change only when `ART801` is run? No order trigger updates it. Closing an order does not reduce it unless some later call of ART801 still sees another open order for that customer.

7. Is option `2` on `ORD200` meant to be rejected for every order? The condition at `ORD200.PGM.SQLRPGLE:187` is true whenever `opt01 = 2`, and the message shown is the closed-order message. An open order cannot reach `Updord`.

8. Are the `ORD101` quantity checks comparing the intended fields? The messages describe ordered versus delivered. The code compares the new delivered quantity with the old ordered quantity, and the new ordered quantity with the old delivered quantity. From a line `(ODQTY, ODQTYLIV) = (10, 4)`, the entry `(6, 8)` passes and can be stored with more delivered than ordered.

9. Should option `6` on `ORD101` deliver the line? The screen says `6=Deliver`. The action subroutine does not handle option `6`. Delivery of the whole order is option `8` on `ORD200`.

10. Should stock, credit, and reorder selection ignore `ARDEL`, `APDEL`, and `VATDEL`? `ExistVATRate` honours `VATDEL`. `ClcVAT`, `GetVATRate`, `ORD700`, `ART801`, `PRO202`, and `PRO203` do not honour the article or VAT delete flag.

11. Which key identifies an order line? The physical file key is `ODLINE`, `ODORID`, `ODYEAR` (`DETORD.PF:21-23`). The programs open `DETORD1`, whose unique key is `ODORID`, `ODLINE` and has no year (`DETORD1.LF:4-8`). `ART801` and `ORDERCUS` also match `ORID` only. The same order number in two years cannot both appear on `DETORD1`. Is the year part of the line identity, or is the logical key the one to keep?

12. Is the purchase-order file name supposed to use `*YMD` (`yymmdd`) while `ORDATE` is stored as `*ISO` (`yyyymmdd`)? And is the buyer block hard-coded in `PRO202.SQLRPGLE:154-158` the identity that should be written on every purchase file?
