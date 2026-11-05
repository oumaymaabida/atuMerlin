package com.atu.modern.vat;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class VatController {

	private final VatService vatService;

	public VatController(VatService vatService) {
		this.vatService = vatService;
	}

	@GetMapping("/api/vat/{code}/amount")
	public Map<String, String> amount(@PathVariable("code") String code, @RequestParam("base") String base) {
		if (code == null || code.length() != 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
		final BigDecimal net;
		try {
			net = new BigDecimal(base);
		} catch (NumberFormatException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
		return Map.of("amount", this.vatService.amount(code, net).toPlainString());
	}

}
