package stirling.software.SPDF.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.preflight.ValidationResult;
import org.apache.pdfbox.preflight.exception.SyntaxValidationException;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.xml.XmpSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PdfA3Service {
    @Autowired(required = false)
    private AttachmentService attachmentService;

    public byte[] toPdfA3bWithUbl(byte[] pdfBytes, byte[] ublXml, String ublName) throws Exception {
        try (PDDocument src = Loader.loadPDF(pdfBytes);
                PDDocument doc = new PDDocument()) {
            // Create attachments in the fresh document BEFORE importing pages. Creating the
            // embedded file objects early in the doc's COSDocument often yields correct
            // indirect objects when the final file is written.
            String fileName = (ublName == null || ublName.isBlank()) ? "invoice.xml" : ublName;
            PDComplexFileSpecification fs = attachUblViaAttachmentService(doc, ublXml, fileName);
            boolean usedAttachmentService = fs != null;
            if (!usedAttachmentService) {
                fs = attachUblManualRobust(doc, ublXml, fileName);
                // Rebuild Names & AF only for the manual fallback
                forceCleanEmbeddedFilesStructure(doc, fs, fileName);
                try {
                    sanitizeRemoveEmbeddedFileKeys(doc.getDocumentCatalog().getCOSObject());
                } catch (Exception ignored) {
                }
            } else {
                // when attachment service used, ensure AF root exists pointing to the file spec
                try {
                    org.apache.pdfbox.cos.COSArray af = new COSArray();
                    org.apache.pdfbox.cos.COSObject fos = ensureIndirect(doc, fs.getCOSObject());
                    if (fos != null) af.add(fos);
                    else af.add(fs.getCOSObject());
                    doc.getDocumentCatalog().getCOSObject().setItem(COSName.AF, af);
                } catch (Exception ignored) {
                }
            }

            // Import pages from source into the fresh document after attachments were added.
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.appendDocument(doc, src);

            // Force PDF 1.7 (PDF/A-3)
            // attempt to call setVersion(String) via reflection (PDFBox 3.x); otherwise fallback
            try {
                java.lang.reflect.Method m = doc.getClass().getMethod("setVersion", String.class);
                m.invoke(doc, "1.7");
            } catch (Exception ignore) {
                // Use canonical API when reflection isn't available
                try {
                    // PDDocument#setVersion expects a float in PDFBox
                    doc.setVersion(1.7f);
                } catch (Exception ex) {
                    // best-effort fallback to lower-level COSDocument API
                    try {
                        doc.getDocument().setVersion(1.7f);
                    } catch (Exception ignored2) {
                    }
                }
            }

            // ===== 1) OutputIntent (sRGB) =====
            try (InputStream icc = getClass().getResourceAsStream("/color/sRGB.icc")) {
                InputStream profile = icc;
                if (profile == null) {
                    java.awt.color.ICC_Profile jprof =
                            java.awt.color.ICC_Profile.getInstance(
                                    java.awt.color.ColorSpace.CS_sRGB);
                    profile = new java.io.ByteArrayInputStream(jprof.getData());
                }
                PDOutputIntent oi = new PDOutputIntent(doc, profile);
                oi.setInfo("sRGB IEC61966-2.1");
                oi.setOutputCondition("sRGB IEC61966-2.1");
                oi.setOutputConditionIdentifier("sRGB IEC61966-2.1");
                oi.setRegistryName("http://www.color.org");
                oi.getCOSObject().setName(COSName.S, "GTS_PDFA1");
                doc.getDocumentCatalog().addOutputIntent(oi);
            }

            // ===== 2) Doc Info (synchronized with XMP) =====
            java.time.Instant nowInstant = java.time.Instant.now();
            java.time.ZonedDateTime nowZdt =
                    java.time.ZonedDateTime.ofInstant(nowInstant, java.time.ZoneId.of("UTC"));
            java.util.GregorianCalendar nowCal = java.util.GregorianCalendar.from(nowZdt);
            org.apache.pdfbox.pdmodel.PDDocumentInformation info = doc.getDocumentInformation();
            if (info == null) info = new org.apache.pdfbox.pdmodel.PDDocumentInformation();
            info.setTitle(info.getTitle() == null ? "PDF/A-3 with UBL" : info.getTitle());
            info.setAuthor(info.getAuthor() == null ? "Stirling-PDF SRB" : info.getAuthor());
            info.setProducer("Stirling-PDF SRB Pack");
            info.setCreationDate(nowCal);
            info.setModificationDate(nowCal);
            doc.setDocumentInformation(info);

            // ===== 3) XMP: pdfaid + dc + xmp basic =====
            XMPMetadata xmp = XMPMetadata.createXMPMetadata();

            // Dublin Core
            DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
            dc.addCreator(info.getAuthor());
            if (info.getTitle() != null) dc.setTitle(info.getTitle());

            // PDF/A identification
            PDFAIdentificationSchema id = xmp.createAndAddPDFAIdentificationSchema();
            id.setPart(3);
            id.setConformance("B");

            // XMP Basic (create/modify date)
            var basic = xmp.createAndAddXMPBasicSchema();
            basic.setCreateDate(nowCal);
            basic.setModifyDate(nowCal);
            basic.setCreatorTool("Stirling-PDF SRB Pack");

            // PDF schema: Producer will be added into the serialized XMP packet below.

            // Serialize XMP. Build a minimal XMP packet that includes the PDF schema Producer
            // so Preflight can find it (some XMPBox versions don't expose PDFSchema helper).
            String producer =
                    info.getProducer() == null ? "Stirling-PDF SRB Pack" : info.getProducer();
            String title = info.getTitle() == null ? "PDF/A-3 with UBL" : info.getTitle();
            String author = info.getAuthor() == null ? "Stirling-PDF SRB" : info.getAuthor();
            // Prefer canonical XMP serialization via XmpSerializer so all schemas are declared
            try {
                // Ensure AdobePDFSchema is present and has Producer
                AdobePDFSchema pdfSchema = xmp.getAdobePDFSchema();
                if (pdfSchema == null) {
                    pdfSchema = xmp.createAndAddAdobePDFSchema();
                }
                pdfSchema.setProducer(producer);

                XMPBasicSchema xmpBasic = xmp.getXMPBasicSchema();
                if (xmpBasic == null) xmpBasic = xmp.createAndAddXMPBasicSchema();
                xmpBasic.setCreateDate(nowCal);
                xmpBasic.setModifyDate(nowCal);
                xmpBasic.setCreatorTool("Stirling-PDF SRB Pack");

                // Serialize using XmpSerializer
                ByteArrayOutputStream xmpBaos = new ByteArrayOutputStream();
                new XmpSerializer().serialize(xmp, xmpBaos, true);
                PDMetadata md = new PDMetadata(doc);
                md.importXMPMetadata(xmpBaos.toByteArray());

                // Let PDFBox handle the metadata stream and its indirectization. Ensure basic
                // stream keys are present on the underlying COSStream so Preflight sees /Type and
                // /Subtype.
                try {
                    org.apache.pdfbox.cos.COSStream mdStream =
                            (org.apache.pdfbox.cos.COSStream) md.getCOSObject();
                    mdStream.setItem(COSName.TYPE, COSName.getPDFName("Metadata"));
                    mdStream.setName(COSName.SUBTYPE, "XML");
                } catch (Exception ignore) {
                }
                doc.getDocumentCatalog().setMetadata(md);
                // Try to force the metadata COSStream to become an indirect object so Catalog
                // points to a COSObject rather than COSNull placeholders in some PDFBox builds.
                try {
                    org.apache.pdfbox.cos.COSObject metaObj =
                            ensureIndirect(doc, md.getCOSObject());
                    if (metaObj != null) {
                        doc.getDocumentCatalog().getCOSObject().setItem(COSName.METADATA, metaObj);
                    }
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                // Don't insert an empty PDMetadata (it may become COSNull).
                // Fail fast so the caller can fix XMP serialization issues.
                throw new IOException("XMP serialization failed", e);
            }

            // (attachments were created earlier, before pages were imported)

            // ===== 5) Pre-save sanity checks and Save fresh file (non-incremental) =====
            // EF sanity
            org.apache.pdfbox.cos.COSArray afArr =
                    (org.apache.pdfbox.cos.COSArray)
                            doc.getDocumentCatalog().getCOSObject().getDictionaryObject(COSName.AF);
            if (afArr == null || afArr.size() == 0 || isNullRef(afArr.getObject(0))) {
                throw new IOException("AF missing or null");
            }
            PDDocumentNameDictionary namesDict = doc.getDocumentCatalog().getNames();
            if (namesDict != null && namesDict.getEmbeddedFiles() != null) {
                var tree = namesDict.getEmbeddedFiles();
                var leaf = tree.getNames();
                if (leaf != null && leaf.containsKey(fileName)) {
                    var fsCheck = leaf.get(fileName);
                    var efDict =
                            (org.apache.pdfbox.cos.COSDictionary)
                                    fsCheck.getCOSObject().getDictionaryObject(COSName.EF);
                    if (efDict == null || isNullRef(efDict.getDictionaryObject(COSName.F))) {
                        throw new IOException("EF/F still null before save()");
                    }
                }
            }

            // XMP sanity
            var metaBase =
                    doc.getDocumentCatalog().getCOSObject().getDictionaryObject(COSName.METADATA);
            if (!(metaBase instanceof org.apache.pdfbox.cos.COSStream)
                    && !(metaBase instanceof org.apache.pdfbox.cos.COSObject
                            && !isNullRef(metaBase))) {
                throw new IOException("Metadata not set as indirect COSStream");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos); // full save by default (not incremental)
            return baos.toByteArray();
        }
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Heuristic validation: checks for presence of OutputIntent, XMP metadata and AF attachments.
     * This is a best-effort lightweight check used in the MVP. For strict PDF/A validation use
     * Apache Preflight externally.
     */
    public PdfAValidationResult validatePdfA(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            boolean hasOutputIntent = !doc.getDocumentCatalog().getOutputIntents().isEmpty();
            boolean hasXmp = doc.getDocumentCatalog().getMetadata() != null;
            COSArray af =
                    (COSArray)
                            doc.getDocumentCatalog()
                                    .getCOSObject()
                                    .getDictionaryObject(COSName.getPDFName("AF"));
            boolean hasAF = af != null && af.size() > 0;
            boolean valid = hasOutputIntent && hasXmp && hasAF;
            String details =
                    String.format("outputIntent=%b, xmp=%b, af=%b", hasOutputIntent, hasXmp, hasAF);
            return new PdfAValidationResult(valid, details);
        }
    }

    public static record PdfAValidationResult(boolean isValid, String details) {}

    /** Compact info about an embedded file. */
    public static final class AttachmentInfo {
        public final String name;
        public final Integer size;
        public final String subtype;
        public final String afRelationship; // e.g. Data, Source, Alternative

        public AttachmentInfo(String name, Integer size, String subtype, String afRelationship) {
            this.name = name;
            this.size = size;
            this.subtype = subtype;
            this.afRelationship = afRelationship;
        }
    }

    public List<AttachmentInfo> listAttachments(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            List<AttachmentInfo> out = new ArrayList<>();

            PDDocumentNameDictionary names = doc.getDocumentCatalog().getNames();
            if (names == null || names.getEmbeddedFiles() == null) {
                return out;
            }
            PDEmbeddedFilesNameTreeNode tree = names.getEmbeddedFiles();

            // Collect AF array object refs (if any) for potential cross-checking
            Set<org.apache.pdfbox.cos.COSBase> afSet = new HashSet<>();
            org.apache.pdfbox.cos.COSBase af =
                    doc.getDocumentCatalog()
                            .getCOSObject()
                            .getDictionaryObject(COSName.getPDFName("AF"));
            if (af instanceof org.apache.pdfbox.cos.COSArray arr) {
                for (int i = 0; i < arr.size(); i++) {
                    afSet.add(arr.getObject(i));
                }
            }

            // Walk the name tree
            collectEmbeddedFiles(tree, out, afSet);
            return out;
        }
    }

    @SuppressWarnings("unchecked")
    private void collectEmbeddedFiles(
            PDNameTreeNode<PDComplexFileSpecification> node,
            List<AttachmentInfo> out,
            Set<org.apache.pdfbox.cos.COSBase> afSet)
            throws IOException {
        Map<String, PDComplexFileSpecification> names = node.getNames();
        if (names != null) {
            for (Map.Entry<String, PDComplexFileSpecification> e : names.entrySet()) {
                PDComplexFileSpecification fs = e.getValue();
                PDEmbeddedFile ef = fs.getEmbeddedFile();
                Integer size = ef != null ? ef.getSize() : null;
                String subtype = ef != null ? ef.getSubtype() : null;

                // Prefer AFRelationship stored on the file spec
                String afr = null;
                org.apache.pdfbox.cos.COSBase afrObj =
                        fs.getCOSObject().getDictionaryObject(COSName.getPDFName("AFRelationship"));
                if (afrObj instanceof org.apache.pdfbox.cos.COSName n) {
                    afr = n.getName();
                } else if (afSet.contains(fs.getCOSObject())) {
                    afr = "Associated"; // fallback if present in AF array but no explicit
                    // relationship
                }

                out.add(new AttachmentInfo(e.getKey(), size, subtype, afr));
            }
        }
        List<PDNameTreeNode<PDComplexFileSpecification>> kids = node.getKids();
        if (kids != null) {
            for (PDNameTreeNode<PDComplexFileSpecification> kid : kids) {
                collectEmbeddedFiles(kid, out, afSet);
            }
        }
    }

    // --- Strict validation via Preflight (returns rich error list) ---
    public StrictResult validatePdfAStrict(byte[] pdf) throws IOException {
        // Try a flavour-aware static validate(src, flavour) if available via reflection.
        try {
            Class<?> byteSrcClass =
                    Class.forName("org.apache.pdfbox.preflight.utils.ByteArrayDataSource");
            Class<?> flavourClass = Class.forName("org.apache.pdfbox.preflight.PDFAFlavour");

            Object src = byteSrcClass.getConstructor(byte[].class).newInstance((Object) pdf);
            Object pdfa3b = flavourClass.getField("PDFA_3_B").get(null);

            try {
                java.lang.reflect.Method staticValidate =
                        PreflightParser.class.getMethod("validate", byteSrcClass, flavourClass);
                Object vrObj = staticValidate.invoke(null, src, pdfa3b);
                if (vrObj instanceof ValidationResult)
                    return toStrictResult((ValidationResult) vrObj);
            } catch (NoSuchMethodException ignore) {
                // static validate not present; fallback below
            }
        } catch (Throwable ignored) {
            // flavour-aware helpers not present or failed; fall back
        }

        // Fallback: write to temp file and use PreflightParser.validate(File)
        java.io.File tmp = java.io.File.createTempFile("preflight_", ".pdf");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
            fos.write(pdf);
        }
        try {
            ValidationResult vr = PreflightParser.validate(tmp);
            return toStrictResult(vr);
        } catch (SyntaxValidationException e) {
            return new StrictResult(
                    false,
                    List.of(new StrictError("SYNTAX", "Not a valid PDF: " + e.getMessage(), null)));
        } finally {
            try {
                tmp.delete();
            } catch (Exception ignored) {
            }
        }
    }

    private StrictResult toStrictResult(ValidationResult vr) {
        List<StrictError> errors = new ArrayList<>();
        if (vr.getErrorsList() != null) {
            for (Object o : vr.getErrorsList()) {
                String code = null;
                String details = null;
                Integer page = null;
                try {
                    java.lang.reflect.Method m =
                            findFirstMethod(
                                    o,
                                    new String[] {"getErrorCode", "getCode", "getId", "getName"});
                    if (m != null) code = String.valueOf(m.invoke(o));

                    m =
                            findFirstMethod(
                                    o,
                                    new String[] {
                                        "getDetails", "getMessage", "getDescription", "toString"
                                    });
                    if (m != null) {
                        Object val = m.invoke(o);
                        details = val != null ? String.valueOf(val) : null;
                    }

                    m =
                            findFirstMethod(
                                    o, new String[] {"getPage", "getPageNumber", "getPageIndex"});
                    if (m != null) {
                        Object pv = m.invoke(o);
                        if (pv instanceof Number) page = ((Number) pv).intValue();
                        else if (pv != null) {
                            try {
                                page = Integer.valueOf(String.valueOf(pv));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } catch (Exception e) {
                    if (details == null) details = e.getMessage();
                }

                if (code == null) code = String.valueOf(o);
                if (details == null) details = String.valueOf(o);
                errors.add(new StrictError(code, details, page));
            }
        }
        return new StrictResult(vr.isValid(), errors);
    }

    /** Compact DTOs so controller can return clean JSON. */
    public static final class StrictResult {
        public final boolean isValid;
        public final List<StrictError> errors;

        public StrictResult(boolean isValid, List<StrictError> errors) {
            this.isValid = isValid;
            this.errors = errors == null ? List.of() : errors;
        }

        public int getErrorCount() {
            return errors.size();
        }
    }

    /**
     * Diagnostic helper: Dumps Catalog, Names/EmbeddedFiles tree and root /AF array. Safe to expose
     * as a dev-only endpoint for debugging Preflight complaints about /EmbeddedFile.
     */
    public String debugDumpPdfaStructures(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            StringBuilder sb = new StringBuilder(4096);
            org.apache.pdfbox.cos.COSDictionary cat = doc.getDocumentCatalog().getCOSObject();

            sb.append("== Catalog ==\n");
            sb.append(cat).append("\n\n");

            // Names → EmbeddedFiles tree (keys only)
            PDDocumentNameDictionary names = doc.getDocumentCatalog().getNames();
            if (names == null) {
                sb.append("No /Names dictionary\n");
            } else {
                sb.append("== Names dictionary ==\n");
                org.apache.pdfbox.cos.COSDictionary namesDict = names.getCOSObject();
                sb.append(namesDict.keySet()).append("\n");
                PDEmbeddedFilesNameTreeNode tree = names.getEmbeddedFiles();
                if (tree == null) {
                    sb.append("No /EmbeddedFiles tree\n");
                } else {
                    sb.append("EmbeddedFiles tree (root COS):\n");
                    sb.append(tree.getCOSObject()).append("\n");
                    Map<String, PDComplexFileSpecification> leaf = tree.getNames();
                    if (leaf != null) {
                        for (var e : leaf.entrySet()) {
                            sb.append("  name: ").append(e.getKey()).append("\n");
                            var fs = e.getValue();
                            sb.append("  FileSpec COS: ").append(fs.getCOSObject()).append("\n");
                            sb.append("  EF COS: ")
                                    .append(fs.getCOSObject().getDictionaryObject(COSName.EF))
                                    .append("\n");
                            sb.append("  AFRelationship: ")
                                    .append(
                                            fs.getCOSObject()
                                                    .getDictionaryObject(COSName.AF_RELATIONSHIP))
                                    .append("\n\n");
                        }
                    }
                }
            }

            // Root AF array
            sb.append("== Root /AF array ==\n");
            org.apache.pdfbox.cos.COSBase af = cat.getDictionaryObject(COSName.AF);
            sb.append(af == null ? "No /AF\n" : af.toString()).append("\n");

            // Quick scan for singular /EmbeddedFile keys anywhere under Catalog
            sb.append("\n== Scan for singular /EmbeddedFile keys under Catalog ==\n");
            // use a visited set and depth limit to avoid infinite recursion on self-referential COS
            scanForEmbeddedFileKey(cat, sb, "Catalog", new HashSet<>(), 0);

            return sb.toString();
        }
    }

    private void scanForEmbeddedFileKey(
            org.apache.pdfbox.cos.COSBase node, StringBuilder sb, String path) {
        // fallback simple wrapper (shouldn't be used anymore) that initializes helpers
        scanForEmbeddedFileKey(node, sb, path, new HashSet<>(), 0);
    }

    private void scanForEmbeddedFileKey(
            org.apache.pdfbox.cos.COSBase node,
            StringBuilder sb,
            String path,
            Set<org.apache.pdfbox.cos.COSBase> visited,
            int depth) {
        final int MAX_DEPTH = 512;
        if (node == null) return;
        if (depth > MAX_DEPTH) {
            sb.append("Max depth reached at ").append(path).append("\n");
            return;
        }
        // avoid revisiting shared/recursive COS objects
        if (visited.contains(node)) return;
        visited.add(node);

        if (node instanceof org.apache.pdfbox.cos.COSDictionary dict) {
            if (dict.containsKey(org.apache.pdfbox.cos.COSName.getPDFName("EmbeddedFile"))) {
                sb.append("Found /EmbeddedFile at ").append(path).append("\n");
                try {
                    sb.append("  -> ")
                            .append(
                                    dict.getDictionaryObject(
                                            org.apache.pdfbox.cos.COSName.getPDFName(
                                                    "EmbeddedFile")))
                            .append("\n");
                } catch (Exception e) {
                    sb.append("  -> (error reading object): " + e.getMessage() + "\n");
                }
            }
            for (org.apache.pdfbox.cos.COSName key : dict.keySet()) {
                try {
                    scanForEmbeddedFileKey(
                            dict.getDictionaryObject(key),
                            sb,
                            path + "/" + key.getName(),
                            visited,
                            depth + 1);
                } catch (Exception ignored) {
                }
            }
        } else if (node instanceof org.apache.pdfbox.cos.COSArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                try {
                    scanForEmbeddedFileKey(
                            arr.getObject(i), sb, path + "[" + i + "]", visited, depth + 1);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static final class StrictError {
        public final String code;
        public final String details;
        public final Integer page; // may be null

        public StrictError(String code, String details, Integer page) {
            this.code = code;
            this.details = details;
            this.page = page;
        }
    }

    private static java.lang.reflect.Method findFirstMethod(Object target, String[] names) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        for (String n : names) {
            try {
                java.lang.reflect.Method m = c.getMethod(n);
                if (m != null) return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    // === Helpers: check COSNull behind COSObject ===
    private static boolean isNullRef(org.apache.pdfbox.cos.COSBase base) {
        if (base instanceof org.apache.pdfbox.cos.COSObject obj) {
            return obj.getObject() instanceof org.apache.pdfbox.cos.COSNull;
        }
        return base instanceof org.apache.pdfbox.cos.COSNull;
    }

    // Reflection-based ensureIndirect: try to call COSDocument.addObject(COSBase) if available
    // Returns a COSObject when successful, otherwise null.
    private static org.apache.pdfbox.cos.COSObject ensureIndirect(
            PDDocument doc, org.apache.pdfbox.cos.COSBase base) {
        try {
            Method m =
                    doc.getDocument()
                            .getClass()
                            .getMethod("addObject", org.apache.pdfbox.cos.COSBase.class);
            Object ret = m.invoke(doc.getDocument(), base);
            if (ret instanceof org.apache.pdfbox.cos.COSObject obj) return obj;
        } catch (NoSuchMethodException ignore) {
            // method not present in this PDFBox build
        } catch (Exception ignored) {
        }
        return null;
    }

    // Cross-version helper to set UF (Unicode filename) on a FileSpecification.
    private static void setUF(PDComplexFileSpecification fs, String fileName) {
        try {
            java.lang.reflect.Method m =
                    PDComplexFileSpecification.class.getMethod("setUnicodeFile", String.class);
            m.invoke(fs, fileName);
        } catch (Exception ignore) {
            // Fallback: set UF directly in COS to support older/newer PDFBox API differences
            fs.getCOSObject().setString(org.apache.pdfbox.cos.COSName.getPDFName("UF"), fileName);
        }
    }

    /**
     * Recursively remove singular /EmbeddedFile keys from COS dictionaries to avoid Preflight
     * errors.
     */
    private static void sanitizeRemoveEmbeddedFileKeys(org.apache.pdfbox.cos.COSBase base) {
        // Wrapper that prevents infinite recursion on shared/recursive COS structures
        sanitizeRemoveEmbeddedFileKeys(base, new HashSet<>(), 0);
    }

    private static void sanitizeRemoveEmbeddedFileKeys(
            org.apache.pdfbox.cos.COSBase base,
            Set<org.apache.pdfbox.cos.COSBase> visited,
            int depth) {
        final int MAX_DEPTH = 512;
        if (base == null) return;
        if (depth > MAX_DEPTH) return;
        if (visited.contains(base)) return;
        visited.add(base);

        if (base instanceof org.apache.pdfbox.cos.COSDictionary dict) {
            // remove direct singular key if present
            try {
                dict.removeItem(org.apache.pdfbox.cos.COSName.getPDFName("EmbeddedFile"));
            } catch (Exception ignore) {
            }
            // iterate child entries
            for (org.apache.pdfbox.cos.COSName name : dict.keySet()) {
                try {
                    org.apache.pdfbox.cos.COSBase child = dict.getDictionaryObject(name);
                    sanitizeRemoveEmbeddedFileKeys(child, visited, depth + 1);
                } catch (Exception ignored) {
                }
            }
        } else if (base instanceof org.apache.pdfbox.cos.COSArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                try {
                    sanitizeRemoveEmbeddedFileKeys(arr.getObject(i), visited, depth + 1);
                } catch (Exception ignored) {
                }
            }
        }
    }

    // --- Attachment bridge + robust manual fallback ---
    private PDComplexFileSpecification attachUblViaAttachmentService(
            PDDocument doc, byte[] xml, String fileName) {
        if (attachmentService == null) return null;
        try {
            for (Method m : attachmentService.getClass().getMethods()) {
                if (!"addAttachment".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                // Signature: addAttachment(PDDocument, InputStream, String, String, String, String)
                if (p.length == 6
                        && PDDocument.class.isAssignableFrom(p[0])
                        && java.io.InputStream.class.isAssignableFrom(p[1])
                        && p[2] == String.class
                        && p[3] == String.class
                        && p[4] == String.class
                        && p[5] == String.class) {
                    Object ret =
                            m.invoke(
                                    attachmentService,
                                    doc,
                                    new ByteArrayInputStream(xml),
                                    fileName,
                                    "application/xml",
                                    "Data",
                                    "Embedded UBL: " + fileName);
                    if (ret instanceof PDComplexFileSpecification fs) return fs;
                }
                // Signature: addAttachment(PDDocument, byte[], String, String, String)
                if (p.length == 5
                        && PDDocument.class.isAssignableFrom(p[0])
                        && p[1] == byte[].class
                        && p[2] == String.class
                        && p[3] == String.class
                        && p[4] == String.class) {
                    Object ret =
                            m.invoke(
                                    attachmentService,
                                    doc,
                                    xml,
                                    fileName,
                                    "application/xml",
                                    "Data");
                    if (ret instanceof PDComplexFileSpecification fs) return fs;
                }
                // Signature: addAttachment(PDDocument, List<MultipartFile>) -> modifies document
                if (p.length == 2
                        && PDDocument.class.isAssignableFrom(p[0])
                        && java.util.List.class.isAssignableFrom(p[1])) {
                    // build a simple MultipartFile wrapper for the XML
                    org.springframework.web.multipart.MultipartFile mf =
                            new org.springframework.web.multipart.MultipartFile() {
                                @Override
                                public String getName() {
                                    return "attachment";
                                }

                                @Override
                                public String getOriginalFilename() {
                                    return fileName;
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
                    Object ret = m.invoke(attachmentService, doc, list);
                    // After invocation, the document should contain the embedded file; try to fetch
                    // it
                    try {
                        PDDocumentNameDictionary names = doc.getDocumentCatalog().getNames();
                        if (names != null && names.getEmbeddedFiles() != null) {
                            Map<String, PDComplexFileSpecification> namesMap =
                                    names.getEmbeddedFiles().getNames();
                            if (namesMap != null && namesMap.containsKey(fileName)) {
                                return namesMap.get(fileName);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    // if the method returned a FileSpec directly, handle that too
                    if (ret instanceof PDComplexFileSpecification fs) return fs;
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private PDComplexFileSpecification attachUblManualRobust(
            PDDocument doc, byte[] xml, String fileName) throws Exception {
        // 1) Create EF from doc+stream
        java.util.GregorianCalendar nowCal =
                java.util.GregorianCalendar.from(
                        java.time.ZonedDateTime.ofInstant(
                                java.time.Instant.now(), java.time.ZoneId.of("UTC")));

        // Create PDEmbeddedFile via PDDocument+InputStream (project-tested pattern). This
        // lets PDFBox attach and (usually) indirectize the stream correctly.
        PDEmbeddedFile ef = new PDEmbeddedFile(doc, new ByteArrayInputStream(xml));
        ef.setSubtype("application/xml");
        ef.setSize(xml.length);
        ef.setModDate(nowCal);

        // 2) FileSpec + UF + AFRelationship
        PDComplexFileSpecification fs = new PDComplexFileSpecification();
        fs.setFile(fileName);
        setUF(fs, fileName);
        fs.getCOSObject().setName(COSName.AF_RELATIONSHIP, "Data");
        fs.getCOSObject().setString(COSName.DESC, "Embedded UBL: " + fileName);

        // 3) Let the high-level API attach the embedded file and (if available) the unicode entry
        // Attach using high-level API; prefer setEmbeddedFile(InputStream) pattern, and also
        // try cross-version setEmbeddedFileUnicode for unicode entry.
        fs.setEmbeddedFile(ef);
        try {
            java.lang.reflect.Method m =
                    PDComplexFileSpecification.class.getMethod(
                            "setEmbeddedFileUnicode", PDEmbeddedFile.class);
            if (m != null) m.invoke(fs, ef);
        } catch (NoSuchMethodException ignore) {
        }

        // 4) Sanity: ensure EF/F references a non-null stream; if not, create a fresh COSStream and
        // reattach
        org.apache.pdfbox.cos.COSDictionary efDictCheck =
                (org.apache.pdfbox.cos.COSDictionary)
                        fs.getCOSObject().getDictionaryObject(COSName.EF);
        if (efDictCheck == null) throw new IOException("EF dict missing");
        org.apache.pdfbox.cos.COSBase fObj = efDictCheck.getDictionaryObject(COSName.F);
        if (fObj == null || isNullRef(fObj)) {
            // Build a fresh COSStream and PDEmbeddedFile and attach it
            org.apache.pdfbox.cos.COSStream stream = doc.getDocument().createCOSStream();
            try (java.io.OutputStream os = stream.createOutputStream()) {
                os.write(xml);
            }
            PDEmbeddedFile ef2 = new PDEmbeddedFile(stream);
            ef2.setSubtype("application/xml");
            ef2.setSize(xml.length);
            ef2.setModDate(nowCal);
            // try to ensure indirect COSObject via reflection helper
            org.apache.pdfbox.cos.COSObject forced = ensureIndirect(doc, stream);
            if (forced != null) {
                efDictCheck.setItem(COSName.F, forced);
            } else {
                efDictCheck.setItem(COSName.F, stream);
            }
            fs.getCOSObject().setItem(COSName.EF, efDictCheck);
            try {
                fs.setEmbeddedFile(ef2);
            } catch (Exception ignored2) {
            }
            // final check
            org.apache.pdfbox.cos.COSBase check = efDictCheck.getDictionaryObject(COSName.F);
            if (check == null || isNullRef(check)) {
                throw new IOException("EF/F still null after force re-embed");
            }
        }
        return fs;
    }

    private void forceCleanEmbeddedFilesStructure(
            PDDocument doc, PDComplexFileSpecification fs, String fileName) {
        PDDocumentCatalog catalog = doc.getDocumentCatalog();
        // remove any existing /Names root to avoid stale COSNull wrapper
        catalog.getCOSObject().removeItem(COSName.NAMES);
        PDDocumentNameDictionary names = new PDDocumentNameDictionary(catalog);
        PDEmbeddedFilesNameTreeNode tree = new PDEmbeddedFilesNameTreeNode();
        Map<String, PDComplexFileSpecification> map = new java.util.TreeMap<>();
        map.put(fileName, fs);
        tree.setNames(map);
        names.setEmbeddedFiles(tree);
        doc.getDocumentCatalog().setNames(names);

        org.apache.pdfbox.cos.COSDictionary cat = doc.getDocumentCatalog().getCOSObject();
        // Fresh AF: replace root /AF with a new array containing our FileSpec COS object
        COSArray af = new COSArray();
        try {
            org.apache.pdfbox.cos.COSObject fos = ensureIndirect(doc, fs.getCOSObject());
            if (fos != null) af.add(fos);
            else af.add(fs.getCOSObject());
        } catch (Exception ignored) {
            af.add(fs.getCOSObject());
        }
        cat.setItem(COSName.AF, af);

        org.apache.pdfbox.cos.COSDictionary namesDict =
                (org.apache.pdfbox.cos.COSDictionary) cat.getDictionaryObject(COSName.NAMES);
        if (namesDict != null) {
            namesDict.removeItem(org.apache.pdfbox.cos.COSName.getPDFName("EmbeddedFile"));
        }

        // Sanity: ensure Names->EmbeddedFiles->Names second entry is not COSNull
        org.apache.pdfbox.cos.COSDictionary namesCos = names.getCOSObject();
        org.apache.pdfbox.cos.COSDictionary efRoot =
                (org.apache.pdfbox.cos.COSDictionary)
                        namesCos.getDictionaryObject(COSName.EMBEDDED_FILES);
        if (efRoot != null) {
            org.apache.pdfbox.cos.COSArray namesArray =
                    (org.apache.pdfbox.cos.COSArray) efRoot.getDictionaryObject(COSName.NAMES);
            if (namesArray != null && namesArray.size() >= 2) {
                org.apache.pdfbox.cos.COSBase second = namesArray.getObject(1);
                if (isNullRef(second)) {
                    namesArray.set(1, fs.getCOSObject());
                }
            }
        }
    }
}
