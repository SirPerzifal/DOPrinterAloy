public class UpdateDOResponse {
    public String jsonrpc;
    public Object id;
    public Result result;

    public static class Result {
        public int status;
        public String msg;
    }
}