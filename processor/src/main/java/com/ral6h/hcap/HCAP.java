package com.ral6h.hcap;

import com.google.auto.service.AutoService;
import com.ral6h.hcap.annotation.Body;
import com.ral6h.hcap.annotation.Client;
import com.ral6h.hcap.annotation.Header;
import com.ral6h.hcap.annotation.PathParam;
import com.ral6h.hcap.annotation.QueryParam;
import com.ral6h.hcap.annotation.Request;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

@AutoService(value = {Processor.class})
@SupportedSourceVersion(value = SourceVersion.RELEASE_21)
@SupportedAnnotationTypes(value = {"com.ral6h.hcap.annotation.Client"})
public class HCAP extends AbstractProcessor {

  private final Predicate<? super ExecutableElement> DEFAULT_METHODS_VALIDATOR =
      el -> el.isDefault();

  private final Predicate<? super ExecutableElement> IS_ABSTRACT =
      el -> el.getModifiers().containsAll(Set.of(Modifier.PUBLIC, Modifier.ABSTRACT));

  private final Predicate<? super ExecutableElement> HAS_CORRECT_RETURN_TYPE =
      el ->
          super.processingEnv
              .getTypeUtils()
              .isSameType(
                  el.getReturnType(),
                  super.processingEnv
                      .getElementUtils()
                      .getTypeElement("com.ral6h.hcap.model.ClientResponse")
                      .asType());

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    final var filer = super.processingEnv.getFiler();
    final var messager = super.processingEnv.getMessager();

    for (final TypeElement annotation : annotations) {
      for (final Element elem : roundEnv.getElementsAnnotatedWith(annotation)) {
        if (elem.getKind() != ElementKind.INTERFACE)
          messager.printError("@Client can only be used on an interface", elem);

        final var typeElem = (TypeElement) elem;

        final var packageName =
            Optional.ofNullable(typeElem.getQualifiedName())
                .map(Object::toString)
                .map(fqn -> fqn.substring(0, fqn.lastIndexOf('.')))
                .orElseThrow(
                    () -> {
                      messager.printError("Could not extract package name for element", elem);
                      return new RuntimeException();
                    });

        final var fullyQualifiedClientName =
            "%s.%s".formatted(packageName, typeElem.getSimpleName());

        try {
          final JavaFileObject jfo = filer.createSourceFile(fullyQualifiedClientName + "Impl");
          generateClientImplementation(jfo, typeElem, packageName);
        } catch (IOException e) {
          messager.printError(
              """
              Exception while generating client:
                %s\
              """
                  .formatted(e.getMessage()),
              elem);
        }
      }
    }

    return true; // as per documentation:  If true is returned, the annotation interfaces are
    // claimed and subsequent processors will not be asked to process them; if false
    // is returned, the annotation interfaces are unclaimed and subsequent processors
    // may be asked to process them.
  }

  private void generateClientImplementation(
      JavaFileObject javaFileObject, TypeElement clientInterfaceElem, String packageName)
      throws IOException {
    final Client clientAnnotation = clientInterfaceElem.getAnnotation(Client.class);

    try (final var fpw =
        new PrintWriter(
            new OutputStreamWriter(javaFileObject.openOutputStream(), StandardCharsets.UTF_8))) {
      printPackage(fpw, packageName);
      printImports(fpw);
      printClassDeclaration(fpw, clientInterfaceElem);
      fpw.println("{");
      printConstructorAndCloseMethod(fpw, clientAnnotation, clientInterfaceElem);
      printImplementedMethods(fpw, clientInterfaceElem);
      fpw.print("}");
    }
  }

  private void printPackage(final PrintWriter fpw, String packageName) {
    fpw.println("package %s;".formatted(packageName));
    fpw.println();
  }

  private void printImports(PrintWriter fpw) {
    fpw.println(
        """
        import javax.annotation.processing.Generated;

        import java.time.Duration;

        import java.net.http.HttpClient;
        import java.net.http.HttpClient.Version;

        import java.util.concurrent.Executors;
        import java.util.concurrent.ExecutorService;

        import com.ral6h.hcap.model.ClientResponse;
        import java.io.IOException;
        import java.net.URI;
        import java.net.URISyntaxException;
        import java.net.http.HttpClient;
        import java.net.http.HttpClient.Version;
        import java.net.http.HttpHeaders;
        import java.net.http.HttpRequest;
        import java.net.http.HttpRequest.BodyPublishers;
        import java.net.http.HttpResponse.BodyHandlers;
        import java.time.Duration;
        import java.util.List;
        import java.util.ArrayList;
        import java.util.Map;
        import java.util.HashMap;
        import java.util.Optional;

        import java.util.stream.Stream;
        """);
  }

  private void printClassDeclaration(PrintWriter fpw, TypeElement typeElem) {
    fpw.print(
        """
        @Generated(value = "com.ral6h.hcap.ClientGenProcessor", date = "%s")
        public final class %s implements %s, AutoCloseable\
        """
            .formatted(
                ZonedDateTime.now().toString(),
                typeElem.getSimpleName().toString() + "Impl",
                typeElem.getSimpleName().toString()));
  }

  private void printConstructorAndCloseMethod(
      PrintWriter fpw, Client clientAnnotation, TypeElement elem) {

    final var scheme = clientAnnotation.scheme();
    final int port =
        Optional.ofNullable(clientAnnotation.port())
            .filter(p -> p != -1)
            .orElseGet(
                () ->
                    switch (scheme) {
                      case HTTP -> 80;
                      case HTTPS -> 443;
                    });

    fpw.println(
        """
          private final HttpClient client;
          private final ExecutorService executor = Executors.newWorkStealingPool();

          private final String scheme = "%s";
          private final String host = "%s";
          private final int port = %d;
          private final String basePath = "%s";

          public %s() {
            final var connectTimeout = %d;

            this.client = HttpClient.newBuilder()
                  .version(Version.HTTP_1_1)
                  .connectTimeout(Duration.ofSeconds(connectTimeout))
                  .executor(this.executor)
                  .build();
          }

          @Override
          public void close() {
            this.executor.close();  //is this necessary? maybe the close method in the http client already does it?? check the source code
            this.client.close();
          }
        """
            .formatted(
                scheme,
                clientAnnotation.host(),
                port,
                clientAnnotation.basePath(),
                elem.getSimpleName().toString() + "Impl",
                clientAnnotation.connectTimeout()));
  }

  private void printImplementedMethods(PrintWriter fpw, TypeElement clientAnnotationElem) {
    final var messager = super.processingEnv.getMessager();

    final var clientAnnotation = clientAnnotationElem.getAnnotation(Client.class);

    final var declaredMethods =
        clientAnnotationElem.getEnclosedElements().stream()
            .filter(elem -> elem.getKind() == ElementKind.METHOD)
            .map(elem -> (ExecutableElement) elem)
            .collect(Collectors.partitioningBy(elem -> elem.getAnnotation(Request.class) != null));

    final List<ExecutableElement> clientMethods = declaredMethods.get(true);
    final List<ExecutableElement> nonClientMethods = declaredMethods.get(false);

    validateInterfaceMethods(messager, clientMethods, nonClientMethods);

    clientMethods.forEach(elem -> writeMethodImplementation(fpw, elem, clientAnnotation));
  }

  private void validateInterfaceMethods(
      final Messager messager,
      final List<ExecutableElement> clientMethods,
      final List<ExecutableElement> nonClientMethods) {
    // =========================default methods validation=========================//

    nonClientMethods.stream()
        .filter(Predicate.not(DEFAULT_METHODS_VALIDATOR))
        .findAny()
        .ifPresent(
            invalid ->
                messager.printError(
                    "Only default methods can not be annotated with @Request in a @Client",
                    invalid));

    // =======================end default methods validation=======================//

    // =========================@Request methods validation=========================//

    clientMethods.stream()
        .filter(Predicate.not(IS_ABSTRACT))
        .findAny()
        .ifPresent(
            invalid ->
                messager.printError(
                    "Only abstract methods can be annotated with @Request in a @Client", invalid));

    clientMethods.stream()
        .filter(Predicate.not(HAS_CORRECT_RETURN_TYPE))
        .findAny()
        .ifPresent(
            invalid ->
                messager.printError(
                    "Methods annotated with @Request must have return type"
                        + " com.ral6h.hcap.model.ClientResponse",
                    invalid));

    // =======================end @Request methods validation=======================//
  }

  private void writeMethodImplementation(
      PrintWriter fpw, ExecutableElement clientMethod, Client clientAnnotation) {

    printMethodSignature(fpw, clientMethod);
    fpw.println("{");
    printMethodBody(fpw, clientMethod, clientAnnotation);
    fpw.println("}");
  }

  private void printMethodBody(
      PrintWriter fpw, ExecutableElement clientMethod, Client clientAnnotation) {

    final var requestAnnotation = clientMethod.getAnnotation(Request.class);

    var bodyPublisherStr = getBodyPublisherStr(clientMethod);

    String requestMethodStr =
        switch (requestAnnotation.method()) {
          case DELETE -> "DELETE()";
          case GET -> "GET()";
          case HEAD -> "HEAD()";
          case POST -> "POST(%s)".formatted(bodyPublisherStr);
          case PUT -> "PUT(%s)".formatted(bodyPublisherStr);
        };

    long readTimeout = requestAnnotation.readTimeout();

    String path = getPathResolver(clientMethod, clientAnnotation, requestAnnotation);

    QueryComponents queryComponents = getQueryComponents(clientMethod.getParameters());
    HeaderComponents headerComponents = getHeaderComponents(clientMethod.getParameters());

    fpw.print(
        """
            String path = %s;
            String query = %s;
            Map<String, Object> optionalQueryParams = new HashMap<>();

            //optional qp map population
            %s

            for (final var entry : optionalQueryParams.entrySet()) {
              if (entry.getValue() != null) {
                query += "&%%s=%%s".formatted(entry.getKey(), entry.getValue());
              }
            }

            List<String> headersList = new ArrayList<>();
            Map<String, Object> requiredHeadersMap = new HashMap<>();
            Map<String, Object> optionalHeadersMap = new HashMap<>();

            //required headers map population
            %s
            //optional headers map population
            %s

            for (final var requiredHeader : requiredHeadersMap.entrySet()) {
              headersList.add(requiredHeader.getKey());
              headersList.add(
                Optional.ofNullable(requiredHeader.getValue())
                  .map(Object::toString)
                  .orElseThrow(IllegalArgumentException::new)
              );
            }

            for (final var optionalHeader : optionalHeadersMap.entrySet()) {
              if (optionalHeader.getValue() != null) {
                headersList.add(optionalHeader.getKey());
                headersList.add(optionalHeader.getValue().toString());
              }
            }

            String[] headers = headersList.toArray(new String[] {});
            int readTimeout = %d;

            URI uri;
            try {
              uri = new URI(this.scheme, null, this.host, this.port, path, query, null);
            } catch (URISyntaxException e) {
              System.out.println("Invalid URI: " + e.toString());
              return new ClientResponse(500, Map.of(), Optional.empty());
            }

            final var requestBuilder =
                HttpRequest.newBuilder(uri)
                    .%s
                    .timeout(Duration.ofSeconds(readTimeout));

            if (headers.length > 0) requestBuilder.headers(headers);

            final var request = requestBuilder.build();

            try {
              System.out.println(request);
              final var httpResponse = client.send(request, BodyHandlers.ofString());

              return new ClientResponse(
                  httpResponse.statusCode(),
                  httpResponse.headers().map(),
                  Optional.ofNullable(httpResponse.body()));

            } catch (IOException e1) {
              return new ClientResponse(500, Map.of(), Optional.empty());
            } catch (InterruptedException e1) {
              Thread.currentThread().interrupt();
              return null;
            }
        """
            .formatted(
                path,
                queryComponents.requiredQpStr(),
                queryComponents.optionalQpMapPopulationStr(),
                headerComponents.requiredHeadersMapPopulationStr(),
                headerComponents.optionalHeadersMapPopulationStr(),
                readTimeout,
                requestMethodStr));
  }

  private String getBodyPublisherStr(ExecutableElement clientMethod) {

    final List<? extends VariableElement> bodyElements =
        clientMethod.getParameters().stream()
            .filter(param -> param.getAnnotation(Body.class) != null)
            .toList();

    return switch (bodyElements.size()) {
      case 0 -> "BodyPublishers.noBody()";
      case 1 -> {
        final var bodyElem = bodyElements.get(0);
        validateBodyParam(bodyElem);
        yield "BodyPublishers.ofString(%s)".formatted(bodyElem.getSimpleName().toString());
      }
      default -> {
        super.processingEnv
            .getMessager()
            .printError("At most 1 method parameter can be annotated with @Body", clientMethod);
        yield null;
      }
    };
  }

  private void validateBodyParam(VariableElement bodyElem) {
    Types typeUtils = super.processingEnv.getTypeUtils();
    Elements elementUtils = super.processingEnv.getElementUtils();

    final var bodyExpectedType = elementUtils.getTypeElement("java.lang.String").asType();

    if (!typeUtils.isSameType(bodyElem.asType(), bodyExpectedType)) {
      super.processingEnv
          .getMessager()
          .printError("Only String parameters can be annotated with @Body", bodyElem);
    }
  }

  private QueryComponents getQueryComponents(List<? extends VariableElement> parameters) {
    // TODO: complete
    final Function<VariableElement, String> queryNameExtractor =
        ve -> ve.getAnnotation(QueryParam.class).name();
    final Function<VariableElement, String> queryArgNameExtractor =
        ve -> ve.getSimpleName().toString();

    final Map<Boolean, Map<String, String>> queryParamToArgNameByRequired =
        parameters.stream()
            .filter(elem -> elem.getAnnotation(QueryParam.class) != null)
            .map(elem -> (VariableElement) elem)
            .collect(
                Collectors.partitioningBy(
                    ve -> ve.getAnnotation(QueryParam.class).required(),
                    Collectors.toMap(
                        queryNameExtractor,
                        queryArgNameExtractor))); // TODO: add support for multiple params with the
    // same name -> this should be done by checking that the type is List<String>

    final var queryParamStrJoiner =
        new StringJoiner(
            """
            +"&"+\
            """);

    for (var requiredParam : queryParamToArgNameByRequired.get(true).entrySet()) {
      final var paramName = requiredParam.getKey();
      final var paramArgName = requiredParam.getValue();

      queryParamStrJoiner.add(
          """
          "%s=%%s".formatted(Optional.ofNullable(%s).orElseThrow(IllegalArgumentException::new))\
          """
              .formatted(paramName, paramArgName));
    }

    final String required =
        queryParamStrJoiner.length() > 0 ? queryParamStrJoiner.toString() : "\"\"";

    final var optionalQpSb = new StringBuilder();
    for (var optionalParam : queryParamToArgNameByRequired.get(false).entrySet()) {
      final var paramName = optionalParam.getKey();
      final var paramArgName = optionalParam.getValue();

      optionalQpSb.append(
          """
          optionalQueryParams.put("%s", %s);\
          """
              .formatted(paramName, paramArgName));
    }

    return new QueryComponents(required, optionalQpSb.toString());
  }

  private HeaderComponents getHeaderComponents(List<? extends VariableElement> parameters) {
    final Function<VariableElement, String> headerNameExtractor =
        ve -> ve.getAnnotation(Header.class).name();
    final Function<VariableElement, String> headerArgNameExtractor =
        ve -> ve.getSimpleName().toString();

    final Map<Boolean, Map<String, String>> headersToArgNameByRequired =
        parameters.stream()
            .filter(elem -> elem.getAnnotation(Header.class) != null)
            .map(elem -> (VariableElement) elem)
            .collect(
                Collectors.partitioningBy(
                    ve -> ve.getAnnotation(Header.class).required(),
                    Collectors.toMap(
                        headerNameExtractor,
                        headerArgNameExtractor))); // TODO: add support for multiple headers with
    // the same name

    final var requiredSb = new StringBuilder();
    final var optionalSb = new StringBuilder();

    // Map<String, Object> requiredHeadersMap = new HashMap<>();
    // Map<String, Object> optionalHeadersMap = new HashMap<>();

    for (var requiredHeader : headersToArgNameByRequired.get(true).entrySet()) {
      final var headerName = requiredHeader.getKey();
      final var headerArgName = requiredHeader.getValue();

      requiredSb.append(
        """
        requiredHeadersMap.put("%s", %s);\
        """.formatted(headerName, headerArgName)
      );
    }

    for (var optionalHeader : headersToArgNameByRequired.get(false).entrySet()) {
      final var headerName = optionalHeader.getKey();
      final var headerArgName = optionalHeader.getValue();

      optionalSb.append(
        """
        optionalHeadersMap.put("%s", %s);\
        """.formatted(headerName, headerArgName)
      );

    }

    // return headersStrJoiner.toString();
    return new HeaderComponents(requiredSb.toString(), optionalSb.toString());
  }

  private String getPathResolver(
      ExecutableElement clientMethod, Client clientAnnotation, Request requestAnnotation) {
    final var basePath = clientAnnotation.basePath();
    final var endpoint = requestAnnotation.endpoint();

    final Function<VariableElement, String> pathParamNameExtractor =
        ve -> ve.getAnnotation(PathParam.class).name();
    final Function<VariableElement, String> pathParamArgNameExtractor =
        ve -> ve.getSimpleName().toString();

    final Map<String, String> queryParamsToArgName =
        clientMethod.getParameters().stream()
            .filter(elem -> elem.getAnnotation(PathParam.class) != null)
            .map(elem -> (VariableElement) elem)
            .collect(Collectors.toMap(pathParamNameExtractor, pathParamArgNameExtractor));

    var placeholdersPath =
        """
        "%s"\
        """
            .formatted(basePath + endpoint);

    final var formattedMethodSj = new StringJoiner(",", ".formatted(", ")");

    for (final var entry : queryParamsToArgName.entrySet()) {
      final var pathParamName = entry.getKey();
      final var argName = entry.getValue();

      placeholdersPath = placeholdersPath.replace("{%s}".formatted(pathParamName), "%s");
      formattedMethodSj.add(argName);
    }
    ;

    return placeholdersPath + formattedMethodSj.toString();
  }

  private void printMethodSignature(PrintWriter fpw, ExecutableElement clientMethod) {
    final var returnTypeStr = getReturnTypeStr(clientMethod);
    final var methodName = clientMethod.getSimpleName();
    final var methodParametersStr = getMethodParametersStr(clientMethod);
    final var throwClauseStr = getThrowClauseStr(clientMethod);

    fpw.print(
        """
          @Override
          public %s %s(%s) %s
        """
            .formatted(returnTypeStr, methodName, methodParametersStr, throwClauseStr));
  }

  private String getReturnTypeStr(ExecutableElement clientMethod) {
    return Optional.ofNullable(clientMethod.getReturnType()).map(Object::toString).orElse("void");
  }

  private String getThrowClauseStr(ExecutableElement clientMethod) {
    final StringJoiner sj = new StringJoiner(", ");

    for (var tt : clientMethod.getThrownTypes()) {
      sj.add(tt.toString());
    }

    return sj.length() > 0 ? "throws " + sj.toString() : "";
  }

  private String getMethodParametersStr(ExecutableElement clientMethod) {
    final StringJoiner sj = new StringJoiner(", ");

    for (var tp : clientMethod.getParameters()) {
      sj.add("%s %s".formatted(tp.asType().toString(), tp.getSimpleName()));
    }

    return sj.toString();
  }
}

record QueryComponents(String requiredQpStr, String optionalQpMapPopulationStr) {}

record HeaderComponents(String requiredHeadersMapPopulationStr, String optionalHeadersMapPopulationStr) {}
