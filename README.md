# Smart Lab — Projet 3

A virtual smart room built with Java and Spring Boot, based on the course starter kit. Three devices communicate with a gateway that handles registration, API requests and automation. A web dashboard displays their state and receives live updates through Server-Sent Events (SSE). No hardware or database is needed.

| Service | Module | Port |
| --- | --- | --- |
| Gateway and dashboard | `gateway` | 8080 |
| Thermostat | `thing-thermostat` | 8081 |
| Lamp | `thing-lamp` | 8082 |
| Motion sensor | `thing-motion` | 8083 |

## Run

**Requirements:** JDK 21, Maven 3.9 or later, and bash with curl for the tests (Git Bash on Windows).

Run commands from the project root, where `pom.xml` is located.

### Windows — PowerShell

```powershell
.\run.ps1
```

If PowerShell blocks the script, allow it for the current terminal session and retry:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\run.ps1
```

The script builds the project and starts all four services. Wait a few seconds for the devices to register, then:

1. Open **http://localhost:8080/**.
2. Enter **`operator-secret`**.
3. Click **Load things** and **Connect to the event stream**.

To stop:

```powershell
.\stop.ps1
```

### Linux or Git Bash

```bash
bash run.sh
```

Open the dashboard as described above. Press **Ctrl+C** to stop the services started by this script. Logs from the startup scripts are in `logs/`.

## Tests

Keep the services running. Open another **bash or Git Bash** terminal in the project root:

```bash
VIEWER=viewer-secret bash check-routes.sh
```

Expected result: **38 passed, 0 failed**. Without `VIEWER`, the script runs 35 checks and skips the three role checks.

The teacher's script checks API routes, status codes, SSE access and permissions. Use the demo below to also check automation and live dashboard updates.

For manual requests in PowerShell, use `curl.exe`, for example:

```powershell
curl.exe -i -H "Authorization: Bearer operator-secret" http://localhost:8080/things
```

The full API contract, including request bodies and response codes, is in **[`gateway.yaml`](gateway.yaml)**.

## Demo

1. Load the three devices and connect the event stream.
2. Change lamp brightness. Its state updates and `propertyChanged` appears in the live log.
3. While the temperature is below **19 °C**, click **Simulate motion**. The lamp turns on and the thermostat starts heating to 19 °C.
4. Wait for `targetReached`: heating switches to `off`.
5. The lamp turns off **10 seconds after the last motion**. Trigger another motion before the timeout to show that the timer restarts.

These steps demonstrate **R1** (motion-controlled lighting with an inactivity timer) and **R2** (motion-triggered heating that stops at the target). The thermostat starts at 17.5 °C and cools towards 15 °C when off.

Two bonuses are implemented:

- **Viewer/operator roles:** enter `viewer-secret`, load the devices and reconnect the stream. Reading remains available, but write controls are disabled. API writes with this token return `403`; a missing or invalid token returns `401`.
- **Device liveness:** use Task Manager to force-stop the lamp PID printed by the startup script, keeping the gateway running. After the next probe, its card shows `offline`. Restart the lamp with `java -jar thing-lamp/target/thing-lamp.jar` to see it return online. A normal shutdown may unregister it instead.

## Configuration and limits

Settings are in each module's `src/main/resources/application.yml`. Restart the affected service after changing them.

- **Lamp timer:** `rules.lamp-off-seconds` in the gateway is **10** for the demo. Use **60** for the reference delay.
- **Tokens:** `gateway.token` and `gateway.viewer-token` configure the two roles. Device tokens must match the operator token. Gateway API requests use Bearer authentication; browser SSE uses `?token=...` on `/events/stream`.
- **Liveness:** the gateway probes devices about every 10 seconds. Offline devices stay registered; proxy requests to unreachable devices return `502`.

State is stored in memory and resets when the corresponding service restarts. Tokens are static, and access control is enforced at the gateway; direct device routes remain accessible. R3 and R4 are not implemented, so automation can override manual settings. The OpenAPI file documents the API; server generation is not implemented.
