package kalvi.transport.model;

public class Conductor extends Person {
    public Conductor(String name, String phone) { super(name, phone); }
    @Override public String role() { return "Conductor"; }
}
