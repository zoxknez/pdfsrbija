package stirling.software.SPDF.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SrbWebController {


    @GetMapping("/iban")
    public String iban() {
        return "srb/iban";
    }

    @GetMapping("/model97")
    public String model97() {
        return "srb/model97";
    }

    @GetMapping("/translit")
    public String translit() {
        return "srb/translit";
    }

    @GetMapping("/ipsqr")
    public String ipsqr() {
        return "srb/ipsqr";
    }

    @GetMapping("/kurs")
    public String kurs() {
        return "srb/kurs";
    }

    @GetMapping("/pib")
    public String pib() {
        return "srb/pib";
    }

    @GetMapping("/jmbg")
    public String jmbg() {
        return "srb/jmbg";
    }

    @GetMapping("/ocr-prescription")
    public String ocrPrescription() {
        return "srb/ocr-prescription";
    }

    // (stubovi – možeš dopuniti kad kreneš dalje)
    @GetMapping("/sef")
    public String sef() {
        return "srb/sef";
    }

    @GetMapping("/pdfa3")
    public String pdfa3() {
        return "srb/pdfa3";
    }

    @GetMapping("/admin")
    public String admin() {
        return "srb/admin";
    }
}
