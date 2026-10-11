package com.fulfilment.application.monolith.warehouses.domain.models;

public class WarehouseException extends RuntimeException {

  public enum Reason { INVALID, CONFLICT, NOT_FOUND }

  private final Reason reason;

  public WarehouseException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason getReason() {
    return reason;
  }
}
