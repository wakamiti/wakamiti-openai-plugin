/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.openai;

import es.wakamiti.api.cli.CommandLine;
import es.wakamiti.api.cli.Option;
import es.wakamiti.api.cli.Options;
import es.wakamiti.api.contributor.CommandProvider;
import es.wakamiti.api.lang.WakamitiException;
import es.wakamiti.api.log.WakamitiLogger;
import es.wakamiti.extension.annotation.Extension;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

//@Extension(name = "openai")
public class FeatureGeneratorCommandProvider implements CommandProvider {

    private static final WakamitiLogger LOGGER = WakamitiLogger.of(FeatureGeneratorCommandProvider.class);

    @Override
    public String key() {
        return "generator";
    }

    @Override
    public String description() {
        return "Generates feature files based on API documentation using OpenAI.";
    }

    @Override
    public Options options() {
        Options options = new Options();
        options.addOption("l", "language", true, "ISO 639-1 language code");
        options.addOption("a", "api-docs", true, "Api docs url or json file");
        options.addOption("o", "output", true, "Feature Generator output directory");
        options.addOption("t", "token", true, "OpenAI token");
        return options;
    }

    @Override
    public void launch(CommandLine commandLine) throws WakamitiException {
        Map<String, String> config = loadConfig(commandLine);
        LOGGER.info("Configuration: {}", config);
        String language = config.get("language");
        String apiDocs = config.get("api-docs");
        String token = config.get("token");
        String output = config.get("output");

        FeatureGenerator generator = new FeatureGenerator(token, apiDocs);
        generator.generate(output, language);
    }

    private Map<String, String> loadConfig(CommandLine commandLine) {
        return options().getOptions().stream()
                .peek(opt -> Objects.requireNonNull(commandLine.getOptionValue(opt),
                        "Missing required arg: " + opt.getLongOpt()))
                .collect(Collectors.toMap(Option::getLongOpt, commandLine::getOptionValue));
    }

}
