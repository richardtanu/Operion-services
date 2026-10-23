package com.example.operion.module.inventory.services;

import java.math.BigDecimal;

import com.example.operion.module.burnrate.dto.BurnRateResponse;
import com.example.operion.module.burnrate.enums.BurnRateSource;
import com.example.operion.module.inventory.enums.LowBecause;

/**
 * BE-27, decided 23 Oct 2026 (INV-02): the one definition of "low".
 *
 * <ul>
 * <li>With usage data (burn-rate source COMPUTED or MANUAL_RATE, a rate above
 *     0): low when days of cover {@code stock / rate} is below the store's
 *     target days.</li>
 * <li>Otherwise: low when {@code stock <= minimumStock}.</li>
 * </ul>
 * Out of stock ({@code stock <= 0}) is a status on top of this, not a third rule.
 */
final class LowStockRule {

    private LowStockRule() {
    }

    /** {@code burnRate} may be null (no burn-rate row): then only the minimum applies. */
    static LowBecause evaluate(int stock, Integer minimumStock, BurnRateResponse burnRate) {

        if (hasUsageData(burnRate)) {
            BigDecimal daysOfCover = BigDecimal.valueOf(stock).divide(burnRate.getBurnRate(), 4, java.math.RoundingMode.HALF_UP);
            return daysOfCover.compareTo(BigDecimal.valueOf(burnRate.getTargetDays())) < 0 ? LowBecause.RUNS_OUT_SOON : null;
        }

        return stock <= (minimumStock == null ? 0 : minimumStock) ? LowBecause.BELOW_MINIMUM : null;
    }

    static boolean hasUsageData(BurnRateResponse burnRate) {

        return burnRate != null
                && (burnRate.getBurnRateSource() == BurnRateSource.COMPUTED
                        || burnRate.getBurnRateSource() == BurnRateSource.MANUAL_RATE)
                && burnRate.getBurnRate() != null
                && burnRate.getBurnRate().signum() > 0
                && burnRate.getTargetDays() != null;
    }
}
