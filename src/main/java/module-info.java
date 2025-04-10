/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
module wakamiti.openai  {

    exports es.wakamiti.openai;

    requires wakamiti.api;

    requires openai.java.core;
    requires openai.java.client.okhttp;

    requires io.swagger.parser;
    requires org.apache.commons.io;
    requires com.fasterxml.jackson.databind;


//    opens es.wakamiti.openai to wakamiti.extension;
//
//    provides es.wakamiti.api.contributor.CommandProvider with
//            es.wakamiti.openai.FeatureGeneratorCommandProvider;

}