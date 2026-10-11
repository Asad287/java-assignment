package com.fulfilment.application.monolith.common;

import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class ApiErrorMapper implements ExceptionMapper<Exception> {

  private static final Logger LOG = Logger.getLogger(ApiErrorMapper.class);

  public record ApiError(int code, String error) {}

  @Override
  public Response toResponse(Exception exception) {
    int status = 500;
    String message = "Request could not be completed";
    if (exception instanceof WarehouseException warehouseException) {
      status = switch (warehouseException.getReason()) {
        case INVALID -> 400;
        case CONFLICT -> 409;
        case NOT_FOUND -> 404;
      };
      message = exception.getMessage();
    } else if (exception instanceof WebApplicationException webException) {
      status = webException.getResponse().getStatus();
      message = status < 500 ? exception.getMessage() : message;
    } else if (isConstraintViolation(exception)) {
      status = 409;
      message = "Request conflicts with existing data";
    }
    if (status >= 500) {
      LOG.error("Failed to handle request", exception);
    }
    return Response.status(status).type(MediaType.APPLICATION_JSON)
        .entity(new ApiError(status, message)).build();
  }

  private boolean isConstraintViolation(Throwable exception) {
    while (exception != null) {
      if (exception instanceof org.hibernate.exception.ConstraintViolationException) {
        return true;
      }
      exception = exception.getCause();
    }
    return false;
  }
}
