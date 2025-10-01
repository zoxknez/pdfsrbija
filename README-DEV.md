# Stirling-PDF — Local dev notes (Docker & Postgres)

Short guide to run the project locally using Docker (Postgres) or building the local image.

Prerequisites
- Docker (Desktop or Engine) installed and running
- PowerShell (Windows) for the helper scripts
- Java/Gradle (only if you want to build without the Gradle wrapper)

Key files / scripts
- `docker-compose.postgres.yml` — Docker Compose for Postgres + Stirling service
- `scripts/build-and-docker-up.ps1` — Build the project (uses `gradlew` if present) and start docker compose (recommended for local dev)
- `scripts/docker-up.ps1` — Start docker compose and wait for the app health endpoint
- `scripts/shutdown-app.ps1` — Stops app processes, optionally runs `docker compose down` and can remove H2 DB files

Recommended quick workflows

1) Build locally and run (recommended)

   From PowerShell (repo root):

   ```powershell
   # Build the project and run docker compose (builds the container from local Dockerfile)
   .\scripts\build-and-docker-up.ps1
   ```

   - Script will run the Gradle build (using `gradlew` if present) and then `docker compose -f docker-compose.postgres.yml up -d --build`.
   - The helper then polls `http://localhost:8080/api/v1/info/status` until the app reports UP (or times out).

2) Run the published image (no local build)

   Uncomment `image:` in `docker-compose.postgres.yml` and comment the `build:` block, then:

   ```powershell
   docker compose -f docker-compose.postgres.yml up -d
   .\scripts\docker-up.ps1 -ComposeFile docker-compose.postgres.yml
   ```

Stopping and cleanup

 - Graceful stop of local processes and optionally Docker:

   ```powershell
   # Dry-run (shows actions)
   .\scripts\shutdown-app.ps1 -DryRun

   # Stop Java processes, ports, and bring docker compose down (removes volumes)
   .\scripts\shutdown-app.ps1 -Force -DockerDown
   ```

Notes & warnings
- `shutdown-app.ps1 -DockerDown` will run `docker compose -f docker-compose.postgres.yml down --volumes --remove-orphans` from the repository root. That will remove the Postgres volume and delete DB data unless you change the compose volumes.
- The compose file mounts `./stirling/latest/config`, `./stirling/latest/logs` and `./stirling/latest/data` on the host — ensure these folders exist or change them if you prefer another location.
- If you prefer iterative code changes without rebuilding the image each time, we can add a development compose that mounts the built JAR or runs the jar directly from the host (I can add that if you want).

Next steps I can take for you
- Run a quick environment check (Docker, docker compose, gradlew exist) and optionally run the full build+docker compose up now.
- Add a small `docker-compose.override.yml` for local iterative development (mounts build output / enables dev JVM options).

If you want me to proceed with the build+up now, tell me to go ahead and I will start it (it may take some minutes and requires Docker running).

Iterative development (fast edit / rebuild loop)

 - A `docker-compose.override.yml` has been added to mount the built JAR from `app/core/build/libs` into the container so you can rebuild the JAR locally and restart the container without rebuilding the full image.

    Quick usage:

    ```powershell
    # Build the JAR locally
    .\gradlew.bat build -x test

    # Start compose with override
    .\scripts\docker-up-override.ps1
    ```

 - The helper `scripts/docker-up-override.ps1` runs `docker compose -f docker-compose.postgres.yml -f docker-compose.override.yml up -d --build` and then waits for the application health endpoint.

 - Note: the override mounts host folders into the container; ensure you have the build JAR present before running, and adjust permissions if the container cannot read the files.

Tailing logs

 - Use the helper to tail both containers (runs docker compose logs -f):

    ```powershell
    .\scripts\tail-logs.ps1
    ```

 - To tail only the Stirling app logs:

    ```powershell
    .\scripts\tail-logs.ps1 stirling
    ```
