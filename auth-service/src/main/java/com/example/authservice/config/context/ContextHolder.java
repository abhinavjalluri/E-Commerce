package com.example.authservice.config.context;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class ContextHolder {

  public static final String CORRELATION_ID = "correlationId";
  private static final ThreadLocal<ContextData> holder = new ThreadLocal<>();

  public void set(ContextData context) {
    holder.set(context);
  }

  public ContextData get() {
    return holder.get();
  }

  public void remove() {
    holder.remove();
  }

  public boolean isAuthenticated() {
    var customContext = get();

    var isUsernameEmptyOrNull = StringUtils.isEmpty(customContext.getUsername());

    return !isUsernameEmptyOrNull && customContext.getUserId() != null;
  }

  public String getCorrelationId() {
    return get().getCorrelationId();
  }

  public Long getUserId() {
    return get().getUserId();
  }

  public String getUsername() {
    return get().getUsername();
  }
}
