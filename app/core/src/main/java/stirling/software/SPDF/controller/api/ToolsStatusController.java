package stirling.software.SPDF.controller.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import stirling.software.SPDF.config.EndpointConfiguration;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolsStatusController {

    private final EndpointConfiguration endpointConfiguration;

    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();

        // Helper to check PATH for a command
        java.util.function.Function<String, Boolean> onPath = (cmd) -> {
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
        };

        // CAD
        Map<String, Object> cad = new LinkedHashMap<>();
        cad.put("odaconv", onPath.apply("odaconv"));
        cad.put("inkscape", onPath.apply("inkscape"));
        cad.put("uniconvertor", onPath.apply("uniconvertor"));
        cad.put("enabled", endpointConfiguration.isGroupEnabled("CAD"));
        out.put("CAD", cad);

        // Ghostscript
        Map<String, Object> gs = new LinkedHashMap<>();
        gs.put("gs", onPath.apply("gs"));
        gs.put("enabled", endpointConfiguration.isGroupEnabled("Ghostscript"));
        out.put("Ghostscript", gs);

        // qpdf
        Map<String, Object> qpdf = new LinkedHashMap<>();
        qpdf.put("qpdf", onPath.apply("qpdf"));
        qpdf.put("enabled", endpointConfiguration.isGroupEnabled("qpdf"));
        out.put("qpdf", qpdf);

        // LibreOffice
        Map<String, Object> lo = new LinkedHashMap<>();
        lo.put("soffice", onPath.apply("soffice"));
        lo.put("enabled", endpointConfiguration.isGroupEnabled("LibreOffice"));
        out.put("LibreOffice", lo);

        // OCR/Tesseract
        Map<String, Object> tess = new LinkedHashMap<>();
        tess.put("tesseract", onPath.apply("tesseract"));
        tess.put("enabled", endpointConfiguration.isGroupEnabled("tesseract"));
        out.put("Tesseract", tess);

        // OCRmyPDF
        Map<String, Object> ocrmypdf = new LinkedHashMap<>();
        ocrmypdf.put("ocrmypdf", onPath.apply("ocrmypdf"));
        ocrmypdf.put("enabled", endpointConfiguration.isGroupEnabled("OCRmyPDF"));
        out.put("OCRmyPDF", ocrmypdf);

        // Weasyprint
        Map<String, Object> weasy = new LinkedHashMap<>();
        weasy.put("weasyprint", onPath.apply("weasyprint"));
        weasy.put("enabled", endpointConfiguration.isGroupEnabled("Weasyprint"));
        out.put("Weasyprint", weasy);

        // pdftohtml
        Map<String, Object> pdftohtml = new LinkedHashMap<>();
        pdftohtml.put("pdftohtml", onPath.apply("pdftohtml"));
        pdftohtml.put("enabled", endpointConfiguration.isGroupEnabled("Pdftohtml"));
        out.put("Pdftohtml", pdftohtml);

        // Unoconvert
        Map<String, Object> unoconv = new LinkedHashMap<>();
        unoconv.put("unoconvert", onPath.apply("unoconvert"));
        unoconv.put("enabled", endpointConfiguration.isGroupEnabled("Unoconvert"));
        out.put("Unoconvert", unoconv);

        // Python/OpenCV (best-effort)
        Map<String, Object> python = new LinkedHashMap<>();
        boolean py = onPath.apply("python") || onPath.apply("python3");
        python.put("python", py);
        python.put("enabledPython", endpointConfiguration.isGroupEnabled("Python"));
        python.put("enabledOpenCV", endpointConfiguration.isGroupEnabled("OpenCV"));
        out.put("Python", python);

        return out;
    }
}
