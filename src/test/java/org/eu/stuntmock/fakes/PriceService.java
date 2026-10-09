package org.eu.stuntmock.fakes;

/** An interface nobody resolves: declaring it gives an instance-scoped proxy handle. */
public interface PriceService {

    double rateFor(String region);

    default String currency() {
        return "EUR";
    }
}
