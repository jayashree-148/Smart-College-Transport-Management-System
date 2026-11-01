package kalvi.transport.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** One bus of the fleet. Has-a Driver and has-a Conductor (composition). */
public class Vehicle {
    private final String id;
    private final String regNo;
    private final String type;          // STANDARD / MINI
    private final int capacity;
    private final String makeModel;
    private final int year;
    private final String fuel;
    private final String routeId;       // may be null (planned live by the Java planner)
    private final String shift;
    private final Driver driver;
    private final Conductor conductor;
    private VehicleStatus status;
    private final String remarks;
    private final int odometerKm;
    private final Double mileage;
    private final LocalDate lastService, nextService, insuranceExpiry, fitnessExpiry, pucExpiry;
    private final String gpsDevice;
    private final int fillPct;

    public Vehicle(String id, String regNo, String type, int capacity, String makeModel, int year, String fuel,
                   String routeId, String shift, Driver driver, Conductor conductor, VehicleStatus status,
                   String remarks, int odometerKm, Double mileage, LocalDate lastService, LocalDate nextService,
                   LocalDate insuranceExpiry, LocalDate fitnessExpiry, LocalDate pucExpiry,
                   String gpsDevice, int fillPct) {
        this.id = id; this.regNo = regNo; this.type = type; this.capacity = capacity;
        this.makeModel = makeModel; this.year = year; this.fuel = fuel; this.routeId = routeId;
        this.shift = shift; this.driver = driver; this.conductor = conductor; this.status = status;
        this.remarks = remarks; this.odometerKm = odometerKm; this.mileage = mileage;
        this.lastService = lastService; this.nextService = nextService;
        this.insuranceExpiry = insuranceExpiry; this.fitnessExpiry = fitnessExpiry; this.pucExpiry = pucExpiry;
        this.gpsDevice = gpsDevice; this.fillPct = fillPct;
    }

    public static long daysUntil(LocalDate d, LocalDate today) { return ChronoUnit.DAYS.between(today, d); }

    public String getId() { return id; }
    public String getRegNo() { return regNo; }
    public String getType() { return type; }
    public int getCapacity() { return capacity; }
    public String getMakeModel() { return makeModel; }
    public int getYear() { return year; }
    public String getFuel() { return fuel; }
    public String getRouteId() { return routeId; }
    public String getShift() { return shift; }
    public Driver getDriver() { return driver; }
    public Conductor getConductor() { return conductor; }
    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public int getOdometerKm() { return odometerKm; }
    public Double getMileage() { return mileage; }
    public LocalDate getLastService() { return lastService; }
    public LocalDate getNextService() { return nextService; }
    public LocalDate getInsuranceExpiry() { return insuranceExpiry; }
    public LocalDate getFitnessExpiry() { return fitnessExpiry; }
    public LocalDate getPucExpiry() { return pucExpiry; }
    public String getGpsDevice() { return gpsDevice; }
    public int getFillPct() { return fillPct; }
}
