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
        System.out.println("Fetch Response: " + response.statusCode());
        System.out.println("Fetch Body: " + response.body());

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
        System.out.println("Update Body: " + response.body());

        return mapper.readValue(response.body(), UpdateDOResponse.class);
    }

    /**
     * Extract all PDF bytes from DOResponse items
     * Items structure: {"74511": "pdf_binary", "74512": "pdf_binary", ...} or [] when empty
     */
    @SuppressWarnings("unchecked")
    public static Map<String, byte[]> getAllPdfBytes(DOResponse doResponse) {
        Map<String, byte[]> pdfMap = new HashMap<>();
        
        if (doResponse == null || doResponse.result == null || doResponse.result.items == null) {
            return pdfMap;
        }
        
        try {
            // Check if items is a Map (when has data)
            if (doResponse.result.items instanceof Map) {
                Map<String, String> items = (Map<String, String>) doResponse.result.items;
                
                for (Map.Entry<String, String> entry : items.entrySet()) {
                    String pickingId = entry.getKey();
                    String base64Data = entry.getValue();
                    
                    if (base64Data != null && !base64Data.isEmpty()) {
                        try {
                            byte[] pdfBytes = Base64.getDecoder().decode(base64Data);
                            pdfMap.put(pickingId, pdfBytes);
                        } catch (IllegalArgumentException e) {
                            System.err.println("Invalid base64 data for picking ID: " + pickingId);
                        }
                    }
                }
            }
            // If items is a List (empty), just return empty map
            else if (doResponse.result.items instanceof List) {
                System.out.println("Items is empty list, no PDFs to extract");
            }
            else {
                System.err.println("Unexpected items type: " + doResponse.result.items.getClass().getName());
            }
        } catch (ClassCastException e) {
            System.err.println("Unexpected data structure in items: " + e.getMessage());
        }
        
        return pdfMap;
    }

    /**
     * Get single PDF bytes by picking ID
     * Items structure: {"74511": "pdf_binary", "74512": "pdf_binary", ...} or [] when empty
     */
    @SuppressWarnings("unchecked")
    public static byte[] getPdfBytes(DOResponse doResponse, String pickingId) {
        if (doResponse == null || doResponse.result == null || doResponse.result.items == null) {
            return null;
        }
        
        try {
            // Only process if items is a Map
            if (doResponse.result.items instanceof Map) {
                Map<String, String> items = (Map<String, String>) doResponse.result.items;
                String base64Data = items.get(pickingId);
                
                if (base64Data != null && !base64Data.isEmpty()) {
                    try {
                        return Base64.getDecoder().decode(base64Data);
                    } catch (IllegalArgumentException e) {
                        System.err.println("Invalid base64 data for picking ID: " + pickingId);
                    }
                }
            }
            else {
                System.out.println("Items is not a Map, cannot extract PDF for ID: " + pickingId);
            }
        } catch (ClassCastException e) {
            System.err.println("Unexpected data structure in items: " + e.getMessage());
        }
        
        return null;
    }
}