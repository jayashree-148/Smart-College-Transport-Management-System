package kalvi.transport.model;

public class Driver extends Person {
    private final int experienceYears;

    public Driver(String name, String phone, int experienceYears) {
        super(name, phone);
        this.experienceYears = experienceYears;
    }

    public int getExperienceYears() { return experienceYears; }
    @Override public String role() { return "Driver"; }
}
