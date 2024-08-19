package com.titanml.llm;

import com.dataiku.common.rpc.ExternalJSONAPIClient;
import com.dataiku.dip.connections.AbstractLLMConnection;
import com.dataiku.dip.custom.PluginSettingsResolver.ResolvedSettings;
import com.dataiku.dip.llm.utils.OnlineLLMUtils;
import com.dataiku.dip.utils.DKULogger;
import com.dataiku.dss.shadelib.org.apache.http.impl.client.HttpClientBuilder;
import com.dataiku.dss.shadelib.org.apache.http.impl.client.LaxRedirectStrategy;
import com.google.gson.*;

import java.io.IOException;
import java.util.function.Consumer;

public class TitanMLLLMSnowflakeConnector extends TitanMLLLMConnector {
    final private static DKULogger logger = DKULogger.getLogger("dku.llm.titanml");
    ResolvedSettings resolvedSettings;

    public TitanMLLLMSnowflakeConnector() {

    }

    public void init(ResolvedSettings settings) {
        super.init(settings);
        logger.info("Adding snowflake functionality-----------------------------------");
        // Initialize the TitanMLLLMConnector. Takes a ResolvedSettings object.
        JsonElement snowflakeAccountURL = resolvedSettings.config.get("snowflakeAccountUrl");
        JsonElement snowflakeTokenPreset = resolvedSettings.config.get("oauth");
        String access_token = null;

        Consumer<HttpClientBuilder> customizeBuilderCallback = (builder) -> {
            builder.setRedirectStrategy(new LaxRedirectStrategy());
            AbstractLLMConnection.HTTPBasedLLMNetworkSettings networkSettings = new AbstractLLMConnection.HTTPBasedLLMNetworkSettings();
            OnlineLLMUtils.add429RetryStrategy(builder, networkSettings);
        };

        if (snowflakeTokenPreset != null && !snowflakeTokenPreset.getAsJsonObject().entrySet().isEmpty()) {
            access_token = snowflakeTokenPreset.getAsJsonObject().get("snowflake_oauth").getAsString();
        }


        // check if snowflake oauth token and snowflake account url are present
        if (access_token == null || snowflakeAccountURL.getAsString().isEmpty()) {
            logger.info(
                    "No snowflake oauth token or snowflake account url found in settings. This won't work for snowflake connection but will work for local takeoff");
        } else {
            logger.info("Snowflake oauth token and snowflake account url found in settings. Use snowflake connection");
            try (ExternalJSONAPIClient tokenClient = new ExternalJSONAPIClient(snowflakeAccountURL.getAsString(), null, true, null, customizeBuilderCallback)) {
                JsonObject tokenRequestBody = new JsonObject();
                tokenRequestBody.addProperty("AUTHENTICATOR", "OAUTH");
                tokenRequestBody.addProperty("TOKEN", access_token);

                JsonObject trData = new JsonObject();
                trData.add("data", tokenRequestBody);

                JsonObject tokenResp = tokenClient.postObjectToJSON("/session/v1/login-request", JsonObject.class, trData);
                String sessionStr = tokenResp.get("data").getAsJsonObject().get("token").getAsString();
                String snowflakeToken = "Snowflake Token=\"" + sessionStr + "\"";

                // Add the snowflake token to the client
                super.setHeaders("Authorization", snowflakeToken);
            }
            catch (IOException e) {
                logger.error("SPCS session token exchange failed", e);
            }

        }

    }


}