package kalvi.transport.model;

/** Abstract base for crew members (inheritance + abstraction). */
public abstract class Person {
    private final String name;
    private final String phone;

    protected Person(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public String getName() { return name; }
    public String getPhone() { return phone; }

    /** Each subclass says what it is (polymorphism). */
    public abstract String role();
}
