package campusos.common;

/** Identity taken from a verified token, never from client-supplied fields. */
public record AuthUser(String id, String name, String role) {
    public static final String STUDENT = "STUDENT";
    public static final String TEACHER = "TEACHER";

    public boolean isTeacher() { return TEACHER.equals(role); }
}
