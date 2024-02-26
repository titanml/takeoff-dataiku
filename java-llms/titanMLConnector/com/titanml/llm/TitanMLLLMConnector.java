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
import com.dataiku.dip.llm.promptstudio.PromptStudio;
import com.dataiku.dip.llm.promptstudio.PromptStudio.LLMStructuredRef;
import com.dataiku.dip.llm.utils.OnlineLLMUtils;
import com.dataiku.dip.resourceusage.ComputeResourceUsage;
import com.dataiku.dip.resourceusage.ComputeResourceUsage.LLMUsageType;
import com.dataiku.dip.utils.DKULogger;
import com.dataiku.dss.shadelib.org.apache.http.client.methods.HttpDelete;
import com.dataiku.dss.shadelib.org.apache.http.client.methods.HttpGet;
import com.dataiku.dss.shadelib.org.apache.http.client.methods.HttpPost;
import com.dataiku.dss.shadelib.org.apache.http.client.methods.HttpPut;
import com.dataiku.dss.shadelib.org.apache.http.impl.client.HttpClientBuilder;
import com.dataiku.dss.shadelib.org.apache.http.impl.client.LaxRedirectStrategy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TitanMLLLMConnector extends CustomLLMClient {
    final private static DKULogger logger = DKULogger.getLogger("dku.llm.titanml");
    ResolvedSettings resolvedSettings;
    private ExternalJSONAPIClient client;

    public TitanMLLLMConnector() {
    }

    public void init(ResolvedSettings settings) {
        // Initialize the TitanMLLLMConnector. Takes a ResolvedSettings object.
        this.resolvedSettings = settings;
        String endpointUrl = resolvedSettings.config.get("endpoint_url").getAsString();
        // Create a Dataiku ExternalJSONAPI client to call takeoff with
        client = new ExternalJSONAPIClient(endpointUrl, null, true, null) {
            @Override
            protected HttpGet newGet(String path) {
                return new HttpGet(baseURI + path);
            }

            @Override
            protected HttpPost newPost(String path) {
                return new HttpPost(baseURI + path);
            }

            @Override
            protected HttpPut newPut(String path) {
                throw new IllegalArgumentException("unimplemented");
            }

            @Override
            protected HttpDelete newDelete(String path) {
                throw new IllegalArgumentException("unimplemented");
            }

            @Override
            protected void customizeBuilder(HttpClientBuilder builder) {
                builder.setRedirectStrategy(new LaxRedirectStrategy());
                HTTPBasedLLMNetworkSettings networkSettings = new HTTPBasedLLMNetworkSettings();
                OnlineLLMUtils.add429RetryStrategy(builder, networkSettings);

            }
        };

    }

    public int getMaxParallelism() {
        return 1;
    }

    public synchronized List<SimpleCompletionResponse> completeBatch(List<CompletionQuery> completionQueries) throws IOException {
        // Build up the list of simpleCompletionResponse in this function and
        // return
        List<SimpleCompletionResponse> ret = new ArrayList<>();

        for (CompletionQuery completionQuery : completionQueries) {
            // Get the titanML json payload from the completionQuery
            JsonObject jsonObject = getGenerationJsonObject(completionQuery);

            // Send the query
            JsonObject response = client.postObjectToJSON(":3000/generate", JsonObject.class, jsonObject);

            // response should look like this:
            // {"text":["Something1","Something2"]}
            logger.info("Logging JSON response: {}" + response);
            String generations = response.get("text").getAsString();

            // And build the final result
            SimpleCompletionResponse queryResult = new SimpleCompletionResponse();
            queryResult.text = generations;

            // Add it to the list of results
            ret.add(queryResult);
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
        PromptStudio.LLMCompletionSettings completionSettings =
                completionQuery.settings;

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

        if (completionSettings.temperature != null) {
            JsonElement temperature =
                    new JsonPrimitive(completionSettings.temperature);
            jsonObject.add("sampling_temperature", temperature);
        }
        if (completionSettings.topP != null) {
            JsonElement topP = new JsonPrimitive(completionSettings.topP);
            jsonObject.add("sampling_topp", topP);
        }
        if (completionSettings.topK != null) {
            JsonElement topK =
                    new JsonPrimitive(completionSettings.topK);
            jsonObject.add("sampling_topk", topK);

        }
        if (completionSettings.maxOutputTokens != null) {
            JsonElement maxNewTokens =
                    new JsonPrimitive(completionSettings.maxOutputTokens);
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
            JsonObject response = client.postObjectToJSON(":3000/embed",
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