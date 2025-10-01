# Brzi razvoj i pokretanje (lokalno)

Ovaj projekat je multi‑module Spring Boot app (Jetty), sa Docker okruženjem za pune funkcije (LibreOffice, Tesseract, ocrmypdf…).

## Preduslovi
- Java 17+ (u dev containeru postoji)
- Gradle wrapper (u repo‑u)
- (Opcionalno) Docker za kompletne integracije

## Lokalni run (H2, bez Docker)
- Start:
  ```bash
  ./gradlew :stirling-pdf:bootRun
  ```
- Otvori: http://localhost:8080
- Health: http://localhost:8080/api/v1/info/status

Napomena: Neke funkcije (OCR, PDF/A preko LibreOffice, kompresija preko Ghostscript) zahtevaju spoljne alate i zato rade u Docker image‑u.

## Build JAR
```bash
./gradlew :stirling-pdf:bootJar -x test
ls app/core/build/libs
# → Stirling-PDF-<ver>.jar
```

## Samo primer testova
Možeš pokrenuti samo jedan test (npr. SrbService):
```bash
./gradlew :stirling-pdf:test --tests '*SrbServiceTest'
```

## Docker + Postgres (puna funkcionalnost)
```bash
docker compose -f docker-compose.postgres.yml up -d --build
```
Aplikacija će biti dostupna na http://localhost:8080.

Za iterativni razvoj, koristi override da mount‑uješ JAR bez rebuilda image‑a:
```bash
./gradlew :stirling-pdf:bootJar -x test
docker compose -f docker-compose.postgres.yml -f docker-compose.override.yml up -d --build
```

## Security/Pro funkcije
Podigni sa proprietary modulom (podrazumevano je uključeno u ovom okruženju). Za finije podešavanje koristi `configs/settings.yml` (login, OAuth2, SAML2…).

---
Ako želiš, mogu da dodam i skripte/Makefile za brži ciklus build‑run‑logs.
