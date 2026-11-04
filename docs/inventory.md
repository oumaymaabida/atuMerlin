# Program inventory

Static read of `ATU_SRC` (atuMerlin). **Lines** are total source lines, including the ARCAD metadata header. **Type** is the runtime role: screen (workstation), service (bound module or callable business program), trigger, print, or SQL.

A program is **high** risk when it mixes 5250 panels with embedded or dynamic SQL, mutates several files, implements trigger or batch rules, depends on a missing member, or is fixed-format RPG or COBOL. **Medium** is a single 5250 flow, a selector with dynamic SQL, print/PDF, or an IBM API. **Low** is a keyed getter or DDL with no UI.

Service programs (from `QILESRVSRC`, exports in `QSRVSRC` unless `EXPORT(*ALL)`):

| Service program | Modules | Notes |
| --- | --- | --- |
| `FARTICLE` | `ART300`, `ART301` | Binds `FFAMILLY`. Does not include `ART302`. `FARTICLE.BND` omits `GETARTINFO`. |
| `FCUSTOMER` | `CUS300`, `CUS301` | |
| `FPROVIDER` | `PRO300`, `PRO301` | |
| `FFAMILLY` | `FAM300`, `FAM301` | |
| `FCOUNTRY` | `COU300`, `COU301` | |
| `FVAT` | `VAT300` | |
| `FPARAMETER` | `PAR300` | `EXPORT(*ALL)`, no binder source |
| `LOG` | `LOG300` | `EXPORT(*ALL)`, no binder source |

`QBNDSRC/SAMPLE.BNDDIR` binds those eight plus `XML`, `XSS`, `ORDER`, and `TXT`, which have no source in this tree. Most screens compile with `bnddir('SAMPLE')`.

## Missing includes

No `/INCLUDE` directives. Every `/COPY ../QPROTOSRC/*.RPGLEINC` target exists. Two member-style copies do not:

| Source | Directive | Expected member | Status |
| --- | --- | --- | --- |
| `QRPGLESRC/PRO202.SQLRPGLE` | `/copy qprotosrc,xml` | `QPROTOSRC/XML` (no `XML.RPGLEINC`) | **Missing** |
| `QRPGLESRC/PRO203.PGM.SQLRPGLE` | `/copy qprotosrc,Xss` | `QPROTOSRC/Xss` (no `XSS.RPGLEINC`) | **Missing** |

`QCBLSRC/PRO201.CBL` uses `COPY DDS-… IN PROVIDE1` and `COPY DDS-… IN PRO201D`. Those are compiler DDS copies. `PROVIDER`/`PROVIDE1` and `PRO201D` are in `QDDSSRC`.

Called objects with no source here (not `/COPY`, same migration blockers):

| Referenced as | From | Gap |
| --- | --- | --- |
| Program `ORD701` | `QSQLSRC/ORD701.SQLTRG` (`Program Name ORD701`) | No `ORD701` member |
| Command `CRTORD` | `ORD100C` | `CRTORD.CMD` has parameters only; no `PGM()` |
| Command `CVTSPLPDF` | `ORD500C` | `CVTSPLPDF.CMD` has no `PGM()` |
| `*SRVPGM` `XML`, `XSS` | `SAMPLE.BNDDIR`, `PRO200.ILEPGM` (`XML`) | No source; matches the missing copies |
| `*SRVPGM` `ORDER`, `TXT` | `SAMPLE.BNDDIR` | No source |
| `QMQRY` `CUSQRY`, `ARTQRY` | `SAMMNU` options 12 and 13 | No query source |

`ART250` calls `GetArtInfo`, prototyped in `ARTICLE.RPGLEINC` and implemented in `ART302`, but `FARTICLE` is built from `ART300` and `ART301` only and does not export `GETARTINFO`.

## Recommended migration order

Move leaves first. Selectors (`*301`) are service programs that still open a display file, so replace the procedure with an API before rewriting the panel.

1. **Reference files and sequences.** `PARAMETER`, `VATDEF`, `COUNTRY`, `FAMILLY`, `CUSTOMER`, `PROVIDER`, `ARTICLE`, `ARTIPROV`, `ARTIINF`, `ORDER`, `DETORD`, `CUSSEQ`, data area `LASTORDNO`. `SAMREF` is the field-reference file for the physical files.
2. **Leaf services.** `PAR300` (`FPARAMETER`), `VAT300` (`FVAT`), `COU300`, `FAM300`, `CUS300`, `PRO300`, `ART300`, `LOG300`. No workstation. Pricing, names, and the order-line log all bind here.
3. **Date SQL.** `DAT002` / `ISOTODATE40` (`ISOTODATE4.SQLUDF`), then `DAT001` / `ISO_Num_To_Date`. `ORD200` and `ORD201` select through `ISOTODATE40`.
4. **Selectors.** `COU301`, `FAM301`, `CUS301`, `PRO301`, `ART301`. Callers are `SltCountry`, `SltArtFam`, `SltCustomer`, `SltProvider`, `SltArticle`.
5. **Article extension.** Add `ART302` to `FARTICLE` and export `GETARTINFO` before any screen that calls it (`ART250`).
6. **Master screens with no order create.** `PAR200`, `COU200`, `ART201`, `ART202`, `ART200`, `ART250`, `CUS250`, `PRO250`, `PRO201`.
7. **Recover `xml` and `Xss`, then provider purchase orders.** `PRO202` and `PRO203` do not compile until those copies (and the `XML` / `XSS` service programs) are restored. `PRO200` calls both `ART202` and `PRO202`.
8. **Order entry and inquiry.** `ORD100` (and the `ORD100C2` QTEMP wrapper), `ORD101`, `ORD202`, then print `ORD500` → `ORD500C` → `CVTSPLPDF` (needs a command processing program and `PATH` from `GetParm2`). Then list screens `ORD201` and `ORD200`. `ORD200` calls `ORD100C`, whose `CRTORD` command has no processing program in this tree.
9. **Customer maintenance last among screens.** `CUS200` calls `ORD200`, so the order list has to exist first.
10. **Triggers and recalculation.** `ORD700` plus `ORD700A` / `ORD700U` / `ORD700D` (stock on `ARTICLE1`, log on delete). Then `ORD701` once the missing program is found — its SQL body also writes `CUSTOMER.CULASTORD`, which `ART801` and `ORD901` already do. Then `ART801`, `ORD901`, `ORD900`. `LOG100` only creates the `SAMLOG` user space.

Country, family, log, and date are not separate product domains. They are in [Supporting programs](#supporting-programs) because the six domains call them.

## Article

| Program | Type | Lines | /COPY | Files | Programs and procedures called | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| `ART200` | screen | 386 | `FAMILLY` | `ARTICLE2` (read), `ARTICLE1` (update/add), `ART200D`; SQL `ARTIINF` | `ART201` via `providers()`; `getArtFamDesc`, `sltArtFam`, `existArtFam` | **High** — multi-panel maintenance, native I/O, and SQL on the long-text table |
| `ART201` | screen | 244 | `PROVIDER`, `ARTICLE` | `ARTIPRO1`, `ART201D` | `GetArtDesc`, `GetProName` | **Medium** — article/provider subfile, also the `ART201` entry `ART200` and `ART250` call |
| `ART202` | screen | 241 | `PROVIDER`, `ARTICLE` | `ARTIPROV`, `ART202D` | `GetProName`, `GetArtDesc` | **Medium** — same pattern keyed by provider; called from `PRO200` and `PRO250` |
| `ART250` | screen | 330 | `FAMILLY`, `ARTICLE`, `VAT` | `ART250D`, `ARTICLE1`, `ARTIPRO2` (declared, no I/O); SQL `ARTICLE` | `ART201`; `SltArticle`, `GetArtFamDesc`, `CLCVat`, `GetArtInfo` | **High** — SQL search, VAT, and `GetArtInfo` which `FARTICLE` does not export |
| `ART300` | service | 138 | `ARTICLE` | `ARTICLE1` (`usropn`) | Exports `GetArtDesc`, `GetArtRefSalPrice`, `GetArtStockPrice`, `GetArtFam`, `GetArtStock`, `GetArtMinStock`, `GetArtVatCode`, `ExistArt`, `IsArtDeleted` | **Low** |
| `ART301` | service | 237 | `ARTICLE`, `FAMILLY` | `ART301D`; dynamic SQL on `ARTICLE` | `sltArtFam`, `getArtFamDesc`; exports `sltarticle` | **High** — dynamic SQL built in the program, plus a subfile |
| `ART302` | service | 24 | `ARTICLE` | SQL `ARTIINF` | Exports `GetArtInfo`. Not in `FARTICLE` | **Medium** — small module, broken bind |
| `ART801` | SQL | 42 | — | `ARTICLE`, `"ORDER"`, `DETORD`, `CUSTOMER` | Procedure `UPDATE_ON_CUS_ORD_QTY` | **High** — rewrites open-order quantity, credit, and last-order date (`COMMIT *NONE`) |
| `ARTLSTDAT` | SQL | 23 | — | view over `ARTICLE`, `"ORDER"`, `DETORD` | — | **Medium** — cross-domain aggregate; menu query `ARTQRY` is a separate missing object |

`ARTIINF.TABLE` (25 lines) is the physical table behind `ART200` panel 3 and `ART302`. `ART200` uses SQL names `ARTICLE_INFORMATION` / `ARTICLE_INFO_ID`; `ART302` selects `artinf` / `arid`.

## Customer

| Program | Type | Lines | /COPY | Files | Programs and procedures called | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| `CUS200` | screen | 342 | `COUNTRY` | `CUSTOME2` (read), `CUSTOME1` (update/add), `CUS200D`; SQL `CUSTOMER`, sequence `CusSeq` | `ORD200` via `orders()`; `GetCountryName`, `SltCountry`, `ExistCountry` | **High** — maintenance, sequence, duplicate check, and a call into orders |
| `CUS250` | screen | 171 | `CUSTOMER`, `COUNTRY` | `CUS250D`, `CUSTOME1` | `SltCustomer`, `GetCountryName` | **Medium** |
| `CUS300` | service | 197 | `CUSTOMER` | `CUSTOME1` (`usropn`) | Exports `GetCusName`, `GetCusPhone`, `GetCusVat`, `GetCusMail`, address getters, `GetCusLimCredit`, `GetCusCredit`, `ExistCus`, `IsCusDeleted` | **Low** |
| `CUS301` | service | 232 | `CUSTOMER` | `CUS301D`; dynamic SQL on `CUSTOMER` | Exports `sltcustomer` | **Medium** — dynamic SQL selector |
| `CUSSEQ` | SQL | 9 | — | sequence `CUSSEQ` (`START WITH 1551`) | Used by `CUS200` | **Low** |

`CUSTADRE.PF` is not referenced by any program in this tree.

## Order

| Program | Type | Lines | /COPY | Files | Programs and procedures called | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| `ORD100` | screen | 337 | `CUSTOMER`, `ARTICLE`, `VAT` | `ORDER` (add), `DETORD` (add), `TMPDETORD`, `ORD100D`; data area `LASTORDNO` | `GetArtDesc`, `GetArtRefSalPrice`, `GetArtVatCode`, `CLCVat`, `GetVatRate`, `SltCustomer`, `GetCusName`, `sltArticle`. Prototype `ORD500` is never called | **High** — subfiles, locked order number, QTEMP detail file |
| `ORD100C` | service | 12 | — | `QTEMP/DETORD` via `CRTDUPOBJ`, override `TMPDETORD` | Command `CRTORD` | **High** — command has no processing program in the repo |
| `ORD100C2` | service | 11 | — | same QTEMP override | `ORD100` | **Medium** |
| `ORD101` | screen | 291 | `CUSTOMER`, `ARTICLE`, `VAT` | `ORDER1`, `DETORD1`, `ORD101D` | `GetArtDesc`, `GetArtVatCode`, `CLCVat`, `GetVatRate`, `GetCusName`. Prototype `ORD500` is never called | **High** — line edits and delivered-quantity checks |
| `ORD200` | screen | 289 | — | `ORDER1`, `DETORD1`, `CUSTOME1`, `ORD200D`; SQL view `ORDERCUS`, UDF `ISOTODATE40` | `ORD101`, `ORD202`, `ORD500`, `ORD100C` | **High** — list, close/deliver/delete, and create |
| `ORD201` | screen | 295 | — | `ORDER1`, `CUSTOME1`, `ARTICLE1`, `DETORD1`, `ORD201D`; SQL `ORDERCUS`, `ISOTODATE40` | `ORD100C2`, `ORD101`, `ORD202`, `ORD500` | **High** — all-customer list, same lifecycle actions |
| `ORD202` | screen | 156 | — | `CUSTOME1`, `ARTICLE1`, `DETORD1`, `ORDER1`, `ORD202D` | — | **Medium** — display only |
| `ORD500` | print | 60 | `PARAMETER` | `ORD500O` (printer), `ORDER1`, `DETORD1`, `CUSTOME1`, `ARTICLE1` | `ORD500C`; `getParm2('PATH')` | **Medium** — spool plus PDF hand-off |
| `ORD500C` | service | 14 | — | spool `ORD500O` | Command `CVTSPLPDF` | **Medium** — command source has no `PGM()` |
| `ORD700` | trigger | 110 | `LOG` | `ARTICLE1` (update `ARCUSQTY`); trigger buffer `EXTNAME(DETORD)` | `addlogEntry` on delete; local `UpdArt` | **High** — after-image stock math for insert, update, and delete |
| `ORD700A` | trigger | 7 | — | `ADDPFTRG` `DETORD` `*INSERT` → `ORD700` | `ORD700` | **Low** — registration only |
| `ORD700U` | trigger | 8 | — | `DETORD` `*UPDATE` `*CHANGE` → `ORD700` | `ORD700` | **Low** |
| `ORD700D` | trigger | 7 | — | `DETORD` `*DELETE` → `ORD700` | `ORD700` | **Low** |
| `ORD701` | trigger | 16 | — | after insert on `ORDER`; body updates `CUSTOMER.CULASTORD` | `Program Name ORD701` (**missing**) | **High** |
| `ORD900` | service | 11 | — | `ORDER1`; data area `LASTORDNO` | — | **Low** — sets the data area from max `ORID` |
| `ORD901` | service | 52 | — | `ORDER` (update); SQL `"ORDER"`, `DETORD`, `CUSTOMER` | — | **High** — shifts every order date and rewrites `CULASTORD` |
| `ORDERCUS` | SQL | 35 | — | view `"ORDER"`, `CUSTOMER`, `DETORD` | Read by `ORD200`, `ORD201` | **Medium** |
| `CRTORD` | command | 6 | — | parameter `CUID` only | Invoked by `ORD100C` | **High** — no command processing program |
| `CVTSPLPDF` | command | 96 | — | spool-to-PDF parameters | Invoked by `ORD500C` | **Medium** — no `PGM()` in the member |

`CRTORD` and `CVTSPLPDF` are command definitions, listed because programs call them. `LASTORDNO.DTAARA` is `*DEC` 6, default `60719`.

## Provider

| Program | Type | Lines | /COPY | Files | Programs and procedures called | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| `PRO200` | screen | 275 | `COUNTRY` | `PROVIDE1`, `PRO200D` | `ART202` via `articles()`; `PRO202` via `prpOrd()`; `GetCountryName`, `SltCountry`, `ExistCountry` | **High** — maintenance plus article list and purchase order. Comment at option 2: `mode` is not set on update |
| `PRO201` | screen | 316 | COBOL `COPY DDS` of `PROVIDE1`, `PRO201D` (DDS present) | `PROVIDE1`, `PRO201D` | `CALL "ART202"` | **Medium** — COBOL subfile, parallel to `PRO200` |
| `PRO202` | screen | 192 | **`qprotosrc,xml` missing**; `PARAMETER` | `PROVIDE1`, `PRO202D`; SQL `article`, `artiprov` | `getParm2`; `xmlopen`, `xmlStrTable`, `XmlStrRec`, `xmladdcol`, `xmlEndTable`, `xmlclose` | **High** — missing copy, XML service program, junction file |
| `PRO203` | screen | 99 | **`qprotosrc,Xss` missing**; `PARAMETER` | `PRO202D`; SQL `article`, `artiprov`, `provider` | `GetParm2`; `xssopenfile`, `XssSetDocProperties`, sheet/row/cell writers, `Xssclosefile` | **High** — missing copy and spreadsheet API |
| `PRO250` | screen | 181 | `PROVIDER`, `COUNTRY` | `PRO250D`, `PROVIDE1` | `ART202` via `items()`; `SltProvider`, `GetCountryName` | **Medium** |
| `PRO300` | service | 174 | `PROVIDER` | `PROVIDE1` (`usropn`) | Exports `GetProName`, `GetProCONT`, `GetProPHONE`, `GetProVAT`, `GetProMAIL`, address getters, `ExistProvider`, `IsProDeleted` | **Low** |
| `PRO301` | service | 232 | `PROVIDER` | `PRO301D`; dynamic SQL on `PROVIDER` | Exports `sltprovider` | **Medium** |

`PRO200.ILEPGM` binds modules `PRO200` and `PRO202` with `BNDSRVPGM(XML FCOUNTRY FPARAMETER)`.

## VAT

| Program | Type | Lines | /COPY | Files | Programs and procedures called | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| `VAT300` | service | 85 | `VAT` | `VATDEF` (`usropn`) | Exports `GetVATRate`, `GetVATDesc`, `CLCVat`, `ExistVATRate`, `CloseVATDEF` | **Low** — rate cache and amount calculation |

No VAT maintenance screen. Callers: `ORD100`, `ORD101`, `ART250` (`CLCVat`, `GetVatRate`). `FARTICLE` exposes `GETARTVATCODE`, which is the code those calls pass in.

## Parameters

| Program | Type | Lines | /COPY | Files | Programs and procedures called | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| `PAR200` | screen | 314 | — | `PARAMETER`, `PAR200D` | — | **Medium** — subfile maintenance of the parameter file |
| `PAR300` | service | 109 | `PARAMETER` | `PARAMETER` (`usropn`) | Exports `GetPARM1`–`GetPARM5`, `ClosePARAMETER` | **Low** |
| `PAR201` | service | 10 | — | — | `CALLPRC GETPARM2` (`PATH`), then `WRKLNK` | **Low** — CL over `FPARAMETER` (`PAR201.ILEPGM`) |

`GetParm2('PATH':' ')` is the IFS directory for `ORD500`, `PRO202`, and `PRO203`.

## Supporting programs

### Country

| Program | Type | Lines | /COPY | Files | Programs and procedures called | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| `COU200` | screen | 138 | — | `COU200D`, `COUNTRY` | — | **High** — only fully fixed-format RPG program (`QRPGSRC`) |
| `COU300` | service | 74 | `COUNTRY` | `COUNTRY` | Exports `GetCountryName`, `GetCountryIso3`, `ExistCountry` | **Low** |
| `COU301` | service | 254 | `COUNTRY` | `COUNTRY`, `COUNTR1`, `COU301D` | Exports `SltCountry` | **Medium** — selector UI inside the service program |

Callers: `CUS200`, `CUS250`, `PRO200`, `PRO250`.

### Family

| Program | Type | Lines | /COPY | Files | Programs and procedures called | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| `FAM300` | service | 74 | `FAMILLY` | `FAMILLY` | Exports `GetArtFamDesc`, `ExistArtFam`, `IsArtFamDeleted` | **Low** |
| `FAM301` | service | 252 | `FAMILLY` | `FAMILLY`, `FAMILL1`, `FAM301D` | Exports `SltArtFam` | **Medium** |

Callers: `ART200`, `ART250`, `ART301`. `FARTICLE` binds `FFAMILLY`.

### Log

| Program | Type | Lines | /COPY | Files | Programs and procedures called | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| `LOG100` | service | 27 | `APICALL` | `PARAMETER` (library from the file INFDS) | `QUSCRTUS`, `QUSPTRUS` (user space `SAMLOG`) | **Medium** — IBM user-space APIs |
| `LOG300` | service | 46 | `APICALL`, `LOG` | user space `SAMLOG` (no database file) | `QUSPTRUS`; exports `AddLogEntry` | **Medium** — pointer append, not a table |

Caller: `ORD700` on delete. `APICALL.RPGLEINC` also prototypes `QCMDEXC`; nothing in this tree calls it.

### Date

| Program | Type | Lines | /COPY | Files | Programs and procedures called | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| `DAT001` | SQL | 61 | — | — | External program for `ISO_Num_To_Date` | **Medium** — RPG UDF, sentinel handling |
| `DAT002` | SQL | 67 | — | — | External program for `ISOTODATE40` | **Medium** — `0` and high-value dates |
| `ISOTODATE` | SQL | 30 | — | — | `EXTERNAL NAME DAT001` | **Low** |
| `ISOTODATE4` | SQL | 14 | — | — | `EXTERNAL NAME DAT002` | **Low** |

## Menu entry points

`QPNLSRC/SAMMNU.MENU`: `ART200`, `CUS200`, `ORD201`, `PRO200`, `PRO201`, `ORD100C2`, `ART250`, `CUS250`, `PRO250`, `PRO203`, `PAR200`, `COU200`, `ORD900`, `ORD901`, `ART801`, `PAR201`. Options 12 and 13 run the missing queries `CUSQRY` and `ARTQRY`. Option 84 displays user space `SAMLOG`.
