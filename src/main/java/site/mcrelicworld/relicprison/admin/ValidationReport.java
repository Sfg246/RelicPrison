package site.mcrelicworld.relicprison.admin;

import java.util.List;

public record ValidationReport(long createdAt, List<String> errors, List<String> warnings, List<String> information) {
    public ValidationReport { errors=List.copyOf(errors); warnings=List.copyOf(warnings); information=List.copyOf(information); }
    public boolean valid(){return errors.isEmpty();}
}
