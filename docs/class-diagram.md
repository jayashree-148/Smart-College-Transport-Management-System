# UML class diagram (paste into https://mermaid.live to export PNG for the report)

```mermaid
classDiagram
    class CollegeBus {
        <<abstract>>
        -String id
        -int capacity
        -boolean available
        +canUseRoad(Edge) boolean*
        +type() String*
    }
    class MiniBus
    class StandardBus
    CollegeBus <|-- MiniBus
    CollegeBus <|-- StandardBus

    class Student { -id -name -stopId -authorized }
    class FeeRecord { -studentId -amountDue -amountPaid +status() }
    class DailyTrip {
        -List~Student~ students
        -Set~String~ studentIds
        -List~Pickup~ pickups
        +assignStudent(Student) boolean
        +boardStudent(Student) boolean
        +completeTrip()
        +breakDownAfter(int)
    }
    class Graph {
        -Map adjacency
        +addRoad()
        +shortestPaths(source, bus) ShortestPaths
    }
    class PickupPlanningPolicy {
        <<interface>>
        +nextStop() String
    }
    class NearestNextStopPolicy
    class TripPlanner {
        -Map~String,ArrayDeque~ waiting
        +loadWaiting()
        +plan()
        +breakdown()
        +report()
    }
    class JdbcDataStore { +students() +saveTrips() +markAttendance() ... }
    class TransportApp { <<Swing GUI>> }

    PickupPlanningPolicy <|.. NearestNextStopPolicy
    DailyTrip "1" *-- "0..*" Student : assigned
    DailyTrip --> CollegeBus
    TripPlanner --> Graph
    TripPlanner --> PickupPlanningPolicy
    TripPlanner o-- DailyTrip
    Graph ..> CollegeBus : filters roads
    TransportApp --> TripPlanner
    TransportApp --> JdbcDataStore
    JdbcDataStore ..> Student
    JdbcDataStore ..> FeeRecord
```


## Web backend layers (added)

```mermaid
classDiagram
    class Person { <<abstract>> -name -phone +role()* }
    class Driver { -experienceYears }
    class Conductor
    Person <|-- Driver
    Person <|-- Conductor
    class Vehicle { -id -regNo -capacity -status ... }
    Vehicle *-- Driver
    Vehicle *-- Conductor
    class Route { -LinkedList~Stop~ stops }
    Route o-- Stop
    class TransportDao { <<interface>> +vehicles() +routes() +students() +fees() +graph() +saveTrips() ... }
    class JdbcTransportDao { MySQL via JDBC }
    class DemoTransportDao { data/demo files }
    TransportDao <|.. JdbcTransportDao
    TransportDao <|.. DemoTransportDao
    class TransportService { +dashboard() +vehicles() +plan() +alerts() +tracking() +reports() }
    TransportService --> TransportDao
    TransportService --> TripPlanner
    TransportService ..> Alert : PriorityQueue
    class ApiHandler { /api/... -> service }
    class StaticHandler { web/ files }
    class WebApp { main() }
    WebApp --> ApiHandler
    WebApp --> StaticHandler
    ApiHandler --> TransportService
```
