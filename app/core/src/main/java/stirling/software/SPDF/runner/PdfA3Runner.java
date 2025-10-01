package stirling.software.SPDF.runner;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import stirling.software.SPDF.service.PdfA3Service;

public class PdfA3Runner {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: PdfA3Runner <input.pdf> <ubl.xml> [out.pdf]");
            System.exit(2);
        }
        File inPdf = new File(args[0]);
        File inXml = new File(args[1]);
        File out = args.length >= 3 ? new File(args[2]) : new File("pdfa3-fresh-runner.pdf");

        byte[] pdf = Files.readAllBytes(inPdf.toPath());
        byte[] xml = Files.readAllBytes(inXml.toPath());

        PdfA3Service svc = new PdfA3Service();
        // Instantiate and inject the project's AttachmentService so we use the battle-tested
        // embedding path (we're running outside Spring in the runner).
        try {
            var attach = new stirling.software.SPDF.service.AttachmentService();
            java.lang.reflect.Field f = PdfA3Service.class.getDeclaredField("attachmentService");
            f.setAccessible(true);
            f.set(svc, attach);
        } catch (Exception ignored) {
        }

        // --- DEBUG: create a fresh PDDocument, attach the UBL using AttachmentService, and
        // inspect the intermediate PDF before running the full pipeline. This will tell us
        // whether the embedded-file objects are created correctly by AttachmentService.
        try (org.apache.pdfbox.pdmodel.PDDocument tmpDoc =
                new org.apache.pdfbox.pdmodel.PDDocument()) {
            try {
                var attachSvc = new stirling.software.SPDF.service.AttachmentService();
                org.springframework.web.multipart.MultipartFile mf =
                        new org.springframework.web.multipart.MultipartFile() {
                            @Override
                            public String getName() {
                                return "attachment";
                            }

                            @Override
                            public String getOriginalFilename() {
                                return inXml.getName();
                            }

                            @Override
                            public String getContentType() {
                                return "application/xml";
                            }

                            @Override
                            public boolean isEmpty() {
                                return xml == null || xml.length == 0;
                            }

                            @Override
                            public long getSize() {
                                return xml == null ? 0 : xml.length;
                            }

                            @Override
                            public byte[] getBytes() {
                                return xml == null ? new byte[0] : xml;
                            }

                            @Override
                            public java.io.InputStream getInputStream() {
                                return new java.io.ByteArrayInputStream(
                                        xml == null ? new byte[0] : xml);
                            }

                            @Override
                            public void transferTo(java.io.File dest)
                                    throws java.io.IOException, IllegalStateException {
                                try (java.io.FileOutputStream fos =
                                        new java.io.FileOutputStream(dest)) {
                                    fos.write(xml == null ? new byte[0] : xml);
                                }
                            }
                        };
                java.util.List<org.springframework.web.multipart.MultipartFile> list =
                        java.util.Collections.singletonList(mf);
                attachSvc.addAttachment(tmpDoc, list);

                try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                    tmpDoc.save(baos);
                    System.out.println("--- Intermediate (after attach) debug dump ---");
                    System.out.println(svc.debugDumpPdfaStructures(baos.toByteArray()));
                }
            } catch (Exception e) {
                System.out.println("Intermediate attach debug failed: " + e.getMessage());
            }
        }
        byte[] outPdf = svc.toPdfA3bWithUbl(pdf, xml, inXml.getName());

        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(outPdf);
        }

        System.out.println("Wrote: " + out.getAbsolutePath());
        System.out.println("--- Debug dump ---");
        System.out.println(svc.debugDumpPdfaStructures(outPdf));

        try {
            var strict = svc.validatePdfAStrict(outPdf);
            System.out.println("Strict valid: " + strict.isValid);
            System.out.println("Errors: " + strict.errors.size());
            for (var e : strict.errors) {
                System.out.println(e.code + " - " + e.details + " (page=" + e.page + ")");
            }
        } catch (Exception ex) {
            System.out.println("Strict validation failed: " + ex.getMessage());
        }
    }
}
