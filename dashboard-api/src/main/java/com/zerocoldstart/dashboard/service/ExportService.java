package com.zerocoldstart.dashboard.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.zerocoldstart.dashboard.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final MetricsService metricsService;
    private final CostSimulationService costSimulationService;
    private final RegressionService regressionService;

    private static final DeviceRgb ACCENT_COLOR = new DeviceRgb(0, 120, 212);
    private static final DeviceRgb SUCCESS_COLOR = new DeviceRgb(16, 124, 16);
    private static final DeviceRgb HEADER_BG = new DeviceRgb(240, 244, 248);

    public byte[] exportPdf(ExportRequest request) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        var boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        var regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        var monoFont = PdfFontFactory.createFont(StandardFonts.COURIER);

        // ── Titre principal ──
        doc.add(new Paragraph("Benchmark Report — Zero Cold Start")
                .setFont(boldFont).setFontSize(22)
                .setFontColor(ACCENT_COLOR)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4));

        doc.add(new Paragraph("Spring Boot 3 + GraalVM Native Image vs JVM")
                .setFont(regularFont).setFontSize(13)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2));

        doc.add(new Paragraph("Genere le : " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                .setFont(regularFont).setFontSize(10)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20));

        // ── Section comparaison ──
        if (request.isIncludeComparison()) {
            addSectionTitle(doc, boldFont, "1. Comparaison JVM vs Native Image");
            Map<String, Object> comparison = metricsService.getComparisonSummary();
            addComparisonTable(doc, boldFont, regularFont, comparison);
        }

        // ── Section simulation de cout ──
        if (request.isIncludeCostSimulation()) {
            addSectionTitle(doc, boldFont, "2. Simulation de cout cloud");

            long invocations = request.getInvocationsPerMonth() > 0
                    ? request.getInvocationsPerMonth() : 1_000_000;
            double coldRatio = request.getColdStartRatioPercent() > 0
                    ? request.getColdStartRatioPercent() : 5.0;

            doc.add(new Paragraph(String.format(
                    "Parametres : %,d invocations/mois, %.0f%% de cold start",
                    invocations, coldRatio))
                    .setFont(regularFont).setFontSize(10)
                    .setFontColor(ColorConstants.GRAY)
                    .setMarginBottom(8));

            List<CostEstimate> costs = costSimulationService.simulate(invocations, coldRatio);
            addCostTable(doc, boldFont, regularFont, costs);
        }

        // ── Section regression ──
        if (request.isIncludeRegression()) {
            addSectionTitle(doc, boldFont, "3. Alertes de regression detectees");
            List<RegressionAlert> alerts = regressionService.checkRegression("native");
            if (alerts.isEmpty()) {
                doc.add(new Paragraph("Aucune regression detectee sur le dernier run.")
                        .setFont(regularFont).setFontSize(11)
                        .setFontColor(SUCCESS_COLOR).setMarginBottom(12));
            } else {
                addRegressionTable(doc, boldFont, regularFont, alerts);
            }
        }

        // ── Footer ──
        doc.add(new Paragraph("\n"));
        doc.add(new Paragraph("Zero Cold Start Dashboard — Master 2 Genie Logiciel")
                .setFont(regularFont).setFontSize(9)
                .setFontColor(ColorConstants.LIGHT_GRAY)
                .setTextAlignment(TextAlignment.CENTER));

        doc.close();
        return baos.toByteArray();
    }

    public String exportLatex(ExportRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("\\documentclass[12pt,a4paper]{article}\n");
        sb.append("\\usepackage[utf8]{inputenc}\n");
        sb.append("\\usepackage{booktabs,longtable,xcolor,geometry}\n");
        sb.append("\\geometry{margin=2.5cm}\n");
        sb.append("\\definecolor{accentblue}{RGB}{0,120,212}\n");
        sb.append("\\definecolor{successgreen}{RGB}{16,124,16}\n\n");
        sb.append("\\begin{document}\n\n");

        sb.append("\\begin{center}\n");
        sb.append("{\\LARGE\\bfseries\\color{accentblue} Benchmark Results --- Zero Cold Start}\\\\[0.5em]\n");
        sb.append("{\\large Spring Boot 3 + GraalVM Native Image vs JVM}\\\\[0.3em]\n");
        sb.append("{\\small Generate le : ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append("}\n");
        sb.append("\\end{center}\n\n");
        sb.append("\\vspace{1em}\n\n");

        if (request.isIncludeComparison()) {
            sb.append("\\section{Comparaison JVM vs Native Image}\n\n");
            Map<String, Object> comparison = metricsService.getComparisonSummary();
            sb.append("\\begin{longtable}{lrrrr}\n");
            sb.append("\\toprule\n");
            sb.append("\\textbf{Service} & \\textbf{JVM Cold Start} & \\textbf{Native Cold Start} ");
            sb.append("& \\textbf{Gain x} & \\textbf{Gain Mem \\%} \\\\\n");
            sb.append("\\midrule\n");
            sb.append("\\endhead\n");

            for (Map.Entry<String, Object> entry : comparison.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) entry.getValue();
                if (data.isEmpty()) continue;
                sb.append(String.format("%s & %.1fs & {\\color{successgreen}%.2fs} & x%.1f & %d\\%% \\\\\n",
                        entry.getKey(),
                        ((Number) data.getOrDefault("jvmColdStartS", 0)).doubleValue(),
                        ((Number) data.getOrDefault("nativeColdStartS", 0)).doubleValue(),
                        ((Number) data.getOrDefault("coldStartGainX", 0)).doubleValue(),
                        ((Number) data.getOrDefault("memoryGainPct", 0)).intValue()));
            }
            sb.append("\\bottomrule\n");
            sb.append("\\end{longtable}\n\n");
        }

        if (request.isIncludeCostSimulation()) {
            long inv = request.getInvocationsPerMonth() > 0 ? request.getInvocationsPerMonth() : 1_000_000;
            double cr = request.getColdStartRatioPercent() > 0 ? request.getColdStartRatioPercent() : 5.0;
            sb.append("\\section{Simulation de cout cloud}\n\n");
            sb.append(String.format("Parametres : %,d invocations/mois, %.0f\\%% de cold start.\n\n", inv, cr));

            sb.append("\\begin{longtable}{llrr}\n");
            sb.append("\\toprule\n");
            sb.append("\\textbf{Provider} & \\textbf{Mode} & \\textbf{Cout total USD} & \\textbf{Overhead cold start} \\\\\n");
            sb.append("\\midrule\n\\endhead\n");

            Map<String, Double> totals = new java.util.LinkedHashMap<>();
            List<CostEstimate> costs = costSimulationService.simulate(inv, cr);
            for (CostEstimate c : costs) {
                String key = c.getProvider() + "|" + c.getMode();
                totals.merge(key, c.getTotalCostUsd(), Double::sum);
            }
            for (Map.Entry<String, Double> e : totals.entrySet()) {
                String[] parts = e.getKey().split("\\|");
                String color = parts[1].equals("NATIVE") ? "successgreen" : "black";
                sb.append(String.format("%s & {\\color{%s}%s} & \\$%.2f & --- \\\\\n",
                        parts[0], color, parts[1], e.getValue()));
            }
            sb.append("\\bottomrule\n\\end{longtable}\n\n");
        }

        sb.append("\\end{document}\n");
        return sb.toString();
    }

    private void addSectionTitle(Document doc, com.itextpdf.kernel.font.PdfFont font, String title) {
        doc.add(new Paragraph(title)
                .setFont(font).setFontSize(14)
                .setFontColor(ACCENT_COLOR)
                .setMarginTop(16).setMarginBottom(8));
    }

    private void addComparisonTable(Document doc, com.itextpdf.kernel.font.PdfFont bold,
                                     com.itextpdf.kernel.font.PdfFont regular,
                                     Map<String, Object> comparison) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 15, 15, 12, 12, 16}))
                .useAllAvailableWidth();

        addHeaderCell(table, bold, "Service");
        addHeaderCell(table, bold, "JVM Cold Start");
        addHeaderCell(table, bold, "Native Cold Start");
        addHeaderCell(table, bold, "Gain x");
        addHeaderCell(table, bold, "JVM Mem");
        addHeaderCell(table, bold, "Gain Mem %");

        for (Map.Entry<String, Object> entry : comparison.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) entry.getValue();
            if (data.isEmpty()) continue;

            table.addCell(new Cell().add(new Paragraph(entry.getKey()).setFont(regular).setFontSize(10)));
            table.addCell(new Cell().add(new Paragraph(
                    String.format("%.1fs", ((Number)data.getOrDefault("jvmColdStartS",0)).doubleValue()))
                    .setFont(regular).setFontSize(10)));
            table.addCell(new Cell().add(new Paragraph(
                    String.format("%.2fs", ((Number)data.getOrDefault("nativeColdStartS",0)).doubleValue()))
                    .setFont(bold).setFontSize(10).setFontColor(SUCCESS_COLOR)));
            table.addCell(new Cell().add(new Paragraph(
                    String.format("x%.1f", ((Number)data.getOrDefault("coldStartGainX",0)).doubleValue()))
                    .setFont(bold).setFontSize(10).setFontColor(ACCENT_COLOR)));
            table.addCell(new Cell().add(new Paragraph(
                    String.format("%.0f Mi", ((Number)data.getOrDefault("jvmMemoryMi",0)).doubleValue()))
                    .setFont(regular).setFontSize(10)));
            table.addCell(new Cell().add(new Paragraph(
                    data.getOrDefault("memoryGainPct",0) + "%")
                    .setFont(bold).setFontSize(10).setFontColor(SUCCESS_COLOR)));
        }

        doc.add(table);
        doc.add(new Paragraph("\n"));
    }

    private void addCostTable(Document doc, com.itextpdf.kernel.font.PdfFont bold,
                               com.itextpdf.kernel.font.PdfFont regular, List<CostEstimate> costs) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 20, 15, 20, 15}))
                .useAllAvailableWidth();

        addHeaderCell(table, bold, "Service");
        addHeaderCell(table, bold, "Provider");
        addHeaderCell(table, bold, "Mode");
        addHeaderCell(table, bold, "Cout total USD");
        addHeaderCell(table, bold, "Overhead cold");

        for (CostEstimate c : costs) {
            boolean isNative = c.getMode().equals("NATIVE");
            table.addCell(new Cell().add(new Paragraph(c.getService()).setFont(regular).setFontSize(9)));
            table.addCell(new Cell().add(new Paragraph(c.getProvider()).setFont(regular).setFontSize(9)));
            table.addCell(new Cell().add(new Paragraph(c.getMode())
                    .setFont(bold).setFontSize(9)
                    .setFontColor(isNative ? SUCCESS_COLOR : ColorConstants.BLACK)));
            table.addCell(new Cell().add(new Paragraph(String.format("$%.2f", c.getTotalCostUsd()))
                    .setFont(bold).setFontSize(9)
                    .setFontColor(isNative ? SUCCESS_COLOR : ColorConstants.BLACK)));
            table.addCell(new Cell().add(new Paragraph(String.format("$%.2f", c.getColdStartOverheadCostUsd()))
                    .setFont(regular).setFontSize(9)));
        }

        doc.add(table);
        doc.add(new Paragraph("\n"));
    }

    private void addRegressionTable(Document doc, com.itextpdf.kernel.font.PdfFont bold,
                                     com.itextpdf.kernel.font.PdfFont regular,
                                     List<RegressionAlert> alerts) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{25, 15, 15, 15, 15, 15}))
                .useAllAvailableWidth();

        addHeaderCell(table, bold, "Service");
        addHeaderCell(table, bold, "Metrique");
        addHeaderCell(table, bold, "Precedent");
        addHeaderCell(table, bold, "Actuel");
        addHeaderCell(table, bold, "Variation %");
        addHeaderCell(table, bold, "Statut");

        for (RegressionAlert a : alerts) {
            boolean reg = a.isRegression();
            DeviceRgb color = reg ? new DeviceRgb(196, 43, 28) : SUCCESS_COLOR;
            table.addCell(new Cell().add(new Paragraph(a.getService()).setFont(regular).setFontSize(9)));
            table.addCell(new Cell().add(new Paragraph(a.getMetric()).setFont(regular).setFontSize(9)));
            table.addCell(new Cell().add(new Paragraph(String.format("%.2f", a.getPreviousValue())).setFont(regular).setFontSize(9)));
            table.addCell(new Cell().add(new Paragraph(String.format("%.2f", a.getCurrentValue())).setFont(regular).setFontSize(9)));
            table.addCell(new Cell().add(new Paragraph(String.format("%+.1f%%", a.getChangePercent()))
                    .setFont(bold).setFontSize(9).setFontColor(color)));
            table.addCell(new Cell().add(new Paragraph(reg ? "REGRESSION" : "AMELIORATION")
                    .setFont(bold).setFontSize(9).setFontColor(color)));
        }

        doc.add(table);
        doc.add(new Paragraph("\n"));
    }

    private void addHeaderCell(Table table, com.itextpdf.kernel.font.PdfFont font, String text) {
        table.addHeaderCell(new Cell()
                .setBackgroundColor(HEADER_BG)
                .add(new Paragraph(text).setFont(font).setFontSize(10).setFontColor(ACCENT_COLOR)));
    }
}
