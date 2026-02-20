package com.ral6h.hcap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value = RetentionPolicy.SOURCE)
@Target(value = {ElementType.METHOD})
public @interface Request {

  RequestMethod method() default RequestMethod.GET;

  String endpoint() default "";

  long readTimeout() default 30l;

  public enum RequestMethod {
    DELETE,
    GET,
    HEAD,
    POST,
    PUT
  }
}
