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
    private String readerID;
    private ExternalJSONAPIClient client;

    public TitanMLLLMConnector() {
    }

    public void setHeaders(String key, String value){
        this.client.addHeader(key, value);
    }

    public void init(ResolvedSettings settings) {
        logger.info("Initializing TitanMLLLMConnector-----------------------------------");
        // Initialize the TitanMLLLMConnector. Takes a ResolvedSettings object.
        this.resolvedSettings = settings;
        String endpointUrl = resolvedSettings.config.get("endpoint_url").getAsString();


        // Create a Dataiku ExternalJSONAPI client to call takeoff with
        Consumer<HttpClientBuilder> customizeBuilderCallback = (builder) -> {
            builder.setRedirectStrategy(new LaxRedirectStrategy());
            HTTPBasedLLMNetworkSettings networkSettings = new HTTPBasedLLMNetworkSettings();
            OnlineLLMUtils.add429RetryStrategy(builder, networkSettings);
        };
        client = new ExternalJSONAPIClient(endpointUrl, null, true, null, customizeBuilderCallback);

        String consumer_group = null;
        if (settings.config.get("consumer_group") != null) {
            consumer_group = settings.config.get("consumer_group").getAsString();
        }

        if (consumer_group == null || consumer_group.isEmpty()) {
            logger.info("No consumer group was specified, defaulting to 'primary'");
            consumer_group = "primary";
        } else {
            logger.info(String.format("Retrieving example readerID for consumer_group %s ", consumer_group));
        }

        // Get the reader-id for chat template purposes, if nesc.
        if (settings.config.get("chatTemplate").getAsBoolean()) {

            try {
                JsonObject response = client.getToJSON("status", JsonObject.class);
                logger.info("Received JSON response: " + response);

                JsonObject liveReaders = response.getAsJsonObject("live_readers");

                for (String key : liveReaders.keySet()) {
                    JsonObject reader = liveReaders.getAsJsonObject(key);
                    JsonPrimitive consumerGroup = reader.getAsJsonPrimitive("consumer_group");
                    if (consumerGroup != null) {
                        if (consumer_group.equals(consumerGroup.getAsString())) {
                            readerID = key;
                            break;
                        }
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);

            }
            logger.info("Found readerID for template: " + readerID);

        }

    }

    public int getMaxParallelism() {
        return 1;
    }

    public synchronized List<SimpleCompletionResponse> completeBatch(List<CompletionQuery> completionQueries)
            throws IOException {
        // Build up the list of simpleCompletionResponse in this function and
        // return
        List<SimpleCompletionResponse> ret = new ArrayList<>();

        for (CompletionQuery completionQuery : completionQueries) {
            // Get the titanML json payload from the completionQuery

            //todo - get prompt template with batched calls instead

            JsonObject jsonObject = getGenerationJsonObject(completionQuery);

            // Log the jsonObject to see what we are sending
            logger.info("Sending JSON object for processing: " + jsonObject.toString());

            try {
                // Send the query
                JsonObject response = client.postObjectToJSON("generate", JsonObject.class, jsonObject);

                // response should look like this:
                // {"text":["Something1","Something2"]}
                logger.info("Logging JSON response: {}" + response);
                String generations = response.get("text").getAsString();

                // And build the final result
                SimpleCompletionResponse queryResult = new SimpleCompletionResponse();
                queryResult.text = generations;

                // Add it to the list of results
                ret.add(queryResult);
            } catch (Exception e) {
                // Log any exception thrown during the HTTP request or response handling
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

        String json_input = completionQuery.messages.stream().map(LLMClient.ChatMessage::getTextEvenIfNotTextOnly)
                .collect(Collectors.joining("\n\n"));
        String completePrompt;
        if (resolvedSettings.config.get("chatTemplate").getAsBoolean()) {

            JsonArray inputsArray = new JsonArray();
            try {
                JsonElement xElement = JsonParser.parseString(json_input);

                //its very easy to mess this up client-side; you HAVE to make sure there's no final comma after the
                // last message (i.e. it has to be bulletproof json).
                for (JsonElement element : xElement.getAsJsonArray()) {
                    if (element.isJsonNull()) {
                        logger.error("Trailing comma detected in list of messages. This will prevent JSON from parsing.");
                        throw new JsonParseException("Trailing comma");

                    }
                }

                inputsArray.add(xElement);
            } catch (JsonParseException e) {
                logger.error("Invalid JSON was input for messages");
                throw new RuntimeException(e);
            }

            JsonObject templatePayload = new JsonObject();
            templatePayload.add("inputs", inputsArray);

            try {
                logger.info("Template payload: " + templatePayload);
                JsonObject response = client.postObjectToJSON("chat_template/" + readerID,
                        JsonObject.class, templatePayload);
                logger.info("Logging Prompt template response: {}" + response);
                completePrompt = response.getAsJsonObject().get("messages").getAsJsonArray().get(0).getAsString();
                logger.info("TEMPLATED PROMPT:" + completePrompt);

            }
            catch (IOException e){
                logger.error("Chat template endpoint failed");
                throw new RuntimeException(e);
            }

        } else {
            completePrompt = json_input;
            logger.info("Prompt constructed: " + completePrompt);
        }

        // Read out the settings (supported are temperature, topp, topk, and
        // max new tokens (also stop tokens, but those aren't supported in
        // takeoff)).

        // Now, build the json body that we send to takeoff. Create an empty JSON
        // object,
        // and a json array with a capacity of one for the enclosing object and the
        // text, resp.
        JsonObject jsonObject = new JsonObject();
        JsonArray prompts = new JsonArray(1);

        // Build the payload
        // Get the consumer group from the connection settings
        prompts.add(completePrompt);
        jsonObject.add("text", prompts);



        String consumer_group = null;
        if (resolvedSettings.config.get("consumer_group") != null) {
            consumer_group = resolvedSettings.config.get("consumer_group").getAsString();
        }

        if (consumer_group == null || consumer_group.isEmpty()) {
            logger.info("No consumer group was specified");
            consumer_group = "primary";
        }


        jsonObject.add("consumer_group", new JsonPrimitive(consumer_group));


        JsonElement jsonSchemaEl = resolvedSettings.config.get("jsonSchema");
        if (jsonSchemaEl != null && !jsonSchemaEl.isJsonNull() && !jsonSchemaEl.getAsString().isEmpty()) {
            jsonObject.add("json_schema", JsonParser.parseString(jsonSchemaEl.getAsString()));
        }

        JsonElement regexEl = resolvedSettings.config.get("regexScheme");
        if (regexEl != null && !regexEl.isJsonNull() && !regexEl.getAsString().isEmpty()) {
            jsonObject.add("regex_string", regexEl);
        }

        if (completionQuery.settings.temperature != null) {
            JsonElement temperature = new JsonPrimitive(completionQuery.settings.temperature);
            jsonObject.add("sampling_temperature", temperature);
        }
        if (completionQuery.settings.topP != null) {
            JsonElement topP = new JsonPrimitive(completionQuery.settings.topP);
            jsonObject.add("sampling_topp", topP);
        }
        if (completionQuery.settings.topK != null) {
            JsonElement topK = new JsonPrimitive(completionQuery.settings.topK);
            jsonObject.add("sampling_topk", topK);

        }
        if (completionQuery.settings.maxOutputTokens != null) {
            JsonElement maxNewTokens = new JsonPrimitive(completionQuery.settings.maxOutputTokens);
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
            JsonArray result = response.get("result").getAsJsonArray();

            JsonArray vector = result.get(0).getAsJsonArray();

            // And build the final result
            SimpleEmbeddingResponse queryResult = new SimpleEmbeddingResponse();

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

        // Now, build the json body that we send to takeoff. Create an empty JSON
        // object,
        // and a json array with a capacity of one for the enclosing and the text, resp.
        JsonObject jsonObject = new JsonObject();
        JsonArray prompts = new JsonArray(1);

        // put the text in the array, and the array in the object
        JsonPrimitive prompt = new JsonPrimitive(embeddingsText);
        prompts.add(prompt);
        jsonObject.add("text", prompts);


        String consumer_group = null;
        if (resolvedSettings.config.get("consumer_group") != null) {
            consumer_group = resolvedSettings.config.get("consumer_group").getAsString();
        }

        if (consumer_group == null || consumer_group.isEmpty()) {
            logger.info("No consumer group was specified, defaulting to 'primary'");
            consumer_group = "primary";
        }
        jsonObject.add("consumer_group", new JsonPrimitive(consumer_group));

        return jsonObject;
    }

    public ComputeResourceUsage getTotalCRU(LLMUsageType usageType, LLMStructuredRef llmRef) {
        return null;
    }

}