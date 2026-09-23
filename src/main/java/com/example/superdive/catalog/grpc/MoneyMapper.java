package com.example.superdive.catalog.grpc;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.example.superdive.catalog.v1.Money;

/**
 * Converts between BigDecimal and the wire Money message.
 *
 * The whole point of Money existing is that the amount never passes through a
 * binary float. Every conversion here stays in BigDecimal/long arithmetic.
 */
public final class MoneyMapper {

	/** Billionths, matching Money.nanos. */
	private static final int NANO_SCALE = 9;

	private MoneyMapper() {
	}

	public static Money toProto(BigDecimal amount, String currencyCode) {
		if (amount == null) {
			return Money.newBuilder().setCurrencyCode(currencyCode).build();
		}

		BigDecimal scaled = amount.setScale(NANO_SCALE, RoundingMode.HALF_UP);

		// longValue() truncates toward zero, so units and the remainder always
		// carry the same sign -- which is exactly the invariant Money requires.
		long units = scaled.longValue();
		int nanos = scaled.subtract(BigDecimal.valueOf(units))
				.movePointRight(NANO_SCALE)
				.intValueExact();

		return Money.newBuilder()
				.setCurrencyCode(currencyCode)
				.setUnits(units)
				.setNanos(nanos)
				.build();
	}

	public static BigDecimal toBigDecimal(Money money) {
		if (money == null) {
			return BigDecimal.ZERO;
		}
		// BigDecimal.valueOf(nanos, 9) is an exact scaled construction, not a
		// division, so no rounding happens here.
		BigDecimal exact = BigDecimal.valueOf(money.getUnits())
				.add(BigDecimal.valueOf(money.getNanos(), NANO_SCALE));

		/*
		 * Normalise the scale. The exact construction above always has scale 9,
		 * so a price of 100.00 would come back as 100.000000000 -- which changes
		 * the JSON the frontend renders and, because BigDecimal.equals compares
		 * scale, silently breaks equality against the same amount. Currency is
		 * conventionally 2dp; keep more only when genuinely present.
		 */
		BigDecimal trimmed = exact.stripTrailingZeros();
		return trimmed.scale() < 2 ? trimmed.setScale(2) : trimmed;
	}

	public static Money multiply(Money unitPrice, int qty) {
		BigDecimal total = toBigDecimal(unitPrice).multiply(BigDecimal.valueOf(qty));
		return toProto(total, unitPrice.getCurrencyCode());
	}

	public static Money add(Money a, Money b) {
		BigDecimal sum = toBigDecimal(a).add(toBigDecimal(b));
		return toProto(sum, a.getCurrencyCode().isEmpty() ? b.getCurrencyCode() : a.getCurrencyCode());
	}
}
