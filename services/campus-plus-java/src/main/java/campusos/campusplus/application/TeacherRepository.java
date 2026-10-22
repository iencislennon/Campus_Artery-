package campusos.campusplus.application;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository {
    record TeacherOffice(String userId, String name, String department, String office) {}

    List<TeacherOffice> search(String nameContains);

    Optional<TeacherOffice> find(String userId);

    void save(TeacherOffice office);
}
