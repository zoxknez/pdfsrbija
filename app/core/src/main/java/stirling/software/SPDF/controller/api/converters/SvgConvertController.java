package stirling.software.SPDF.controller.api.converters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api")
@Slf4j
public class SvgConvertController {

    @PostMapping(path = "/svg-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> svgToPdf(@RequestPart("file") MultipartFile file)
            throws Exception {
        if (!isOnPath("inkscape")) {
            return ResponseEntity.status(503)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Inkscape nije dostupan (nije na PATH-u).".getBytes());
        }
        File in = Files.createTempFile("svg-in-", ".svg").toFile();
        File out = Files.createTempFile("svg-out-", ".pdf").toFile();
        file.transferTo(in);
        try {
            exec(new String[] {
                "inkscape",
                in.getAbsolutePath(),
                "--export-type=pdf",
                "--export-filename=" + out.getAbsolutePath()
            });
            byte[] bytes = Files.readAllBytes(out.toPath());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=converted.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes);
        } finally {
            safeDelete(in);
            safeDelete(out);
        }
    }

    @PostMapping(path = "/pdf-to-svg", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> pdfToSvg(@RequestPart("file") MultipartFile file)
            throws Exception {
        if (!isOnPath("inkscape")) {
            return ResponseEntity.status(503)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Inkscape nije dostupan (nije na PATH-u).".getBytes());
        }
        File in = Files.createTempFile("svg-in-", ".pdf").toFile();
        File out = Files.createTempFile("svg-out-", ".svg").toFile();
        file.transferTo(in);
        try {
            exec(new String[] {
                "inkscape",
                in.getAbsolutePath(),
                "--export-type=svg",
                "--export-filename=" + out.getAbsolutePath()
            });
            byte[] bytes = Files.readAllBytes(out.toPath());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=converted.svg")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        } finally {
            safeDelete(in);
            safeDelete(out);
        }
    }

    private static boolean isOnPath(String cmd) {
        try {
            Process p = new ProcessBuilder(
                            System.getProperty("os.name").toLowerCase().contains("windows")
                                    ? new String[] {"where", cmd}
                                    : new String[] {"which", cmd})
                    .start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void exec(String[] cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (var in = p.getInputStream()) {
            in.transferTo(baos);
        }
        int ec = p.waitFor();
        String out = baos.toString();
        if (ec != 0) {
            log.warn("SVG cmd failed (exitCode={}): {}\nOutput: {}", ec, String.join(" ", cmd), out);
            throw new RuntimeException("Komanda nije uspela (" + ec + "): " + String.join(" ", cmd));
        }
    }

    private static void safeDelete(File f) {
        try {
            if (f != null) f.delete();
        } catch (Exception ignored) {
        }
    }
}
