package com.atu.modern.vat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("VATDEF")
public record VatDef(
        @Id @Column("VATCODE") String vatCode,
        @Column("VATRATE") BigDecimal vatRate,
        @Column("VATDESC") String vatDesc,
        @Column("VATCREA") LocalDate vatCrea,
        @Column("VATMOD") LocalDateTime vatMod,
        @Column("VATMODID") String vatModId,
        @Column("VATDEL") String dlCode) {
}
