package com.ral6h.hcap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value = RetentionPolicy.SOURCE)
@Target(value = {ElementType.TYPE})
public @interface Client {
  HttpScheme scheme() default HttpScheme.HTTP;

  String host();

  int port() default -1;

  String basePath() default "";

  long connectTimeout() default 30l;

  /**
   * should add support in the future, for now setting this to true will cause compilation to fail,
   * sry :(
   */
  boolean async() default false; // TODO: add support in the future

  enum HttpScheme {
    HTTP,
    HTTPS;

    public String toString() {
      return this.name().toLowerCase();
    }
  }
}
