package org.eu.de.stuntmock.fakes;

/** Code under test that receives its collaborator, for proxy-handle tests. */
public class TaxCalculator {

    private final PriceService prices;

    public TaxCalculator(PriceService prices) {
        this.prices = prices;
    }

    public double taxFor(String region, double amount) {
        return amount * prices.rateFor(region);
    }
}
