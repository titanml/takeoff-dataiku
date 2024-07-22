package com.titanml.llm;

import com.dataiku.common.rpc.ExternalJSONAPIClient;
import com.dataiku.dip.connections.AbstractLLMConnection.HTTPBasedLLMNetworkSettings;
import com.dataiku.dip.custom.PluginSettingsResolver.ResolvedSettings;
import com.dataiku.dip.llm.custom.CustomLLMClient;
import com.dataiku.dip.llm.online.LLMClient;
import com.dataiku.dip.llm.online.LLMClient.CompletionQuery;
import com.dataiku.dip.llm.online.LLMClient.EmbeddingQuery;
import com.dataiku.dip.llm.online.LLMClient.SimpleCompletionResponse;
import com.dataiku.dip.llm.online.LLMClient.SimpleEmbeddingResponse;
import com.dataiku.dip.llm.promptstudio.PromptStudio.LLMStructuredRef;
import com.dataiku.dip.llm.utils.OnlineLLMUtils;
import com.dataiku.dip.resourceusage.ComputeResourceUsage;
import com.dataiku.dip.resourceusage.ComputeResourceUsage.LLMUsageType;
import com.dataiku.dip.utils.DKULogger;
import com.dataiku.dss.shadelib.org.apache.http.impl.client.HttpClientBuilder;
import com.dataiku.dss.shadelib.org.apache.http.impl.client.LaxRedirectStrategy;
import com.google.gson.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.function.Consumer;

public class TitanMLLLMConnector extends CustomLLMClient {
    final private static DKULogger logger = DKULogger.getLogger("dku.llm.titanml");
    ResolvedSettings resolvedSettings;
    private ExternalJSONAPIClient client;
    private ExternalJSONAPIClient tokenClient;

    public TitanMLLLMConnector() {
    }

    public void init(ResolvedSettings settings) {
        logger.info("Initializing TitanMLLLMConnector-----------------------------------");
        // Initialize the TitanMLLLMConnector. Takes a ResolvedSettings object.
        this.resolvedSettings = settings;
        String endpointUrl = resolvedSettings.config.get("endpoint_url").getAsString();
        String snowflakeAccountURL = resolvedSettings.config.get("snowflakeAccountUrl").getAsString();


        JsonElement snowflakeTokenEl = resolvedSettings.config.get("oauth");

        // Create a Dataiku ExternalJSONAPI client to call takeoff with
        Consumer<HttpClientBuilder> customizeBuilderCallback = (builder) -> {
            builder.setRedirectStrategy(new LaxRedirectStrategy());
            HTTPBasedLLMNetworkSettings networkSettings = new HTTPBasedLLMNetworkSettings();
            OnlineLLMUtils.add429RetryStrategy(builder, networkSettings);
        };
        client = new ExternalJSONAPIClient(endpointUrl, null, true, null, customizeBuilderCallback);
        tokenClient = new ExternalJSONAPIClient(snowflakeAccountURL, null, true, null, customizeBuilderCallback);

        logger.info("snowflakeTokenEl-----------------------------------");
        logger.info(snowflakeTokenEl);
        

        String access_token = snowflakeTokenEl.getAsJsonObject().get("snowflake_oauth").getAsString();
        
        JsonObject tokenRequestBody= new JsonObject();
        tokenRequestBody.addProperty("AUTHENTICATOR", "OAUTH");
        tokenRequestBody.addProperty("TOKEN", access_token);
        
        JsonObject trData = new JsonObject();
        trData.add("data",tokenRequestBody);
        

        logger.info("Access Token: " + access_token);
        logger.info("Token Request Body: " + trData);
        logger.info("Token Request Data: " + trData.get("data"));

        JsonObject tokenResp=new JsonObject();
        try {
            tokenResp = tokenClient.postObjectToJSON("/session/v1/login-request", JsonObject.class, trData);
        } catch (IOException e) {
            logger.error("SPCS session token exchange failed",e);
        }
        String sessionStr=tokenResp.get("data").getAsJsonObject().get("token").getAsString();
        String snowflakeToken =  "Snowflake Token=\""+sessionStr+"\"";
        
        logger.info("Token Response: " + tokenResp);
        logger.info("Session Token: " + sessionStr);
        logger.info("Snowflake Token: " + snowflakeToken);
        
        // // Decorate header with session token
        // llmClient = new ExternalJSONAPIClient(llmEndpointUrl, null, true, com.dataiku.dip.ApplicationConfigurator.getProxySettings(), customizeBuilderCallback);  
        client.addHeader("Authorization", snowflakeToken);

    }

    public int getMaxParallelism() {
        return 1;
    }

    public String get_snowflake_token(ResolvedSettings settings) {
        String snowflakeAccountURL = settings.config.get("snowflakeAccountUrl").getAsString();
        String access_token = settings.config.get("oauth").getAsJsonObject().get("snowflake_oauth").getAsString();

        Consumer<HttpClientBuilder> customizeBuilderCallback = (builder) -> {
            builder.setRedirectStrategy(new LaxRedirectStrategy());
            HTTPBasedLLMNetworkSettings networkSettings = new HTTPBasedLLMNetworkSettings();
            OnlineLLMUtils.add429RetryStrategy(builder, networkSettings);
        };
        // Begin Oauth to Session Token Dance
        ExternalJSONAPIClient tokenClient = new ExternalJSONAPIClient(snowflakeAccountURL, null, true, com.dataiku.dip.ApplicationConfigurator.getProxySettings(), customizeBuilderCallback);
        JsonObject tokenRequestBody= new JsonObject();
        tokenRequestBody.addProperty("AUTHENTICATOR", "OAUTH");
        tokenRequestBody.addProperty("TOKEN", access_token);
        logger.info("Access Token: " + access_token);

        JsonObject trData = new JsonObject();
        trData.add("data",tokenRequestBody);
        

        JsonObject tokenResp=new JsonObject();
        try {
            tokenResp = tokenClient.postObjectToJSON("/session/v1/login-request", JsonObject.class, trData);
            logger.info("Token Response: " + tokenResp);
        } catch (IOException e) {
            logger.error("SPCS session token exchange failed",e);
        }
        String sessionStr=tokenResp.get("data").getAsJsonObject().get("token").getAsString();
        logger.info("Session Token: " + sessionStr);
        return "Snowflake Token=\""+sessionStr+"\"";
    }


    public synchronized List<SimpleCompletionResponse> completeBatch(List<CompletionQuery> completionQueries) throws IOException {
        // Build up the list of simpleCompletionResponse in this function and
        // return
        List<SimpleCompletionResponse> ret = new ArrayList<>();

        for (CompletionQuery completionQuery : completionQueries) {
            // Get the titanML json payload from the completionQuery
            JsonObject jsonObject = getGenerationJsonObject(completionQuery);
            
            // Log the jsonObject to see what we are sending
            logger.info("Sending JSON object for processing: " + jsonObject.toString());

            try {
                // Send the query
                JsonObject response = client.postObjectToJSON("generate", JsonObject.class, jsonObject);

                // response should look like this:
                // {"text":["Something1","Something2"]}
                logger.info("Received JSON response: " + response);
                logger.info("Logging JSON response: {}" + response);
                String generations = response.get("text").getAsString();

                // And build the final result
                SimpleCompletionResponse queryResult = new SimpleCompletionResponse();
                queryResult.text = generations;

                // Add it to the list of results
                ret.add(queryResult);
            } catch (Exception e) {
                // Log any exception thrown during the HTTP request or response handling
                logger.error("Exception !!!!!!!!!!!!!!!!!", e);
                throw new IOException("Error during communication with API", e);
            }
        }


        return ret;
    }

    private JsonObject getGenerationJsonObject(CompletionQuery completionQuery) {
        // Make a TitanML compatible json POST object from a dataiku
        // completionQuery
        // Combine all the messages we've seen so far (dataiku uses a chat
        // completion like format, so concatenate with double newlines.)
        String completePrompt = completionQuery.messages.stream().map(LLMClient.ChatMessage::getTextEvenIfNotTextOnly).collect(Collectors.joining("\n\n"));
        logger.info("Prompt constructed: " + completePrompt);

        // Read out the settings (supported are temperature, topp, topk, and
        // max new tokens (also stop tokens, but those aren't supported in
        // takeoff)).

        // Now, build the json body that we send to takeoff. Create an empty JSON object,
        // and a json array with a capacity of one for the enclosing object and the text, resp.
        JsonObject jsonObject = new JsonObject();
        JsonArray prompts = new JsonArray(1);

        // Build the payload
        // Get the consumer group from the connection settings
        prompts.add(completePrompt);
        jsonObject.add("text", prompts);

        String consumerGroup =
                resolvedSettings.config.get("consumer_group").getAsString();

        if (consumerGroup != null) {
            // Add the consumer group to the body
            jsonObject.add("consumer_group", new JsonPrimitive(consumerGroup));

        }



        JsonElement jsonSchemaEl = resolvedSettings.config.get("jsonSchema");
        if (jsonSchemaEl != null && !jsonSchemaEl.isJsonNull() && !jsonSchemaEl.getAsString().isEmpty()){
            jsonObject.add("json_schema", JsonParser.parseString(jsonSchemaEl.getAsString()));
        }

        JsonElement regexEl = resolvedSettings.config.get("regexScheme");
        if (regexEl != null && !regexEl.isJsonNull() && !regexEl.getAsString().isEmpty()){
            jsonObject.add("regex_string", regexEl);
        }


        if (completionQuery.settings.temperature != null) {
            JsonElement temperature =
                    new JsonPrimitive(completionQuery.settings.temperature);
            jsonObject.add("sampling_temperature", temperature);
        }
        if (completionQuery.settings.topP != null) {
            JsonElement topP = new JsonPrimitive(completionQuery.settings.topP);
            jsonObject.add("sampling_topp", topP);
        }
        if (completionQuery.settings.topK != null) {
            JsonElement topK =
                    new JsonPrimitive(completionQuery.settings.topK);
            jsonObject.add("sampling_topk", topK);

        }
        if (completionQuery.settings.maxOutputTokens != null) {
            JsonElement maxNewTokens =
                    new JsonPrimitive(completionQuery.settings.maxOutputTokens);
            jsonObject.add("max_new_tokens", maxNewTokens);
        }

        return jsonObject;
    }

    public List<SimpleEmbeddingResponse> embedBatch(List<EmbeddingQuery> queries) throws IOException {
        // Build up the list of simpleCompletionResponse in this function and
        // return
        List<SimpleEmbeddingResponse> ret = new ArrayList<>();

        for (EmbeddingQuery embeddingQuery : queries) {
            // Get the titanML json payload from the completionQuery
            JsonObject jsonObject = getEmbeddingsJsonObject(embeddingQuery);

            // Send the query
            JsonObject response = client.postObjectToJSON("embed",
                    JsonObject.class, jsonObject);

            // response should look like this:
            // {"text":["Something1","Something2"]}
            logger.info("Logging JSON response: {}" + response);
            JsonArray result =
                    response.get("result").getAsJsonArray();

            JsonArray vector = result.get(0).getAsJsonArray();

            // And build the final result
            SimpleEmbeddingResponse queryResult =
                    new SimpleEmbeddingResponse();

            queryResult.embedding = convertJsonArrayToDoubleArray(vector);
            // Add it to the list of results
            ret.add(queryResult);
        }


        return ret;
    }

    private static double[] convertJsonArrayToDoubleArray(JsonArray jsonArray) {
        // Utility method to convert a GSON JsonArray type into an array of
        // doubles
        // Initializes the array with the size of the JsonArray
        double[] result = new double[jsonArray.size()];

        // Iterate over the JsonArray
        int i = 0;
        for (JsonElement element : jsonArray) {
            // Convert each element to double and store in the array
            result[i++] = element.getAsDouble();
        }
        return result;
    }

    private JsonObject getEmbeddingsJsonObject(EmbeddingQuery embeddingQuery) {
        // Make a TitanML compatible json POST object from a dataiku
        // completionQuery
        // Read out the embeddings text, wrap it in TitanML JSON
        String embeddingsText = embeddingQuery.text;

        // Now, build the json body that we send to takeoff. Create an empty JSON object,
        // and a json array with a capacity of one for the enclosing  and the text, resp.
        JsonObject jsonObject = new JsonObject();
        JsonArray prompts = new JsonArray(1);

        // put the text in the array, and the array in the object
        JsonPrimitive prompt = new JsonPrimitive(embeddingsText);
        prompts.add(prompt);
        jsonObject.add("text", prompts);

        // Get the consumer group from the connection settings
        String consumerGroup =
                resolvedSettings.config.get("consumer_group").getAsString();

        if (consumerGroup != null) {
            // Add the consumer group to the body
            jsonObject.add("consumer_group", new JsonPrimitive(consumerGroup));
        }

        return jsonObject;
    }

    public ComputeResourceUsage getTotalCRU(LLMUsageType usageType, LLMStructuredRef llmRef) {
        return null;
    }

}