package stirling.software.SPDF.controller.api.converters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import stirling.software.SPDF.config.EndpointConfiguration;

@RestController
@RequestMapping("/api/cad")
@RequiredArgsConstructor
@Slf4j
public class CadConvertController {

    private final EndpointConfiguration endpointConfiguration;

    @PostMapping(path = "/dwg-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> dwgToPdf(@RequestPart("file") MultipartFile file,
                                           @RequestParam(name = "dpi", required = false, defaultValue = "300") int dpi) throws Exception {
        if (!endpointConfiguration.isGroupEnabled("CAD")) {
            return ResponseEntity.status(503).contentType(MediaType.TEXT_PLAIN)
                    .body("CAD konverter nije dostupan (odaconv/uniconvertor/inkscape nije instaliran).".getBytes());
        }
        File tmpIn = Files.createTempFile("cad-in-", ".dwg").toFile();
        File tmpOutDir = Files.createTempDirectory("cad-out-").toFile();
        file.transferTo(tmpIn);
        try {
            // Try odaconv (ODA File Converter) -> converts DWG to PDF if available
            List<String> cmd = new ArrayList<>();
            if (isOnPath("odaconv")) {
                // Example: odaconv inputDWG outputFolder PDF 12
                cmd.add("odaconv");
                cmd.add(tmpIn.getAbsolutePath());
                cmd.add(tmpOutDir.getAbsolutePath());
                cmd.add("PDF");
                cmd.add(String.valueOf(dpi));
                exec(cmd.toArray(String[]::new));
            } else if (isOnPath("uniconvertor")) {
                // fallback: uniconvertor dwg -> svg, then use inkscape to pdf (best-effort)
                File svg = new File(tmpOutDir, "out.svg");
                exec(new String[]{"uniconvertor", tmpIn.getAbsolutePath(), svg.getAbsolutePath()});
                if (!svg.exists()) throw new RuntimeException("uniconvertor nije uspeo da napravi SVG");
                File pdf = new File(tmpOutDir, "out.pdf");
                exec(new String[]{"inkscape", svg.getAbsolutePath(), "--export-type=pdf", "--export-filename=" + pdf.getAbsolutePath()});
            } else if (isOnPath("inkscape")) {
                // Some DWG can be opened directly by inkscape via libdwg (depends on build)
                File pdf = new File(tmpOutDir, "out.pdf");
                exec(new String[]{"inkscape", tmpIn.getAbsolutePath(), "--export-type=pdf", "--export-filename=" + pdf.getAbsolutePath()});
            } else {
                throw new IllegalStateException("Nema podržanih CAD alata na PATH-u");
            }
            // Pick the first pdf in output dir
            File outPdf = findFirst(tmpOutDir, ".pdf");
            if (outPdf == null) {
                throw new RuntimeException("Konverzija nije proizvela PDF");
            }
            byte[] bytes = Files.readAllBytes(outPdf.toPath());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=converted.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes);
        } finally {
            safeDelete(tmpIn);
            safeDeleteDir(tmpOutDir);
        }
    }

    @PostMapping(path = "/pdf-to-dwg", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> pdfToDwg(@RequestPart("file") MultipartFile file) throws Exception {
        if (!endpointConfiguration.isGroupEnabled("CAD")) {
            return ResponseEntity.status(503).contentType(MediaType.TEXT_PLAIN)
                    .body("CAD konverter nije dostupan (odaconv/uniconvertor/inkscape nije instaliran).".getBytes());
        }
        File tmpIn = Files.createTempFile("cad-in-", ".pdf").toFile();
        File tmpOutDir = Files.createTempDirectory("cad-out-").toFile();
        file.transferTo(tmpIn);
        try {
            // There is no universal open-source PDF->DWG; attempt via inkscape to SVG then odaconv/uniconvertor to DWG (best-effort)
            File svg = new File(tmpOutDir, "out.svg");
            if (isOnPath("inkscape")) {
                exec(new String[]{"inkscape", tmpIn.getAbsolutePath(), "--export-type=svg", "--export-filename=" + svg.getAbsolutePath()});
            } else {
                throw new IllegalStateException("Inkscape nije dostupan za PDF→SVG korak");
            }
            File outDwg = new File(tmpOutDir, "out.dwg");
            if (isOnPath("odaconv")) {
                // Many tools do not support svg->dwg directly; odaconv is typically DWG/DXF↔PDF.
                // If odaconv can't import svg, fallback to dxf via inkscape then rely on odaconv for DXF->DWG
                File dxf = new File(tmpOutDir, "out.dxf");
                exec(new String[]{"inkscape", svg.getAbsolutePath(), "--export-dxf", dxf.getAbsolutePath()});
                if (!dxf.exists()) throw new RuntimeException("Inkscape nije uspeo da konvertuje u DXF");
                // There isn't a standard CLI for DXF->DWG in odaconv freeware; often it does DWG->DXF. Keep DXF as output.
                outDwg = dxf; // deliver DXF as closest interchange if DWG not possible
            } else if (isOnPath("uniconvertor")) {
                // Some builds support svg->cdr/dxf; deliver DXF
                File dxf = new File(tmpOutDir, "out.dxf");
                exec(new String[]{"uniconvertor", svg.getAbsolutePath(), dxf.getAbsolutePath()});
                outDwg = dxf;
            } else {
                throw new IllegalStateException("Nema alata za SVG→(DWG/DXF)");
            }
            byte[] bytes = Files.readAllBytes(outDwg.toPath());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=converted." + getExt(outDwg))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        } finally {
            safeDelete(tmpIn);
            safeDeleteDir(tmpOutDir);
        }
    }

    private static boolean isOnPath(String cmd) {
        try {
            Process p;
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                p = new ProcessBuilder("where", cmd).start();
            } else {
                p = new ProcessBuilder("which", cmd).start();
            }
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void exec(String[] cmd) throws Exception {
        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (var in = p.getInputStream()) {
            in.transferTo(baos);
        }
        int ec = p.waitFor();
        String out = baos.toString();
        if (ec != 0) {
            log.warn("CAD cmd failed (exitCode={}): {}\nOutput: {}", ec, String.join(" ", cmd), out);
            throw new RuntimeException("Komanda nije uspela (" + ec + "): " + String.join(" ", cmd));
        } else {
            log.debug("CAD cmd ok: {}\nOutput: {}", String.join(" ", cmd), out);
        }
    }

    private static File findFirst(File dir, String suffix) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                File r = findFirst(f, suffix);
                if (r != null) return r;
            } else if (f.getName().toLowerCase().endsWith(suffix)) {
                return f;
            }
        }
        return null;
    }

    private static void safeDelete(File f) { try { if (f != null) f.delete(); } catch (Exception ignored) {} }
    private static void safeDeleteDir(File d) {
        try {
            if (d != null && d.isDirectory()) {
                File[] files = d.listFiles(); if (files != null) for (File f : files) { if (f.isDirectory()) safeDeleteDir(f); else safeDelete(f); }
                d.delete();
            }
        } catch (Exception ignored) {}
    }

    private static String getExt(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(i+1) : "bin";
    }
}
