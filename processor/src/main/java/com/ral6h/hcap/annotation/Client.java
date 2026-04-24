package com.ral6h.hcap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.http.HttpClient.Version;

@Retention(value = RetentionPolicy.SOURCE)
@Target(value = { ElementType.TYPE })
public @interface Client {
  HttpScheme scheme() default HttpScheme.HTTP;

  Version version() default Version.HTTP_1_1;

  String host() default "";

  int port() default -1;

  String basePath() default "";

  long connectTimeout() default 30_000l;

  /**
   * if true, provide a constructor which accepts a {@link ClientConfig} configuration class
   * all the other properties should be derived from its accessor methods
   * if this is set tu true the other annotation shall be ignored
   */ 
   boolean classConfig() default false;

   /**
   * should add support in the future, for now setting this to true will cause
   * compilation to fail,
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
