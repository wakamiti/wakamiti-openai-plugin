/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.openai.test;


import es.wakamiti.openai.FeatureGeneratorCommandProvider;


public class FeatureGeneratorService {

    public static void main(String[] args) {
        FeatureGeneratorCommandProvider commandProvider = new FeatureGeneratorCommandProvider();
        commandProvider.launch(args);
    }

}
