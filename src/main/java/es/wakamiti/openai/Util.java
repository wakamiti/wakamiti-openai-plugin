/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.openai;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.wakamiti.api.lang.WakamitiException;
import es.wakamiti.api.log.WakamitiLogger;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Utility class for parsing and manipulating OpenAPI schemas.
 */
public class Util {

    public static final String PATH_SEPARATOR = "/";
    private static final WakamitiLogger LOGGER = WakamitiLogger.of(FeatureGenerator.class);


    private Util() {
    }

    /**
     * Parses the given OpenAPI schema and returns a map of operation IDs to their corresponding JSON representations.
     *
     * @param schema the OpenAPI schema as a string
     *
     * @return a map of operation IDs to JSON representations of the OpenAPI fragments
     */
    public static Map<String, String> parse(String schema) {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolveFully(true);
        OpenAPIParser parser = new OpenAPIParser();

        SwaggerParseResult result = isLocation(schema) ?
                parser.readLocation(schema, null, parseOptions)
                : parser.readContents(schema, null, parseOptions);
        if (result.getMessages() != null) {
            result.getMessages().forEach(LOGGER::warn); // validation errors and warnings
        }
        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null) {
            throw new WakamitiException("Unresolved swagger schema");
        }

        return fragmentByOperation(openAPI).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toJson(e.getValue())));
    }

    /**
     * Fragments the given OpenAPI schema by operation and returns a map of operation IDs to OpenAPI fragments.
     *
     * @param openAPI the OpenAPI schema
     *
     * @return a map of operation IDs to OpenAPI fragments
     */
    private static Map<String, OpenAPI> fragmentByOperation(
            OpenAPI openAPI
    ) {
        Map<String, OpenAPI> fragments = new HashMap<>();
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            PathItem pathItem = entry.getValue();
            for (Map.Entry<PathItem.HttpMethod, Operation> operation : pathItem.readOperationsMap().entrySet()) {
                PathItem newPathItem = copy(pathItem);
                newPathItem.operation(operation.getKey(), operation.getValue());
                OpenAPI api = new OpenAPI();
                api.setInfo(openAPI.getInfo());
                api.setPaths(new Paths().addPathItem(entry.getKey(), newPathItem));
                fragments.put(operationId(entry.getKey(), newPathItem), api);
            }
        }
        return fragments;
    }

    /**
     * Converts the given object to a JSON string.
     *
     * @param o the object to convert
     *
     * @return the JSON representation of the object
     */
    private static String toJson(Object o) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new WakamitiException("Error writing json of {}", o, e);
        }
    }

    /**
     * Checks if the given string is a URL.
     *
     * @param url the string to check
     *
     * @return true if the string is a URL, false otherwise
     */
    private static boolean isLocation(
            String url
    ) {
        try {
            return new File(url).exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates a copy of the given PathItem.
     *
     * @param item the PathItem to copy
     *
     * @return a copy of the PathItem
     */
    private static PathItem copy(
            PathItem item
    ) {
        return new PathItem()
                .description(item.getDescription())
                .summary(item.getSummary())
                .servers(item.getServers())
                .$ref(item.get$ref())
                .extensions(item.getExtensions())
                .parameters(item.getParameters());
    }

    /**
     * Generates an operation ID for the given endpoint and PathItem.
     *
     * @param endpoint the endpoint
     * @param path     the PathItem
     *
     * @return the operation ID
     */
    private static String operationId(
            String endpoint,
            PathItem path
    ) {
        Map<PathItem.HttpMethod, Operation> operation = path.readOperationsMap();
        return operation.keySet().stream().findFirst()
                .map(op -> {
                    String opId = op.toString().toLowerCase() + endpointFormat(endpoint);
                    return Optional.ofNullable(operation.get(op).getTags())
                            .filter(tags -> !tags.isEmpty())
                            .map(tags -> tags.get(0).replaceAll("\\s+", "-"))
                            .map(t -> t + "/" + opId)
                            .orElse(opId);
                })
                .orElseThrow(() -> new WakamitiException("Cannot generate id of operation [{}]", endpoint));
    }

    /**
     * Formats the given endpoint to a string suitable for use in an operation ID.
     *
     * @param endpoint the endpoint
     *
     * @return the formatted endpoint
     */
    private static String endpointFormat(String endpoint) {
        StringBuilder builder = new StringBuilder();
        List<String> parameters = new LinkedList<>();
        for (String it : endpoint.replaceAll("[\\s*!;,?:@&=+$.~'()]", "").split(PATH_SEPARATOR)) {
            if (it.isEmpty()) continue;
            if (it.startsWith("{")) {
                it = it.replaceAll("\\{(.+?)}", "$1");
                parameters.add(it.substring(0, 1).toUpperCase() + it.substring(1));
            } else {
                builder.append(it.substring(0, 1).toUpperCase()).append(it.substring(1));
            }
        }
        if (!parameters.isEmpty()) {
            builder.append("By").append(String.join("And", parameters));
        }
        return builder.toString();
    }

}
