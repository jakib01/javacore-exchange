package com.exchange.domain;

public interface OrderValidator {

    String validate(Order order, Account account);
}
