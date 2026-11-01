package kalvi.transport;

/** Transport-fee record, kept separate from pickup planning (administrative report only). */
public class FeeRecord {
    private final String studentId;
    private final int amountDue;
    private final int amountPaid;

    public FeeRecord(String studentId, int amountDue, int amountPaid) {
        this.studentId = studentId;
        this.amountDue = amountDue;
        this.amountPaid = amountPaid;
    }

    public String getStudentId() { return studentId; }
    public int getAmountDue() { return amountDue; }
    public int getAmountPaid() { return amountPaid; }

    public String status() {
        if (amountPaid >= amountDue) return "PAID";
        return amountPaid > 0 ? "PARTIAL" : "PENDING";
    }
}
