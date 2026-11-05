package com.atu.modern.vat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VAT-01 {@code ClcVAT}. {@code ATU_SRC/QRPGLESRC/VAT300.RPGLE:46-47}:
 * {@code tot = (net * vatrate) / 100} into zoned(11,4), then {@code %dech(tot : 9 : 2)}.
 * A missing code is rate 0 after {@code clear *all} ({@code VAT300.RPGLE:70-71}).
 * This procedure does not call {@code ExistVATRate}, so {@code VATDEL = 'X'} is charged.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VatAmountTest {

	private final MockMvc mockMvc;

	private final JdbcTemplate jdbc;

	private final VatService vatService;

	@Autowired
	VatAmountTest(MockMvc mockMvc, JdbcTemplate jdbc, VatService vatService) {
		this.mockMvc = mockMvc;
		this.jdbc = jdbc;
		this.vatService = vatService;
	}

	@ParameterizedTest
	@CsvFileSource(resources = "/golden/vat-amount.csv", numLinesToSkip = 1)
	@DisplayName("VAT300.RPGLE:46-47 ClcVAT half-adjusts and still charges VATDEL X")
	void amount(String code, String rate, String vatDel, BigDecimal base, BigDecimal expected) {
		loadRate(code, rate, vatDel);
		assertThat(this.vatService.amount(code, base)).isEqualByComparingTo(expected);
	}

	@Test
	@DisplayName("VAT300.RPGLE:68-72 a later update of the same code is visible on the next amount")
	void sameCodeReadsTheCurrentRate() {
		loadRate("1", "19.65", " ");
		assertThat(this.vatService.amount("1", new BigDecimal("10.00"))).isEqualByComparingTo("1.97");
		this.jdbc.update("UPDATE VATDEF SET VATRATE = ? WHERE VATCODE = ?", new BigDecimal("10.00"), "1");
		assertThat(this.vatService.amount("1", new BigDecimal("10.00"))).isEqualByComparingTo("1.00");
	}

	@Test
	@DisplayName("VAT300.RPGLE:46-47 ClcVAT half-adjusts and still charges VATDEL X")
	void amountOverHttp() throws Exception {
		loadRate("1", "19.65", " ");
		this.mockMvc.perform(get("/api/vat/1/amount").param("base", "10.00"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value("1.97"));
	}

	/**
	 * Empty {@code rate} means the code is not on VATDEF. {@code VATDEL} is stored as given,
	 * including {@code X}.
	 */
	private void loadRate(String code, String rate, String vatDel) {
		this.jdbc.update("DELETE FROM VATDEF WHERE VATCODE = ?", code);
		if (rate == null || rate.isBlank()) {
			return;
		}
		String deleteFlag = (vatDel == null || vatDel.isBlank()) ? " " : vatDel;
		this.jdbc.update(
				"INSERT INTO VATDEF (VATCODE, VATRATE, VATDEL) VALUES (?, ?, ?)",
				code,
				new BigDecimal(rate),
				deleteFlag);
	}

}
