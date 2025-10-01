package stirling.software.SPDF.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PdfEditWebController {

    @GetMapping("/organize/replace-page")
    public String replacePageView() {
        return "organize/replace-page"; // templates/organize/replace-page.html
    }
}
