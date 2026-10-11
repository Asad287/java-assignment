package com.fulfilment.application.monolith.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import jakarta.ws.rs.WebApplicationException;

class ApiErrorMapperTest {
  @Test
  void hidesInternalExceptionMessages() {
    var mapper = new ApiErrorMapper();
    var response = mapper.toResponse(new IllegalStateException("Database password: secret"));
    assertEquals(500, response.getStatus());
    var error = (ApiErrorMapper.ApiError) response.getEntity();
    assertEquals("Request could not be completed", error.error());
    var webError = mapper.toResponse(new WebApplicationException("Internal secret", 503));
    assertEquals(503, webError.getStatus());
    assertEquals("Request could not be completed", ((ApiErrorMapper.ApiError) webError.getEntity()).error());
  }
}
