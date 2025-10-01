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
<p align="center"><img src="https://raw.githubusercontent.com/Stirling-Tools/Stirling-PDF/main/docs/stirling.png" width="80"></p>
<h1 align="center">Stirling-PDF</h1>

Stirling PDF je moćan alat za rad sa PDF dokumentima, sa mogućnostima obrade, uređivanja, bezbednosti i konverzije. Podržava veliki broj funkcionalnosti uključujući spajanje, deljenje, dodavanje vodenih žigova, OCR, potpisivanje, enkripciju, i još mnogo toga.
Više informacija i dokumentacija su dostupni u originalnom README fajlu projekta, kao i putem pratećih dokumenata u ovom repozitorijumu.

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

—

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

—

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

—

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

—

## Pakovanje za Windows

Koristimo Gradle jpackage:

```powershell
./gradlew jpackage --no-daemon
```

- Ako je WiX (light.exe/candle.exe) na PATH‑u, pravi se EXE installer.
- Ako nije, pravi se “app‑image” (portable folder) u `build/jpackage`.

—

## Razvoj i build

```powershell
# Kompajliranje bez testova
./gradlew :stirling-pdf:classes -x test --no-daemon

# Build celog projekta (bez testova)
./gradlew build -x test --no-daemon
```

Branch: `feature/srb-ui-integration` (rebrand + SRB alati + konverzije + pomoć).

—

## Navigacija i rute (izbor)
- Početna: `/` – sve alatke grupisane (uklj. SRBIJA kolonu)
- SVG u PDF: `/svg-to-pdf`
- PDF u SVG: `/pdf-to-svg`
- DWG u PDF: `/dwg-to-pdf`
- PDF u DWG: `/pdf-to-dwg`
- Pomoć/Status: `/pomoc-alati`
- PDF/A‑3 (SRB): `/pdfa3`

—

## Rebrending
Naziv i vizuelni elementi su promenjeni na “o0o0o0o”. Ikonica i navbar su osveženi u okviru `app/core/src/main/resources/templates/fragments/navbar.html` i pratećih statičkih fajlova.

—

## Problemi i saveti
- Ne vidiš nove ikonice? Uradi “Hard refresh” (Ctrl+F5) ili otvori u privatnom prozoru.
- Konverzija vraća grešku 503? Proveri da li je potreban alat na PATH‑u (Inkscape itd.).
- DWG ne radi? Na `/pomoc-alati` proveri da li je detektovan neki CAD alat.
- OCR ruši? Tesseract mora imati validne traineddata fajlove; endpointovi su pooštreni da prijave jasnu grešku umesto pucanja.

—

## Licenca

Pogledaj `LICENSE` u root‑u repozitorijuma.

—

## Doprinosi
PR‑ovi su dobrodošli. Za veće izmene, prvo otvori issue ili kontaktiraj nas sa kratkim opisom.

Hvala što koristiš o0o0o0o! 🇷🇸
<p align="center"><img src="https://raw.githubusercontent.com/Stirling-Tools/Stirling-PDF/main/docs/stirling.png" width="80"></p>
<h1 align="center">Stirling-PDF</h1>

[![Docker Pulls](https://img.shields.io/docker/pulls/frooodle/s-pdf)](https://hub.docker.com/r/frooodle/s-pdf)
[![Discord](https://img.shields.io/discord/1068636748814483718?label=Discord)](https://discord.gg/HYmhKj45pU)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/Stirling-Tools/Stirling-PDF/badge)](https://scorecard.dev/viewer/?uri=github.com/Stirling-Tools/Stirling-PDF)
[![GitHub Repo stars](https://img.shields.io/github/stars/stirling-tools/stirling-pdf?style=social)](https://github.com/Stirling-Tools/stirling-pdf)

<a href="https://www.producthunt.com/posts/stirling-pdf?embed=true&utm_source=badge-featured&utm_medium=badge&utm_souce=badge-stirling&#0045;pdf" target="_blank"><img src="https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=641239&theme=light" alt="Stirling&#0032;PDF - Open&#0032;source&#0032;locally&#0032;hosted&#0032;web&#0032;PDF&#0032;editor | Product Hunt" style="width: 250px; height: 54px;" width="250" height="54" /></a>
[![Deploy to DO](https://www.deploytodo.com/do-btn-blue.svg)](https://cloud.digitalocean.com/apps/new?repo=https://github.com/Stirling-Tools/Stirling-PDF/tree/digitalOcean&refcode=c3210994b1af)

[Stirling-PDF](https://www.stirlingpdf.com) is a robust, locally hosted web-based PDF manipulation tool using Docker. It enables you to carry out various operations on PDF files, including splitting, merging, converting, reorganizing, adding images, rotating, compressing, and more. This locally hosted web application has evolved to encompass a comprehensive set of features, addressing all your PDF requirements.

All files and PDFs exist either exclusively on the client side, reside in server memory only during task execution, or temporarily reside in a file solely for the execution of the task. Any file downloaded by the user will have been deleted from the server by that point.

Homepage: [https://stirlingpdf.com](https://stirlingpdf.com)

All documentation available at [https://docs.stirlingpdf.com/](https://docs.stirlingpdf.com/)

![stirling-home](images/stirling-home.jpg)

## Features

- 50+ PDF Operations
- Parallel file processing and downloads
- Dark mode support
- Custom download options
- Custom 'Pipelines' to run multiple features in a automated queue
- API for integration with external scripts
- Optional Login and Authentication support (see [here](https://docs.stirlingpdf.com/Advanced%20Configuration/System%20and%20Security) for documentation)
- Database Backup and Import (see [here](https://docs.stirlingpdf.com/Advanced%20Configuration/DATABASE) for documentation)
- Enterprise features like SSO (see [here](https://docs.stirlingpdf.com/Advanced%20Configuration/Single%20Sign-On%20Configuration) for documentation)

## PDF Features

### Page Operations

- View and modify PDFs - View multi-page PDFs with custom viewing, sorting, and searching. Plus, on-page edit features like annotating, drawing, and adding text and images. (Using PDF.js with Joxit and Liberation fonts)
- Full interactive GUI for merging/splitting/rotating/moving PDFs and their pages
- Merge multiple PDFs into a single resultant file
- Split PDFs into multiple files at specified page numbers or extract all pages as individual files
- Reorganize PDF pages into different orders
- Rotate PDFs in 90-degree increments
- Remove pages
- Multi-page layout (format PDFs into a multi-paged page)
- Scale page contents size by set percentage
- Adjust contrast
- Crop PDF
- Auto-split PDF (with physically scanned page dividers)
- Extract page(s)
- Convert PDF to a single page
- Overlay PDFs on top of each other
- PDF to a single page
- Split PDF by sections

### Conversion Operations

- Convert PDFs to and from images
- Convert any common file to PDF (using LibreOffice)
- Convert PDF to Word/PowerPoint/others (using LibreOffice)
- Convert HTML to PDF
- Convert PDF to XML
- Convert PDF to CSV
- URL to PDF
- Markdown to PDF

### Security & Permissions

- Add and remove passwords
- Change/set PDF permissions
- Add watermark(s)
- Certify/sign PDFs
- Sanitize PDFs
- Auto-redact text

### Other Operations

- Add/generate/write signatures
- Split by Size or PDF
- Repair PDFs
- Detect and remove blank pages
- Compare two PDFs and show differences in text
- Add images to PDFs
- Compress PDFs to decrease their filesize (using qpdf)
- Extract images from PDF
- Remove images from PDF
- Extract images from scans
- Remove annotations
- Add page numbers
- Auto-rename files by detecting PDF header text
- OCR on PDF (using Tesseract OCR)
- PDF/A conversion (using LibreOffice)
- Edit metadata
- Flatten PDFs
- Get all information on a PDF to view or export as JSON
- Show/detect embedded JavaScript




# 📖 Get Started

Visit our comprehensive documentation at [docs.stirlingpdf.com](https://docs.stirlingpdf.com) for:

- Installation guides for all platforms
- Configuration options
- Feature documentation
- API reference
- Security setup
- Enterprise features


## Supported Languages

Stirling-PDF currently supports 40 languages!

| Language                                     | Progress                               |
| -------------------------------------------- | -------------------------------------- |
| Arabic (العربية) (ar_AR)                        | ![60%](https://geps.dev/progress/60)   |
| Azerbaijani (Azərbaycan Dili) (az_AZ)        | ![61%](https://geps.dev/progress/61)   |
| Basque (Euskara) (eu_ES)                     | ![36%](https://geps.dev/progress/36)   |
| Bulgarian (Български) (bg_BG)                | ![67%](https://geps.dev/progress/67)   |
| Catalan (Català) (ca_CA)                     | ![66%](https://geps.dev/progress/66)   |
| Croatian (Hrvatski) (hr_HR)                  | ![97%](https://geps.dev/progress/97)   |
| Czech (Česky) (cs_CZ)                        | ![68%](https://geps.dev/progress/68)   |
| Danish (Dansk) (da_DK)                       | ![60%](https://geps.dev/progress/60)   |
| Dutch (Nederlands) (nl_NL)                   | ![59%](https://geps.dev/progress/59)   |
| English (English) (en_GB)                    | ![100%](https://geps.dev/progress/100) |
| English (US) (en_US)                         | ![100%](https://geps.dev/progress/100) |
| French (Français) (fr_FR)                    | ![89%](https://geps.dev/progress/89)   |
| German (Deutsch) (de_DE)                     | ![96%](https://geps.dev/progress/96)   |
| Greek (Ελληνικά) (el_GR)                     | ![66%](https://geps.dev/progress/66)   |
| Hindi (हिंदी) (hi_IN)                          | ![66%](https://geps.dev/progress/66)   |
| Hungarian (Magyar) (hu_HU)                   | ![98%](https://geps.dev/progress/98)   |
| Indonesian (Bahasa Indonesia) (id_ID)        | ![61%](https://geps.dev/progress/61)   |
| Irish (Gaeilge) (ga_IE)                      | ![67%](https://geps.dev/progress/67)   |
| Italian (Italiano) (it_IT)                   | ![96%](https://geps.dev/progress/96)   |
| Japanese (日本語) (ja_JP)                    | ![91%](https://geps.dev/progress/91)   |
| Korean (한국어) (ko_KR)                      | ![66%](https://geps.dev/progress/66)   |
| Norwegian (Norsk) (no_NB)                    | ![65%](https://geps.dev/progress/65)   |
| Persian (فارسی) (fa_IR)                      | ![63%](https://geps.dev/progress/63)   |
| Polish (Polski) (pl_PL)                      | ![71%](https://geps.dev/progress/71)   |
| Portuguese (Português) (pt_PT)               | ![67%](https://geps.dev/progress/67)   |
| Portuguese Brazilian (Português) (pt_BR)     | ![74%](https://geps.dev/progress/74)   |
| Romanian (Română) (ro_RO)                    | ![56%](https://geps.dev/progress/56)   |
| Russian (Русский) (ru_RU)                    | ![89%](https://geps.dev/progress/89)   |
| Serbian Latin alphabet (Srpski) (sr_LATN_RS) | ![98%](https://geps.dev/progress/98)   |
| Simplified Chinese (简体中文) (zh_CN)         | ![92%](https://geps.dev/progress/92)   |
| Slovakian (Slovensky) (sk_SK)                | ![51%](https://geps.dev/progress/51)   |
| Slovenian (Slovenščina) (sl_SI)              | ![70%](https://geps.dev/progress/70)   |
| Spanish (Español) (es_ES)                    | ![96%](https://geps.dev/progress/96)   |
| Swedish (Svenska) (sv_SE)                    | ![64%](https://geps.dev/progress/64)   |
| Thai (ไทย) (th_TH)                           | ![58%](https://geps.dev/progress/58)   |
| Tibetan (བོད་ཡིག་) (bo_CN)                     | ![64%](https://geps.dev/progress/64) |
| Traditional Chinese (繁體中文) (zh_TW)        | ![98%](https://geps.dev/progress/98)   |
| Turkish (Türkçe) (tr_TR)                     | ![97%](https://geps.dev/progress/97)   |
| Ukrainian (Українська) (uk_UA)               | ![69%](https://geps.dev/progress/69)   |
| Vietnamese (Tiếng Việt) (vi_VN)              | ![56%](https://geps.dev/progress/56)   |
| Malayalam (മലയാളം) (ml_IN)              | ![72%](https://geps.dev/progress/72)   |

## Stirling PDF Enterprise

Stirling PDF offers an Enterprise edition of its software. This is the same great software but with added features, support and comforts.
Check out our [Enterprise docs](https://docs.stirlingpdf.com/Pro)


## 🤝 Looking to contribute?

Join our community:
- [Contribution Guidelines](CONTRIBUTING.md)
- [Translation Guide (How to add custom languages)](devGuide/HowToAddNewLanguage.md)
- [Developer Guide](devGuide/DeveloperGuide.md)
- [Issue Tracker](https://github.com/Stirling-Tools/Stirling-PDF/issues)
- [Discord Community](https://discord.gg/HYmhKj45pU)
