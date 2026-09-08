package com.gotham.newsmediabrowser.datagen.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * HTTP client for ComfyUI REST API (SDXL-Turbo text-to-image and Wan2.1 text-to-video).
 */
public class ComfyuiClient {

    private static final String SERVICE_NAME = "ComfyUI";
    private final String baseUrl;
    private final Duration timeout;
    private final Duration pollInterval;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ComfyuiClient(String baseUrl, Duration timeout, Duration pollInterval, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(180);
        this.pollInterval = pollInterval != null ? pollInterval : Duration.ofMillis(500);
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public ComfyuiClient(String baseUrl) {
        this(baseUrl, Duration.ofSeconds(180), Duration.ofMillis(500), null, null);
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/system_stats"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public GeneratedMedia generateImage(String promptText, String negativePrompt, String title, String caption) {
        Objects.requireNonNull(promptText, "promptText must not be null");
        ObjectNode promptGraph = buildSdxlTurboGraph(promptText, negativePrompt);
        String filename = executePromptAndExtractOutput(promptGraph, "image");
        byte[] bytes = downloadFile(filename);

        String mimeType = filename.endsWith(".jpg") || filename.endsWith(".jpeg") ? "image/jpeg" : "image/png";
        String resolvedTitle = title != null && !title.isBlank() ? title : "Photograph: " + promptText.substring(0, Math.min(40, promptText.length()));
        String resolvedCaption = caption != null && !caption.isBlank() ? caption : promptText;

        return new GeneratedMedia(
                bytes,
                mimeType,
                filename,
                resolvedTitle,
                resolvedCaption,
                resolvedCaption,
                resolvedCaption,
                "Gotham Gazette Photo Desk"
        );
    }

    public GeneratedMedia generateVideo(String promptText, String negativePrompt, int seconds, String title, String caption) {
        Objects.requireNonNull(promptText, "promptText must not be null");
        int resolvedSeconds = seconds > 0 ? seconds : 5;
        ObjectNode promptGraph = buildWanVideoGraph(promptText, negativePrompt, resolvedSeconds);
        String filename = executePromptAndExtractOutput(promptGraph, "video");
        byte[] bytes = downloadFile(filename);

        String mimeType = "video/mp4";
        String resolvedTitle = title != null && !title.isBlank() ? title : "Video: " + promptText.substring(0, Math.min(40, promptText.length()));
        String resolvedCaption = caption != null && !caption.isBlank() ? caption : promptText;

        return new GeneratedMedia(
                bytes,
                mimeType,
                filename,
                resolvedTitle,
                resolvedCaption,
                resolvedCaption,
                resolvedCaption,
                "Gotham News Broadcast Archive"
        );
    }

    private String executePromptAndExtractOutput(ObjectNode promptGraph, String expectedKind) {
        try {
            ObjectNode requestPayload = objectMapper.createObjectNode();
            requestPayload.set("prompt", promptGraph);
            requestPayload.put("client_id", UUID.randomUUID().toString());

            byte[] requestBody = objectMapper.writeValueAsBytes(requestPayload);

            HttpRequest queueRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/prompt"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            HttpResponse<String> queueResponse = httpClient.send(queueRequest, HttpResponse.BodyHandlers.ofString());

            if (queueResponse.statusCode() != 200) {
                throw new DatagenClientException(SERVICE_NAME, queueResponse.statusCode(), "Queue prompt failed: " + queueResponse.body());
            }

            JsonNode queueJson = objectMapper.readTree(queueResponse.body());
            String promptId = queueJson.path("prompt_id").asText();
            if (promptId.isBlank()) {
                throw new DatagenClientException(SERVICE_NAME, "Missing prompt_id in queue response: " + queueResponse.body());
            }

            return pollHistoryForOutputFilename(promptId, expectedKind);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DatagenClientException(SERVICE_NAME, "Error submitting workflow to ComfyUI at " + baseUrl, e);
        }
    }

    private String pollHistoryForOutputFilename(String promptId, String expectedKind) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(timeout);

        while (Instant.now().isBefore(deadline)) {
            HttpRequest historyRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/history/" + promptId))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> historyResponse = httpClient.send(historyRequest, HttpResponse.BodyHandlers.ofString());

            if (historyResponse.statusCode() == 200 && historyResponse.body() != null && !historyResponse.body().isBlank()) {
                JsonNode historyRoot = objectMapper.readTree(historyResponse.body());
                JsonNode item = historyRoot.path(promptId);
                if (!item.isMissingNode()) {
                    JsonNode outputs = item.path("outputs");
                    if (outputs.isObject() && !outputs.isEmpty()) {
                        for (JsonNode nodeOutput : outputs) {
                            JsonNode filesArray = nodeOutput.has("images") ? nodeOutput.path("images")
                                    : nodeOutput.has("videos") ? nodeOutput.path("videos")
                                    : nodeOutput.has("gifs") ? nodeOutput.path("gifs") : null;

                            if (filesArray != null && filesArray.isArray() && !filesArray.isEmpty()) {
                                String filename = filesArray.get(0).path("filename").asText();
                                if (!filename.isBlank()) {
                                    return filename;
                                }
                            }
                        }
                    }
                }
            }

            Thread.sleep(pollInterval.toMillis());
        }

        throw new DatagenClientException(SERVICE_NAME, "Timed out waiting for " + expectedKind + " output for prompt " + promptId);
    }

    private byte[] downloadFile(String filename) {
        try {
            String viewUrl = baseUrl + "/view?filename=" + filename + "&type=output";
            HttpRequest viewRequest = HttpRequest.newBuilder()
                    .uri(URI.create(viewUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(viewRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new DatagenClientException(SERVICE_NAME, response.statusCode(), "Failed to download " + filename + " from /view");
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DatagenClientException(SERVICE_NAME, "Failed to download output media file: " + filename, e);
        }
    }

    public ObjectNode buildSdxlTurboGraph(String promptText, String negativePrompt) {
        ObjectNode graph = objectMapper.createObjectNode();

        // Node 3: KSampler
        ObjectNode ksampler = graph.putObject("3");
        ksampler.put("class_type", "KSampler");
        ObjectNode ksInputs = ksampler.putObject("inputs");
        ksInputs.put("seed", (long) (Math.random() * 1_000_000_000L));
        ksInputs.put("steps", 1);
        ksInputs.put("cfg", 1.0);
        ksInputs.put("sampler_name", "euler_ancestral");
        ksInputs.put("scheduler", "karras");
        ksInputs.put("denoise", 1.0);
        ksInputs.putArray("model").add("4").add(0);
        ksInputs.putArray("positive").add("6").add(0);
        ksInputs.putArray("negative").add("7").add(0);
        ksInputs.putArray("latent_image").add("5").add(0);

        // Node 4: CheckpointLoaderSimple
        ObjectNode ckpt = graph.putObject("4");
        ckpt.put("class_type", "CheckpointLoaderSimple");
        ckpt.putObject("inputs").put("ckpt_name", "sd_xl_turbo_1.0_fp16.safetensors");

        // Node 5: EmptyLatentImage
        ObjectNode latent = graph.putObject("5");
        latent.put("class_type", "EmptyLatentImage");
        ObjectNode latentInputs = latent.putObject("inputs");
        latentInputs.put("width", 512);
        latentInputs.put("height", 512);
        latentInputs.put("batch_size", 1);

        // Node 6: Positive prompt
        ObjectNode pos = graph.putObject("6");
        pos.put("class_type", "CLIPTextEncode");
        ObjectNode posInputs = pos.putObject("inputs");
        posInputs.put("text", promptText != null ? promptText : "Gotham city street scene, photo");
        posInputs.putArray("clip").add("4").add(1);

        // Node 7: Negative prompt
        ObjectNode neg = graph.putObject("7");
        neg.put("class_type", "CLIPTextEncode");
        ObjectNode negInputs = neg.putObject("inputs");
        negInputs.put("text", negativePrompt != null ? negativePrompt : "blurry, low quality, distorted");
        negInputs.putArray("clip").add("4").add(1);

        // Node 8: VAEDecode
        ObjectNode vae = graph.putObject("8");
        vae.put("class_type", "VAEDecode");
        ObjectNode vaeInputs = vae.putObject("inputs");
        vaeInputs.putArray("samples").add("3").add(0);
        vaeInputs.putArray("vae").add("4").add(2);

        // Node 9: SaveImage
        ObjectNode save = graph.putObject("9");
        save.put("class_type", "SaveImage");
        ObjectNode saveInputs = save.putObject("inputs");
        saveInputs.put("filename_prefix", "Gotham_SDXL");
        saveInputs.putArray("images").add("8").add(0);

        return graph;
    }

    public ObjectNode buildWanVideoGraph(String promptText, String negativePrompt, int seconds) {
        ObjectNode graph = objectMapper.createObjectNode();

        // Node 1: UNETLoader
        ObjectNode unet = graph.putObject("1");
        unet.put("class_type", "UNETLoader");
        unet.putObject("inputs").put("unet_name", "wan2.1_t2v_1.3B_bf16.safetensors");

        // Node 2: CLIPLoader
        ObjectNode clip = graph.putObject("2");
        clip.put("class_type", "CLIPLoader");
        ObjectNode clipInputs = clip.putObject("inputs");
        clipInputs.put("clip_name", "umt5_xxl_fp8_e4m3fn_scaled.safetensors");
        clipInputs.put("type", "wan");

        // Node 3: VAELoader
        ObjectNode vae = graph.putObject("3");
        vae.put("class_type", "VAELoader");
        vae.putObject("inputs").put("vae_name", "wan_2.1_vae.safetensors");

        // Node 4: EmptyWanLatentVideo (5s @ 16fps => 81 frames)
        int length = Math.max(16, seconds * 16 + 1);
        ObjectNode latent = graph.putObject("4");
        latent.put("class_type", "EmptyWanLatentVideo");
        ObjectNode latentInputs = latent.putObject("inputs");
        latentInputs.put("width", 480);
        latentInputs.put("height", 320);
        latentInputs.put("length", length);
        latentInputs.put("batch_size", 1);

        // Node 5: Positive prompt
        ObjectNode pos = graph.putObject("5");
        pos.put("class_type", "CLIPTextEncode");
        ObjectNode posInputs = pos.putObject("inputs");
        posInputs.put("text", promptText != null ? promptText : "Gotham city press conference, 5 seconds video");
        posInputs.putArray("clip").add("2").add(0);

        // Node 6: Negative prompt
        ObjectNode neg = graph.putObject("6");
        neg.put("class_type", "CLIPTextEncode");
        ObjectNode negInputs = neg.putObject("inputs");
        negInputs.put("text", negativePrompt != null ? negativePrompt : "blurry, low quality, stutter");
        negInputs.putArray("clip").add("2").add(0);

        // Node 7: KSampler
        ObjectNode ksampler = graph.putObject("7");
        ksampler.put("class_type", "KSampler");
        ObjectNode ksInputs = ksampler.putObject("inputs");
        ksInputs.put("seed", (long) (Math.random() * 1_000_000_000L));
        ksInputs.put("steps", 20);
        ksInputs.put("cfg", 6.0);
        ksInputs.put("sampler_name", "uni_pc");
        ksInputs.put("scheduler", "simple");
        ksInputs.put("denoise", 1.0);
        ksInputs.putArray("model").add("1").add(0);
        ksInputs.putArray("positive").add("5").add(0);
        ksInputs.putArray("negative").add("6").add(0);
        ksInputs.putArray("latent_image").add("4").add(0);

        // Node 8: VAEDecode
        ObjectNode vaeDecode = graph.putObject("8");
        vaeDecode.put("class_type", "VAEDecode");
        ObjectNode vdInputs = vaeDecode.putObject("inputs");
        vdInputs.putArray("samples").add("7").add(0);
        vdInputs.putArray("vae").add("3").add(0);

        // Node 9: Video Combine
        ObjectNode save = graph.putObject("9");
        save.put("class_type", "VHS_VideoCombine");
        ObjectNode saveInputs = save.putObject("inputs");
        saveInputs.put("filename_prefix", "Gotham_Wan_Video");
        saveInputs.put("fps", 16);
        saveInputs.putArray("images").add("8").add(0);

        return graph;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private static String normalizeBaseUrl(String url) {
        Objects.requireNonNull(url, "baseUrl must not be null");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
