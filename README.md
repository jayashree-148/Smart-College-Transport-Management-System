# CampusTransit - Smart College Transportation Management (Java OOP + MySQL)

Web dashboard (HTML/CSS/JS) served by a **pure Java backend** (no Python, no frameworks) that reads and writes a **MySQL** database.
Mini Project 19 - Kalvi College: 120 buses, 14 routes, 70 pickup stops, 45 students.

```
Browser  ->  web/ (HTML+JS)  ->  Java HTTP server  ->  TransportService  ->  TransportDao  ->  MySQL
                                 kalvi.transport.web   kalvi.transport.service  kalvi.transport.dao
```

## 1. Run it (3 steps)

Needs **JDK 17+** (`javac -version`).

**A. Try it right now, no MySQL needed (demo mode)**
```
./run.sh --demo          (Windows: run.bat --demo)
```
Open http://localhost:8080 . A yellow banner reminds you it is reading `data/demo` files, not MySQL.

**B. Use your MySQL database (what you show in the review)**
1. Download `mysql-connector-j-8.x.x.jar` and copy it into `lib/`.
2. Edit `db.properties` (user / password).
3. Create + fill the database (uses JDBC, no `mysql` command needed):
   ```
   ./setup_db.sh            (Windows: setup_db.bat)       add --reset to rebuild from scratch
   ```
   (or run `sql/01_schema.sql` ... `sql/05_fleet.sql` in MySQL Workbench, in that order)
4. Start the server: `./run.sh` (Windows: `run.bat`). The console must print `[db] Connected to MySQL.`
   and the Settings page must show the green **MySQL** badge. Use `./run.sh --mysql-only` to refuse the demo fallback.

Port busy? `./run.sh --port 9090`.

## Easiest: connect MySQL from the web page

Sign in as **admin**. While MySQL is not connected the yellow banner has a **Connect to MySQL** button: enter host (`localhost`), port (`3306`), user (`root`) and your MySQL password, press **Connect**.
The server connects at once (no restart), creates `college_transport_db` + tables + sample data if needed, keeps the accounts you already made, and saves the settings in `db.properties`. If it fails, the form shows the exact reason (driver / wrong password / MySQL not running). This also works when you start the program from an IDE, where the console cannot ask for the password.

## Quick start (Windows PowerShell)

```
cd path\to\campustransit-java-mysql
.\stop.bat          # only if an old copy might still be running
.\run.bat           # first run: downloads the MySQL driver, asks for your MySQL password if needed, builds the database
```
1. Start the MySQL service (Services / XAMPP / Workbench).
2. `.\run.bat` - if MySQL refuses the password, the console asks for it (XAMPP default: just press Enter), tests it and saves it into `db.properties`. You can also edit `password=` there by hand.
3. The driver (`lib\mysql-connector-j-*.jar`) is downloaded automatically the first time (internet needed once), or drop the jar into `lib\` yourself.
4. The database and tables are created automatically. To build or rebuild them by hand: `.\setup_db.bat` (add `--reset` to wipe and reload).
5. The browser opens on the loading page, then sign in (`admin` / `Admin@123`) or create an account. No yellow banner = MySQL is connected; the console shows `[db] Connected to MySQL.`

## 0. If you still see the OLD page (no loading page, no sign-in)

An older copy of the program is still running and its window/port is being reused. Fix:
1. Run **`stop.bat`** (Windows) / `./stop.sh` - it stops any old CampusTransit server. (Or close the old black console window / press Ctrl+C in it.)
2. Unzip this version into a NEW folder and start it with **`run.bat`** / `./run.sh`.
3. The correct version shows **"CampusTransit v2.1 (login + street map)"** under the sign-in box and at the bottom of the sidebar.

The new server never silently reuses a busy port: if 8080 is taken it prints the port it chose and opens the browser there.
It also works from an IDE's Run button (main class `kalvi.transport.web.WebApp`): it finds the project folder and loads the MySQL driver from `lib/` itself.

## 1a. Start-up and the yellow "Demo mode" banner

`run.bat` (Windows) / `./run.sh` does everything in order: compile -> download the MySQL driver into `lib/` if it is missing (once, needs internet) -> start the server -> open the browser on the **loading page**, which leads to the **sign-in / create account** page.

The first time, if MySQL is running, the program also **creates `college_transport_db` and loads the sample data by itself**. The only thing you may need to set is your MySQL password in `db.properties` (`user=` / `password=`).

If the yellow banner says *Demo mode*, it now also says **why**:

| Reason shown | Fix |
|---|---|
| MySQL driver missing | run with `run.bat` / `./run.sh` while online (it downloads the driver), or copy `mysql-connector-j-8.x.x.jar` into `lib/` |
| MySQL refused the login | put the right password in `db.properties`, restart |
| MySQL is not running | start the MySQL service (Services / XAMPP / Workbench), restart |

Demo mode still works fully (sample data from `data/demo`); it just does not save to MySQL.

## 1b. Sign in (user ID + password)

The dashboard opens on a **sign-in screen**; every `/api/...` call except `/api/auth/*` returns 401 without a session.

| Role | Can do |
|---|---|
| **ADMIN** | everything, plus the **Users** page (change roles, reset passwords, delete users) |
| **STAFF** | change vehicle status, record fee payments, mark attendance, save plans |
| **VIEWER** | read-only (any POST is refused with 403). Self-registered accounts start as VIEWER |

- First run creates **admin / Admin@123**. Change it from the user menu (top right). **Create an account** on the sign-in screen registers a new VIEWER.
- Passwords are stored only as salted PBKDF2-HMAC-SHA256 hashes (120,000 rounds). 5 wrong passwords lock the account for 5 minutes.
- Session = random token in an `HttpOnly`, `SameSite=Strict` cookie, kept in server memory for 8 hours (restarting the server signs everyone out).
- Accounts live in MySQL table `app_users` (created automatically, also `sql/06_users.sql`); in demo mode in `data/demo/users.tsv`.
- Sign-ins, failed attempts and role changes are written to `event_log` (Settings page).
- Code: `auth/AuthService`, `auth/PasswordHasher`, `auth/Role`, `auth/Session`; checks are in `web/ApiHandler`. Frontend: sign-in screen + user menu in `web/app.js`.
- This is meant for a college demo on localhost. For a real deployment put it behind HTTPS and add the `Secure` cookie flag.

## 2. Pages

| Page | What it shows | Backend call |
|---|---|---|
| Dashboard | KPIs, route overview map (Dijkstra plan), fleet-status donut, fees, trips, alerts | `GET /api/dashboard` |
| Routes | 14 routes, stop-by-stop route map, buses per route | `GET /api/map`, `/api/routes` |
| Vehicles | 120 buses: search, filter, sort, paging, CSV export, detail window, change status | `GET /api/vehicles`, `/api/vehicles/{id}`, `POST /api/vehicles/{id}/status` |
| Students | 45 students, attendance toggle, fee status | `GET /api/students`, `POST /api/attendance` |
| Drivers / Conductors | 120 each, with phone and bus | `GET /api/crew?role=` |
| Fee Collection | totals, per-student balance, record a payment | `GET /api/fees`, `POST /api/fees/pay` |
| Tracking | live (simulated) bus positions on a **street map** (schematic map as offline fallback) | `GET /api/tracking` |
| Reports | fleet analysis + the **pickup plan** (Dijkstra), breakdown simulation, save plan | `GET /api/reports`, `/api/plan`, `POST /api/plan` |
| Alerts | expired documents, overdue service, breakdowns, fee dues (priority order) | `GET /api/alerts` |
| Users (admin) | user accounts and roles | `GET /api/users`, `POST /api/users/role|reset|delete` |
| Settings | data source, OOP + data-structure list, recent `event_log` rows | `GET /api/system`, `/api/events` |

Things that really change MySQL: vehicle status (`fleet_buses` + `buses`), fee payments (`fee_records`), attendance (`attendance`),
saved plan (`daily_trips`, `trip_assignments`), and every change is logged to `event_log`.
Try: set BUS-02 to *Broken Down* -> Reports -> the plan is re-computed for the remaining buses.

## 3. Code map

```
src/kalvi/transport/
  CollegeBus (abstract) <- StandardBus, MiniBus      polymorphic canUseRoad()
  Graph, TripPlanner, DailyTrip, Student, FeeRecord  planner core (Dijkstra, queues)
  PickupPlanningPolicy <- NearestNextStopPolicy      strategy interface
  model/    Person <- Driver, Conductor; Vehicle (has-a Driver, Conductor); Route (LinkedList of Stop); Stop; Alert; VehicleStatus
  dao/      TransportDao (interface) <- JdbcTransportDao (MySQL, ALL SQL here) | DemoTransportDao (files)
            DbConfig, DbSetup (runs sql/*.sql through JDBC)
  service/  TransportService (logic), BusSimulator
  auth/     AuthService (login, roles, sessions, lockout), PasswordHasher, Role, Session
  web/      WebApp (main), ApiHandler (URL -> service + login check), StaticHandler, Json, HttpError
  TransportApp, JdbcDataStore                        Swing desktop app facade over the same DAO (run_swing.sh)
sql/        01_schema, 02_pickup_stops (70 stops), 03_seed_data, 04_stop_coordinates, 05_fleet (120 buses, 14 routes)
web/        index.html, app.css, app.js, streetmap.js (map views), vendor/leaflet (bundled Leaflet 1.9.4)
data/demo/  tab-separated copy of the SQL data, used only by demo mode
```

### OOP concepts (say these in the viva)
- **Abstraction + inheritance**: `CollegeBus` -> `StandardBus` / `MiniBus`; `Person` -> `Driver` / `Conductor`.
- **Polymorphism**: `canUseRoad()` (standard buses avoid narrow roads, mini buses do not), `Person.role()`, `PickupPlanningPolicy`.
- **Encapsulation**: private fields + getters; `Vehicle`, `Route`, `Student`, `FeeRecord`.
- **Interfaces / dependency inversion**: the service depends on `TransportDao`; MySQL or files plug in underneath.
- **Composition**: `Vehicle` has a `Driver` and a `Conductor`; `Route` has many `Stop`s.
- **Layered design**: web and Swing -> shared DAO contract -> MySQL or demo files. SQL is confined to `JdbcTransportDao` / `DbSetup`.

### Data structures
| Structure | Where | Why |
|---|---|---|
| Graph (adjacency list) | `Graph.java` built from table `road_links` | roads + travel minutes between stops |
| PriorityQueue (min-heap) | Dijkstra in `Graph.shortestPaths`; `TransportService.alerts()` | nearest node / most urgent alert first |
| ArrayDeque as FIFO queue | `TripPlanner.waiting` (one queue per stop) | students board in arrival order |
| LinkedList | `Route.stops`, `ShortestPaths.pathTo` | ordered chain of stops |
| HashMap / HashSet | vehicle index by id, student counts per stop, present-student ids | O(1) lookup |
| TreeMap, sorted list + binary search | routes grouped by id; vehicle lookup by registration number | ordered, O(log n) |
| EnumMap | fleet status counters | fast counters per enum |
| ArrayDeque as stack | "Recent Trips" (push newest) | LIFO |

Algorithm: for each available bus, repeatedly run Dijkstra from the bus position, choose the nearest stop that still has waiting
students (`NearestNextStopPolicy`), board students FIFO until the bus is full, then return to campus. If a bus breaks down, its
waiting students go back to the stop queues and the other buses are re-planned.

### Handy SQL for the review
```sql
SELECT status, COUNT(*) FROM fleet_buses GROUP BY status;
SELECT fuel, COUNT(*) FROM fleet_buses GROUP BY fuel;
SELECT * FROM v_fleet_documents_due ORDER BY expiry;
SELECT * FROM v_fee_report WHERE fee_status <> 'PAID';
SELECT r.route_id, r.route_name, COUNT(b.bus_id) AS buses FROM fleet_routes r LEFT JOIN fleet_buses b USING (route_id) GROUP BY r.route_id;
SELECT * FROM event_log ORDER BY event_id DESC LIMIT 10;
```

## 4. Notes
- All names, phone numbers and registration numbers are invented sample data.
- Tracking is a **simulation** (`BusSimulator`): each bus loops along its route. Real GPS pings could replace it later.
- **Maps**: Dashboard, Routes and Tracking open as a real **OpenStreetMap street map** (Leaflet is bundled in `web/vendor/leaflet`; only the map tiles need internet). The button above each map switches to the schematic map, which is also used automatically when the tiles cannot load. Tile use follows the OpenStreetMap tile policy (fine for a demo).
- `v_fleet_documents_due` in `05_fleet.sql` uses a fixed date (2026-10-12); the web app compares with today's date instead.
- Server handles one request at a time on purpose (one shared JDBC connection, simple and safe for a project demo).
