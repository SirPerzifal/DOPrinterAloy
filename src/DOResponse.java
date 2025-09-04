public class DOResponse {
    public String jsonrpc;
    public Object id;
    public Result result;

    public static class Result {
        public int status;
        public String msg;
        public java.util.List<java.util.Map<String, String>> items; // Perbaikan di sini
        public boolean is_more;
    }
}