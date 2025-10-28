import java.net.http.*;
import java.net.URI;
import java.util.*;
import java.util.Base64;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DOFetcher {
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static DOResponse fetchDO(String endpoint, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // System.out.println("Fetch Response: " + response.statusCode());
        // System.out.println("Fetch Body: " + response.body());

        return mapper.readValue(response.body(), DOResponse.class);
    }

    public static UpdateDOResponse updateDO(String endpoint, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Update Response: " + response.statusCode());
        // System.out.println("Update Body: " + response.body());

        return mapper.readValue(response.body(), UpdateDOResponse.class);
    }

    public static PaperSizeResponse GetPaperSize() throws Exception {
        String jsonBody = "{\"jsonrpc\": \"2.0\"}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://aloy.id/warehouse/get/paper-format"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Update Response: " + response.statusCode());
        // System.out.println("Update Body: " + response.body());

        return mapper.readValue(response.body(), PaperSizeResponse.class);
    }

    public static ShopeeTemplateResponse GetShopeeTemplateFormat() throws Exception {
        String jsonBody = "{\"jsonrpc\": \"2.0\"}";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://aloy.id/warehouse/get/shopee-template"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Update Response: " + response.statusCode());
        // System.out.println("Update Body: " + response.body());

        return mapper.readValue(response.body(), ShopeeTemplateResponse.class);
    }
}