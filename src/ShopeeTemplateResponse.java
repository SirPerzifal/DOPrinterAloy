import java.util.List;

public class ShopeeTemplateResponse {
    public String jsonrpc;
    public Object id;
    public Result result;

    public static class ShopeeTemplateItem {
        public String name;
        public String key;
        
        // Default constructor for Jackson
        public ShopeeTemplateItem() {}
        
        public ShopeeTemplateItem(String name, String key) {
            this.name = name;
            this.key = key;
        }
    }

    public static class Result {
        public int status;
        public String msg;
        public List<ShopeeTemplateItem> items; // Perbaikan di sini
    }
}
