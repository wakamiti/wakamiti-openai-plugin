/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.chatgpt.test;


import com.openai.client.okhttp.OpenAIOkHttpClient;
import es.wakamiti.api.lang.WakamitiException;
import es.wakamiti.chatgpt.FeatureGenerator;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.RegexBody.regex;


public class FeatureGeneratorTest {

    private static final String[] API_DOCS = {
            "examples/v2.0/json/petstore.json", "examples/v2.0/json/petstore-separate/spec/swagger.json",
            "examples/v2.0/yaml/petstore.yaml", "examples/v2.0/yaml/petstore-separate/spec/swagger.yaml",
            "examples/v3.0/petstore.json", "examples/v3.0/petstore.yaml"
    };
    private static final Path TEMP_PATH = Path.of("target/output");
    private static final Integer PORT = 4321;
    private static final String BASE_URL = MessageFormat.format("http://localhost:{0}", PORT.toString());
    private static final ClientAndServer server = startClientAndServer(PORT);
    private static final List<File> FEATURES = Stream.of(
                    "pets1/getPets", "pets1/postPets", "pets2/getPetsById", "deletePetsById")
            .map(f -> TEMP_PATH.resolve(f + ".feature").toFile())
            .toList();

    private static Stream<Arguments> schemas() {
        return Stream.of(
                Arguments.of("examples/v2.0/json/petstore.json"),
                Arguments.of("examples/v2.0/json/petstore-separate/spec/swagger.json"),
                Arguments.of("examples/v2.0/yaml/petstore.yaml"),
                Arguments.of("examples/v2.0/yaml/petstore-separate/spec/swagger.yaml"),
                Arguments.of("examples/v3.0/petstore.json"),
                Arguments.of("examples/v3.0/petstore.yaml")
        );
    }

    private static Stream<Arguments> features() {
        return Stream.of(
                Arguments.of("pets1/getPets", "getPets", "pets1"),
                Arguments.of("pets1/postPets", "postPets", "pets1"),
                Arguments.of("pets2/getPetsById", "getPetsById", "pets2"),
                Arguments.of("deletePetsById", "deletePetsById", null)
        );
    }

    @AfterAll
    public static void tearDown() {
        server.close();
    }

    @BeforeEach
    public void beforeEach() throws IOException {
        FileUtils.deleteDirectory(TEMP_PATH.toFile());
        server.reset();
    }

    @DisplayName("Test schema by content")
    @ParameterizedTest(name = "Schema ''{0}'' ")
    @FieldSource("API_DOCS")
    public void testGenerateTestWhenContentWithSuccess(
            String schema
    ) throws IOException, NoSuchFieldException, IllegalAccessException {
        // Prepare
        mockServer(
                request()
                        .withMethod("POST")
                        .withPath("/chat/completions"),
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"choices\":[{\"message\":{\"content\":\"Something\"}}]}"),
                Times.exactly(FEATURES.size())
        );

        FeatureGenerator featureGenerator = new FeatureGenerator("", content(schema));
        getField(featureGenerator, "clientBuilder", OpenAIOkHttpClient.Builder.class).baseUrl(BASE_URL);


        // Act
        featureGenerator.generate(TEMP_PATH.toAbsolutePath().toString(), "en");

        // Check
        assertThat(FEATURES).allMatch(File::exists)
                .allMatch(f -> Files.contentOf(f, Charset.defaultCharset()).equals("Something"));
    }

    @DisplayName("Test schema by local url")
    @ParameterizedTest(name = "Schema ''{0}'' ")
    @FieldSource("API_DOCS")
    public void testGenerateTestWhenLocalURLWithSuccess(
            String schema
    ) throws NoSuchFieldException, IllegalAccessException {
        // Prepare
        mockServer(
                request()
                        .withMethod("POST")
                        .withPath("/chat/completions"),
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"choices\":[{\"message\":{\"content\":\"Something\"}}]}"),
                Times.exactly(FEATURES.size())
        );

        FeatureGenerator featureGenerator = new FeatureGenerator("", url(schema).toExternalForm());
        getField(featureGenerator, "clientBuilder", OpenAIOkHttpClient.Builder.class).baseUrl(BASE_URL);


        // Act
        featureGenerator.generate(TEMP_PATH.toAbsolutePath().toString(), "en");

        // Check
        assertThat(FEATURES).allMatch(File::exists)
                .allMatch(f -> Files.contentOf(f, Charset.defaultCharset()).equals("Something"));
    }

    @DisplayName("Test schema by http url")
    @Test
//    @ParameterizedTest(name = "Feature ''{0}'' ")
//    @MethodSource("features")
    public void testGenerateTestWhenHttpURLWithSuccess()
            throws IOException, NoSuchFieldException, IllegalAccessException {
        // Prepare
        for (File file : FEATURES) {
            Matcher matcher = Pattern.compile(Pattern.quote(TEMP_PATH.toString()) + "([/\\\\]([^/\\\\]++))?[/\\\\]([^/\\\\]+?)\\.feature$")
                    .matcher(file.toString());
            matcher.find();
            String apiId = matcher.group(2);
            String operationId = matcher.group(3);

            String r = ".+schema=\\{.+}\\\\nlanguage=en";
            if (apiId != null) r += "\\\\napiId="+apiId;
            r += "\\\\noperationId="+operationId+".+";
            mockServer(
                    request()
                            .withMethod("POST")
                            .withPath("/chat/completions")
                            .withBody(regex(r))
                    ,
                    response()
                            .withStatusCode(200)
                            .withContentType(MediaType.APPLICATION_JSON)
                            .withBody("{\"choices\":[{\"message\":{\"content\":\"Something\"}}]}")
            );
        }


        mockServer(
                request()
                        .withMethod("GET")
                        .withPath("/api_docs"),
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody(content("examples/v3.0/petstore.json"))
        );

        FeatureGenerator featureGenerator = new FeatureGenerator("", BASE_URL + "/api_docs");
        getField(featureGenerator, "clientBuilder", OpenAIOkHttpClient.Builder.class).baseUrl(BASE_URL);


        // Act
        featureGenerator.generate(TEMP_PATH.toAbsolutePath().toString(), "en");

        // Check
        assertThat(FEATURES).allMatch(File::exists)
                .allMatch(f -> Files.contentOf(f, Charset.defaultCharset()).equals("Something"));
    }

    private URL url(String resource) {
        return Objects.requireNonNull(this.getClass().getClassLoader().getResource(resource));
    }

    private String content(String resource) throws IOException {
        return IOUtils.toString(url(resource), Charset.defaultCharset());
    }

    private void mockServer(HttpRequest expected, HttpResponse response) {
        mockServer(expected, response, Times.once());
    }

    private void mockServer(HttpRequest expected, HttpResponse response, Times times) {
        server.when(expected, times).respond(response);
    }

    private static <T> T getField(Object o, String field, Class<T> cls) throws NoSuchFieldException, IllegalAccessException {
        Field buildField = o.getClass().getDeclaredField(field);
        buildField.setAccessible(true);
        return cls.cast(buildField.get(o));
    }

}
