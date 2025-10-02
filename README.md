# o0o0o0o — lokalni PDF toolbox (SRB)

o0o0o0o je lokalna (on‑prem) web aplikacija zasnovana na Spring Boot‑u za rad sa PDF dokumentima i specifičnim alatima za Srbiju. Radi potpuno lokalno (bez slanja fajlova na internet) i uključuje:

- PDF/A‑3 (MVP):
	- Ugrađivanje priloga (AF), sRGB OutputIntent, XMP pdfaid (part=3, conformance=B), Preflight validacija (Apache PDFBox Preflight)
	- UI saveti i rezultat validacije
- Zameni stranicu (Replace Page) — end‑to‑end alat
- SRBIJA alati (posebna kolona na početnoj):
	- IPS/QR (SEF i račun), model 97, IBAN, PIB, JMBG, transliteracija, kurs, SEF pomoć
- Konverzije:
	- SVG ↔ PDF (preko Inkscape)
	- DWG ↔ PDF (preko eksternih CAD alata ako su dostupni)
- Pomoć – Opcioni alati: uživo status eksterne zavisnosti (PATH detekcija), kratka uputstva i linkovi
- Pakovanje za Windows: jpackage (EXE ako je WiX prisutan; u suprotnom portable app‑image)

Sve rute i UI su na srpskom jeziku, sa jasnim porukama kad neki opcioni alat nedostaje.

---

## Brzi start (Windows, PowerShell)

Potrebno:
- Java 21 (JDK)
- Gradle wrapper je već u repozitorijumu (gradlew.bat)

Pokretanje lokalno:

```powershell
# iz root foldera repozitorijuma
./gradlew :stirling-pdf:bootRun --no-daemon -DBROWSER_OPEN=false -DSTIRLING_PDF_DESKTOP_UI=false -DDISABLE_ADDITIONAL_FEATURES=true
# otvori u browseru
# http://localhost:8080/
```

Alternativa (build bez testova, pa start):

```powershell
./gradlew build -x test --no-daemon
java -jar app/core/build/libs/Stirling-PDF-*.jar
```

Napomena: Postoji i desktop UI mod, ali je podrazumevano isključen (`-DSTIRLING_PDF_DESKTOP_UI=false`).

---

## Glavne funkcije

### PDF/A‑3 (MVP)
- Dodavanje priloga (AF) i validacija sa Preflight‑om
- Automatski sRGB OutputIntent + XMP pdfaid
- Stranica: “PDF/A‑3” (u koloni SRBIJA)

### Zamena stranice (Replace Page)
- Alat “Zameni stranicu” dostupan u sekciji Organize

### SRBIJA kolona
- IPS/QR, model 97, IBAN, PIB, JMBG, transliteracija (Ćir ↔ Lat), kurs, SEF

### Konverzije
- SVG u PDF i PDF u SVG
	- Zahteva Inkscape u PATH‑u; bez njega će konverzija vratiti 503 i jasnu poruku
	- Rute: `/svg-to-pdf` i `/pdf-to-svg`
- DWG ↔ PDF (eksperimentalno)
	- Radi ako postoji jedan od CAD alata (odaconv/Teigha, unoconvert, inkscape fallback za neke formate)
	- Rute: `/dwg-to-pdf` i `/pdf-to-dwg`

### Pomoć – Opcioni alati
- Stranica: `/pomoc-alati`
- API status: `/api/tools/status` (JSON)
- Pregled instaliranih/dostupnih eksternih alata i grupe koje su time omogućene/onemogućene

---

## Eksterni alati (opciono)

Neke funkcije zavise od alata na PATH‑u. Aplikacija će raditi i bez njih (ikonice i stranice su vidljive), ali će sama funkcija vratiti jasnu poruku ako alat nije instaliran.

- Inkscape — SVG ↔ PDF
- Ghostscript — kompresija/popravka (alternativa Java/qpdf)
- qpdf — kompresija/popravka (alternativa Ghostscript/Java)
- Tesseract i/ili OCRmyPDF — OCR
- LibreOffice ili Unoconvert — konverzija office → PDF
- WeasyPrint — HTML/Markdown/EML → PDF
- pdftohtml — PDF → HTML/Markdown
- CAD alati — DWG ↔ PDF (odaconv/Teigha, unoconvert)

Proveri status na: `/pomoc-alati` ili JSON: `/api/tools/status`.

---

## Pakovanje za Windows

Koristimo Gradle jpackage:

```powershell
./gradlew jpackage --no-daemon
```

- Ako je WiX (light.exe/candle.exe) na PATH‑u, pravi se EXE installer.
- Ako nije, pravi se “app‑image” (portable folder) u `build/jpackage`.

---

## Razvoj i build

```powershell
# Kompajliranje bez testova
./gradlew :stirling-pdf:classes -x test --no-daemon

# Build celog projekta (bez testova)
./gradlew build -x test --no-daemon
```

Branch: `feature/srb-ui-integration` (rebrand + SRB alati + konverzije + pomoć).

---

## Navigacija i rute (izbor)
- Početna: `/` – sve alatke grupisane (uklj. SRBIJA kolonu)
- SVG u PDF: `/svg-to-pdf`
- PDF u SVG: `/pdf-to-svg`
- DWG u PDF: `/dwg-to-pdf`
- PDF u DWG: `/pdf-to-dwg`
- Pomoć/Status: `/pomoc-alati`
- PDF/A‑3 (SRB): `/pdfa3`

---

## Rebrending
Naziv i vizuelni elementi su promenjeni na “o0o0o0o”. Ikonica i navbar su osveženi u okviru `app/core/src/main/resources/templates/fragments/navbar.html` i pratećih statičkih fajlova.

---

## Problemi i saveti
- Ne vidiš nove ikonice? Uradi “Hard refresh” (Ctrl+F5) ili otvori u privatnom prozoru.
- Konverzija vraća grešku 503? Proveri da li je potreban alat na PATH‑u (Inkscape itd.).
- DWG ne radi? Na `/pomoc-alati` proveri da li je detektovan neki CAD alat.
- OCR ruši? Tesseract mora imati validne traineddata fajlove; endpointovi su pooštreni da prijave jasnu grešku umesto pucanja.

---

## Licenca

Pogledaj `LICENSE` u root‑u repozitorijuma.

---

## Doprinosi
PR‑ovi su dobrodošli. Za veće izmene, prvo otvori issue ili kontaktiraj nas sa kratkim opisom.

Hvala što koristiš o0o0o0o! 🇷🇸
