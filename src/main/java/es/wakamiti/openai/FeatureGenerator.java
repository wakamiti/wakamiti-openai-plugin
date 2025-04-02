/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.openai;


import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import es.wakamiti.api.lang.WakamitiException;
import es.wakamiti.api.log.WakamitiLogger;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static es.wakamiti.openai.Util.PATH_SEPARATOR;
import static es.wakamiti.openai.Util.parse;


/**
 * Generates feature files based on API documentation.
 * It uses the OpenAI API to generate the content of the feature files.
 */
public class FeatureGenerator {

    private static final WakamitiLogger LOGGER = WakamitiLogger.of(FeatureGenerator.class);
    private static final String FEATURE_EXTENSION = ".feature";
    private static final String DEFAULT_PROMPT = "/generator/prompt.txt";

    private final OpenAIOkHttpClient.Builder clientBuilder;
    private final Map<String, String> apiDocs;

    private OpenAIClient client;

    /**
     * Constructs a FeatureGenerator with the given API key and API documentation.
     *
     * @param apiKey  the API key for OpenAI
     * @param apiDocs the API documentation content or URL
     */
    public FeatureGenerator(
            String apiKey,
            String apiDocs
    ) {
        this.clientBuilder = OpenAIOkHttpClient.builder().apiKey(apiKey);
        this.apiDocs = parse(apiDocs);
    }

    /**
     * Generates feature files at the specified destination path and in the specified language.
     *
     * @param destinationPath the path where the feature files will be generated
     * @param language        the language in which the feature files will be generated
     */
    public void generate(
            String destinationPath,
            String language
    ) {
        LOGGER.info("Feature generation started...");
        try {
            client = clientBuilder.build();
            Path path = Path.of(destinationPath).toAbsolutePath();
            if (!Files.exists(path) && !path.toFile().mkdirs()) {
                throw new NoSuchFileException(path.toString());
            }

            CompletableFuture.allOf(apiDocs.entrySet().stream().map(e -> {
                String operationId = e.getKey();

                Path featurePath = Path.of(destinationPath, operationId + FEATURE_EXTENSION).toAbsolutePath();
                Map<String, Object> input = new LinkedHashMap<>();
                input.put("schema", e.getValue());
                input.put("language", language);
                if (operationId.contains(PATH_SEPARATOR)) {
                    String[] aux = operationId.split(PATH_SEPARATOR);
                    input.put("apiId", aux[0]);
                    operationId = aux[1];
                }
                input.put("operationId", operationId);
                return createFeature(featurePath, input);
            }).toArray(CompletableFuture[]::new)).join();
            client.close();
        } catch (NoSuchFileException e) {
            throw new WakamitiException(e);
        }
    }

    /**
     * Reads the default prompt.
     *
     * @return the prompt as a string
     */
    private String prompt() {
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream(DEFAULT_PROMPT))) {
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new WakamitiException(e);
        }
    }

    /**
     * Creates a feature file at the specified path with the given inputs.
     *
     * @param featurePath the path where the feature file will be created
     * @param inputs      the inputs for generating the feature file
     * @return a CompletableFuture representing the asynchronous operation
     */
    private CompletableFuture<?> createFeature(Path featurePath, Map<String, Object> inputs) {
        if (!Files.exists(featurePath) || featurePath.toFile().delete()) {
            try {
                if (!Files.exists(featurePath.getParent()) && !featurePath.getParent().toFile().mkdirs()) {
                    throw new NoSuchFileException(featurePath.getParent().toString(), null, "Cannot create dir");
                }
                Path path = Files.createFile(featurePath);
                String input = inputs.entrySet().stream()
                        .map(Map.Entry::toString)
                        .collect(Collectors.joining("\n"));

                ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                        .addUserMessage(prompt().concat(input))
                        .model(ChatModel.GPT_4O)
                        .build();
                return client.async().chat().completions().create(params)
                        .thenAccept(chatCompletion ->
                                chatCompletion.choices().get(0).message().content().ifPresentOrElse(content -> {
                                    try {
                                        Files.write(path, content.getBytes());
                                    } catch (IOException e) {
                                        throw new WakamitiException(e.getMessage(), e);
                                    }
                                }, () -> {
                                    throw new WakamitiException("Empty response");
                                })
                        );
            } catch (Exception e) {
                throw new WakamitiException("Cannot create feature [{}]", featurePath, e);
            }
        } else {
            return CompletableFuture.runAsync(() -> {});
        }
    }

}
