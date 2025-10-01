o0o0o0o – Windows distribucija

Ovaj folder sadrži resurse za Windows installer i skriptu za instalaciju opcionih alata.

Sadržaj:
- resources/install-optional-tools.ps1 — instalira Ghostscript, qpdf, Tesseract i LibreOffice (tiho), dodaje u PATH.

Kako napraviti installer (EXE):
1) Build jar:
   - U root projektu pokrenuti: Gradle task `:stirling-pdf:bootJar` ili `build`.
2) Napraviti EXE sa jpackage:
   - Task: `jpackage` (kreira EXE u `build/jpackage`).

Napomene:
- JRE se pakuje uz installer (jpackage), tako da korisnik ne mora imati Java instaliran.
- Aplikacija se pokreće kao web server na lokalnom portu i otvara se u podrazumevanom browseru.
- Za OCR/konverzije/repair potrebno je pokrenuti `install-optional-tools.ps1` kao Administrator, ili ručno instalirati alate pa restartovati sistem da bi PATH bio osvežen.
