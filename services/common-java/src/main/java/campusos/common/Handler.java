package campusos.common;

@FunctionalInterface
public interface Handler {
    Response handle(Request request) throws Exception;
}
