import java.util.List;

public class PaperSizeResponse {
    public String jsonrpc;
    public Object id;
    public Result result;

    // Required classes for API response mapping
    public static class PaperSizeItem {
        public String name;
        public String dimensions;
        public String mediaSizeName;
        
        // Default constructor for Jackson
        public PaperSizeItem() {}
        
        public PaperSizeItem(String name, String dimensions, String mediaSizeName) {
            this.name = name;
            this.dimensions = dimensions;
            this.mediaSizeName = mediaSizeName;
        }
    }

    public static class Result {
        public int status;
        public String msg;
        public List<PaperSizeItem> items; // Perbaikan di sini
    }
}
