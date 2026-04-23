package com.ral6h.demo;

import java.net.http.HttpClient.Version;

import com.ral6h.hcap.annotation.Body;
import com.ral6h.hcap.annotation.Client;
import com.ral6h.hcap.annotation.Header;
import com.ral6h.hcap.annotation.Client.HttpScheme;
import com.ral6h.hcap.annotation.PathParam;
import com.ral6h.hcap.annotation.QueryParam;
import com.ral6h.hcap.annotation.Request;
import com.ral6h.hcap.annotation.Request.RequestMethod;
import com.ral6h.hcap.model.ClientResponse;

public class App {
  public static void main(String[] args) throws Exception {
    try (final var clientImpl = new DummyClientImpl()) {
      // System.out.println(clientImpl.testGet());
      // System.out.println(clientImpl.testPostWithHeaders("testBody", "valueh1", null));
      System.out.println(clientImpl.testPostWithParams("testBody", "id1", "id2", "gigio", "value"));
      // System.out.println(clientImpl.testPutWithQueryParams("username", null));
      // System.out.println(clientImpl.testPutWithQueryParams("username", 12));
      // System.out.println(clientImpl.testPutWithQueryParams(null, 12));
    }
  }
}

@Client(scheme = HttpScheme.HTTPS, host = "postman-echo.com", version = Version.HTTP_2)
interface DummyClient {
  @Request(endpoint = "/get")
  public ClientResponse testGet();

  @Request(endpoint = "/post", method = RequestMethod.POST)
  public ClientResponse testPostWithHeaders(@Body String pippo,
      @Header(name = "pippo") String pipppoHeader,
      @Header(name = "pippo2", required = false) String pipppoHeader2);

  @Request(endpoint = "/post/{testParam1}/fisso/{testParam2}", method = RequestMethod.POST)
  public ClientResponse testPostWithParams(
      @Body String pippo,
      @PathParam(name = "testParam1") String arg1,
      @PathParam(name = "testParam2") String arg2,
      @QueryParam(name = "name") String name,
      @QueryParam(required = false, name = "key") String key
  );

  @Request(endpoint = "/put", method = RequestMethod.PUT)
  ClientResponse testPutWithQueryParams(
    @QueryParam(name = "user", required = false) String user,
    @QueryParam(name = "age", required = false) Integer age
  );
}
