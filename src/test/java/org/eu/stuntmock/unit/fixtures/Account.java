package org.eu.stuntmock.unit.fixtures;

/** The class the fixtures declare. */
public class Account {
    long balance = 100;

    public long getBalance() {
        return balance;
    }

    public void book(long betrag) {
        balance += betrag;
    }
}
