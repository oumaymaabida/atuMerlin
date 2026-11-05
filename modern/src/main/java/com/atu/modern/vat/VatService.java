package com.atu.modern.vat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

/**
 * {@code ClcVAT} at {@code ATU_SRC/QRPGLESRC/VAT300.RPGLE:46-47}.
 * A missing row is rate 0 after {@code clear *all} ({@code VAT300.RPGLE:70-71}).
 * {@code VATDEL} is not tested here, so a deleted rate is still charged.
 */
@Service
public class VatService {

	private final VatRepository repository;

	public VatService(VatRepository repository) {
		this.repository = repository;
	}

	public BigDecimal amount(String code, BigDecimal net) {
		BigDecimal rate = this.repository.findById(code)
			.map(VatDef::vatRate)
			.orElse(BigDecimal.ZERO);
		BigDecimal netScaled = net.setScale(2, RoundingMode.DOWN);
		BigDecimal rateScaled = rate.setScale(2, RoundingMode.DOWN);
		BigDecimal tot = netScaled.multiply(rateScaled)
			.divide(new BigDecimal("100"), 4, RoundingMode.DOWN);
		return tot.setScale(2, RoundingMode.HALF_UP);
	}

}
