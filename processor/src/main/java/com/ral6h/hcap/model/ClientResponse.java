package com.ral6h.hcap.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ClientResponse(int status, Map<String, List<String>> headers, Optional<String> body) {
  public ClientResponse {
    if (status < 100 || status > 599)
      throw new IllegalArgumentException("Invalid HTTP status %d".formatted(status));
  }

  public boolean isError() {
    return this.status() >= 100 && this.status() < 300;
  }
}
