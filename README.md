# SmartPark API

Intelligent parking management system for Manila-scale urban areas. Built with Spring Boot 3, Redisson (Redis), and H2 (portable to PostgreSQL).

---

## Prerequisites (TODO)

| Tool | Version                                                      |
|------|--------------------------------------------------------------|
| Java | 21+                                                          |
| Maven | 3.8+                                                         |
| Redis | 7+ *(optional – app falls back to DB lock when unavailable)* |

---

## Build

```bash
cd smartpark-api
mvn clean package -DskipTests
```

---

## Run

### With Redis (recommended for production-like testing)

Start Redis locally (Docker):
```bash
docker run -d --name smartpark-redis -p 6379:6379 redis:7-alpine
```

Start the application:
```bash
mvn spring-boot:run
```

### Custom Redis host/port

```bash
REDIS_HOST=my-redis-host REDIS_PORT=6380 mvn spring-boot:run
```

---

## Run Tests

```bash
mvn test
```

Tests are pure unit tests (Mockito). No Redis or database required to run them.

---

## H2 Console

Available at [http://localhost:8080/h2-console](http://localhost:8080/h2-console) while the app is running.

- JDBC URL: `jdbc:h2:mem:smartpark`
- Username: `dev`
- Password: *(leave blank)*

---

## API Reference

Base URL: `http://localhost:8080`

### Parking Lots

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/lots` | Register a parking lot |
| `GET` | `/api/v1/lots/{lotId}/occupancy` | Current occupancy & availability |
| `GET` | `/api/v1/lots/{lotId}/vehicles` | All vehicles currently parked |

### Vehicles

| Method | Path                              | Description                               |
|--------|-----------------------------------|-------------------------------------------|
| `POST` | `/api/v1/vehicles`                | Register a vehicle                        |
| `GET`  | `/api/v1/vehicles/{licensePlate}` | Get vehicle information                   |
| `GET`  | `/api/v1/vehicles` | Get a paginated list of vehicles (e.g., /api/v1/vehicles?page=0&size=20) |


### Parking Sessions

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/sessions/check-in` | Check a vehicle into a lot |
| `POST` | `/api/v1/sessions/check-out` | Check a vehicle out of a lot |

---

## Test the API

### Option A: Postman
#### Import smartpark-api.postman_collection.json into Postman:
```
Open Postman > Import > drag and drop the file
Set the baseUrl variable to http://localhost:8080
Run requests in this order: Register Lot > Register Vehicle > Check In > Check Out > Get Occupancy
```

### Option B: cURL
#### Register a parking lot
```bash
curl -s -X POST http://localhost:8080/api/v1/lots \
  -H "Content-Type: application/json" \
  -d '{"id":"LOT-MALL-01","location":"SM Megamall, Mandaluyong","capacity":200}'
```

#### Register a vehicle
```bash
curl -s -X POST http://localhost:8080/api/v1/vehicles \
  -H "Content-Type: application/json" \
  -d '{"licensePlate":"ZZZ-0001","vehicleType":"CAR","ownerName":"Jose Rizal"}'
```

#### Check in (uses pre-seeded data)
```bash
curl -s -X POST http://localhost:8080/api/v1/sessions/check-in \
  -H "Content-Type: application/json" \
  -d '{"lotId":"LOT-AYALA-01","licensePlate":"AAA-1234"}'
```

#### Check out
```bash
curl -s -X POST http://localhost:8080/api/v1/sessions/check-out \
  -H "Content-Type: application/json" \
  -d '{"lotId":"LOT-AYALA-01","licensePlate":"AAA-1234"}'
```

#### Get occupancy
```bash
curl -s http://localhost:8080/api/v1/lots/LOT-AYALA-01/occupancy
```

#### Get parked vehicles
```bash
curl -s http://localhost:8080/api/v1/lots/LOT-AYALA-01/vehicles
```

---

## Pre-seeded Data

The application loads the following data on startup (`DataSeeder.java`):

### Parking Lots
| ID | Location | Capacity |
|----|----------|----------|
| `LOT-AYALA-01` | Ayala Center, Makati City | 500 |
| `LOT-SMNORTH-01` | SM City North EDSA, Quezon City | 800 |
| `LOT-BGC-01` | Bonifacio High Street, BGC, Taguig | 300 |
| `LOT-ROBINSONS-01` | Robinsons Galleria, Ortigas | 600 |
| `LOT-GREENBELT-01` | Greenbelt 5, Makati City | 400 |

### Vehicles
| Plate | Type | Owner |
|-------|------|-------|
| `AAA-1234` | CAR | Juan dela Cruz |
| `BBB-5678` | CAR | Maria Santos |
| `CCC-9012` | MOTORCYCLE | Pedro Reyes |
| `DDD-3456` | TRUCK | Jose Garcia |
| `EEE-7890` | CAR | Ana Villanueva |
| `FFF-1122` | MOTORCYCLE | Carlo Bautista |
| `GGG-3344` | CAR | Rosa Mendoza |
| `HHH-5566` | TRUCK | Luis Torres |
| `III-7788` | CAR | Elena Ramos |
| `JJJ-9900` | MOTORCYCLE | Miguel Cruz |
