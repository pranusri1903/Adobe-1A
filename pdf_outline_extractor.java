package com.pdfextractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PDFOutlineExtractor {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Common heading patterns in multiple languages
    private static final Pattern[] HEADING_PATTERNS = {
        // English patterns
        Pattern.compile("^\\s*(\\d+\\.?\\s+[A-Z][^\\n]{5,100})\\s*$", Pattern.MULTILINE),
        Pattern.compile("^\\s*(\\d+\\.\\d+\\.?\\s+[A-Z][^\\n]{5,100})\\s*$", Pattern.MULTILINE),
        Pattern.compile("^\\s*(\\d+\\.\\d+\\.\\d+\\.?\\s+[A-Z][^\\n]{5,100})\\s*$", Pattern.MULTILINE),
        
        // Chapter/Section patterns
        Pattern.compile("^\\s*(Chapter\\s+\\d+[:.\\s]+[A-Z][^\\n]{5,100})\\s*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE),
        Pattern.compile("^\\s*(Section\\s+\\d+[:.\\s]+[A-Z][^\\n]{5,100})\\s*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE),
        Pattern.compile("^\\s*(Part\\s+\\d+[:.\\s]+[A-Z][^\\n]{5,100})\\s*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE),
        
        // All caps patterns
        Pattern.compile("^\\s*([A-Z][A-Z\\s]{10,80})\\s*$", Pattern.MULTILINE),
        
        // Japanese patterns (Kanji, Hiragana, Katakana)
        Pattern.compile("^\\s*(第?[０-９0-9一二三四五六七八九十百千万]+[章節項部編]\\s*[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF]{3,50})\\s*$", Pattern.MULTILINE),
        Pattern.compile("^\\s*([０-９0-9]+[．.][０-９0-9]*\\s*[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF]{3,50})\\s*$", Pattern.MULTILINE),
        
        // Bold/emphasized text patterns (common in PDFs)
        Pattern.compile("^\\s*([A-Z][A-Za-z\\s]{8,60}[A-Za-z])\\s*$", Pattern.MULTILINE)
    };
    
    // Title detection patterns
    private static final Pattern[] TITLE_PATTERNS = {
        Pattern.compile("^\\s*([A-Z][A-Za-z\\s]{10,80}[A-Za-z])\\s*$", Pattern.MULTILINE),
        Pattern.compile("^\\s*([\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF]{5,80})\\s*$", Pattern.MULTILINE), // Japanese
        Pattern.compile("^\\s*([A-Z][A-Z\\s]{15,80})\\s*$", Pattern.MULTILINE) // All caps
    };
    
    public static void main(String[] args) {
        String inputDir = "/app/input";
        String outputDir = "/app/output";
        
        File inputDirectory = new File(inputDir);
        File outputDirectory = new File(outputDir);
        
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        
        if (!inputDirectory.exists() || !inputDirectory.isDirectory()) {
            System.err.println("Input directory not found: " + inputDir);
            return;
        }
        
        File[] pdfFiles = inputDirectory.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".pdf"));
        
        if (pdfFiles == null || pdfFiles.length == 0) {
            System.out.println("No PDF files found in input directory");
            return;
        }
        
        for (File pdfFile : pdfFiles) {
            try {
                System.out.println("Processing: " + pdfFile.getName());
                
                DocumentOutline outline = extractOutline(pdfFile);
                
                String outputFileName = pdfFile.getName().replaceAll("\\.pdf$", ".json");
                File outputFile = new File(outputDirectory, outputFileName);
                
                writeOutlineToJson(outline, outputFile);
                
                System.out.println("Completed: " + outputFileName);
                
            } catch (Exception e) {
                System.err.println("Error processing " + pdfFile.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private static DocumentOutline extractOutline(File pdfFile) throws IOException {
        try (PDDocument document = PDDocument.load(new FileInputStream(pdfFile))) {
            
            List<PageContent> pageContents = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            
            // Extract text from each page
            for (int pageNum = 1; pageNum <= Math.min(document.getNumberOfPages(), 50); pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document);
                pageContents.add(new PageContent(pageNum, pageText));
            }
            
            // Extract title from first few pages
            String title = extractTitle(pageContents);
            
            // Extract headings
            List<Heading> headings = extractHeadings(pageContents);
            
            return new DocumentOutline(title, headings);
        }
    }
    
    private static String extractTitle(List<PageContent> pageContents) {
        // Look for title in first 3 pages
        for (int i = 0; i < Math.min(3, pageContents.size()); i++) {
            String pageText = pageContents.get(i).text;
            String[] lines = pageText.split("\\n");
            
            // Look for prominent text at the beginning of the document
            for (String line : lines) {
                line = line.trim();
                if (line.length() > 10 && line.length() < 100) {
                    // Check if it looks like a title
                    for (Pattern pattern : TITLE_PATTERNS) {
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.matches()) {
                            String candidateTitle = matcher.group(1).trim();
                            if (candidateTitle.length() > 10 && !isLikelyHeader(candidateTitle)) {
                                return candidateTitle;
                            }
                        }
                    }
                }
            }
        }
        
        // Fallback: use filename without extension
        return "Document";
    }
    
    private static boolean isLikelyHeader(String text) {
        // Check if text looks more like a heading than a title
        return text.matches("^\\d+\\..*") || 
               text.toLowerCase().startsWith("chapter") ||
               text.toLowerCase().startsWith("section") ||
               text.toLowerCase().startsWith("part");
    }
    
    private static List<Heading> extractHeadings(List<PageContent> pageContents) {
        List<Heading> headings = new ArrayList<>();
        
        for (PageContent pageContent : pageContents) {
            String pageText = pageContent.text;
            int pageNum = pageContent.pageNumber;
            
            // Extract headings using patterns
            for (Pattern pattern : HEADING_PATTERNS) {
                Matcher matcher = pattern.matcher(pageText);
                while (matcher.find()) {
                    String headingText = matcher.group(1).trim();
                    
                    if (headingText.length() > 5 && headingText.length() < 150) {
                        String level = determineHeadingLevel(headingText);
                        
                        // Avoid duplicates
                        boolean isDuplicate = headings.stream()
                            .anyMatch(h -> h.text.equals(headingText) && 
                                     Math.abs(h.page - pageNum) <= 1);
                        
                        if (!isDuplicate) {
                            headings.add(new Heading(level, headingText, pageNum));
                        }
                    }
                }
            }
        }
        
        // Sort by page number and filter/clean results
        headings.sort(Comparator.comparingInt(h -> h.page));
        
        return filterAndCleanHeadings(headings);
    }
    
    private static String determineHeadingLevel(String headingText) {
        // Numbered headings
        if (headingText.matches("^\\d+\\.\\d+\\.\\d+.*")) {
            return "H3";
        } else if (headingText.matches("^\\d+\\.\\d+.*")) {
            return "H2";
        } else if (headingText.matches("^\\d+\\..*")) {
            return "H1";
        }
        
        // Chapter/Part/Section patterns
        if (headingText.toLowerCase().matches("^(chapter|part)\\s+\\d+.*")) {
            return "H1";
        } else if (headingText.toLowerCase().matches("^section\\s+\\d+.*")) {
            return "H2";
        }
        
        // Japanese patterns
        if (headingText.matches(".*[章編部].*")) {
            return "H1";
        } else if (headingText.matches(".*[節項].*")) {
            return "H2";
        }
        
        // All caps likely H1, mixed case likely lower level
        if (headingText.matches("^[A-Z][A-Z\\s]+$")) {
            return "H1";
        }
        
        // Default based on length and position
        if (headingText.length() > 40) {
            return "H1";
        } else if (headingText.length() > 25) {
            return "H2";
        } else {
            return "H3";
        }
    }
    
    private static List<Heading> filterAndCleanHeadings(List<Heading> headings) {
        List<Heading> filtered = new ArrayList<>();
        Set<String> seenTexts = new HashSet<>();
        
        for (Heading heading : headings) {
            String cleanText = heading.text.replaceAll("\\s+", " ").trim();
            
            // Skip very short or very long headings
            if (cleanText.length() < 5 || cleanText.length() > 120) {
                continue;
            }
            
            // Skip duplicates
            if (seenTexts.contains(cleanText.toLowerCase())) {
                continue;
            }
            
            // Skip common false positives
            if (isCommonFalsePositive(cleanText)) {
                continue;
            }
            
            seenTexts.add(cleanText.toLowerCase());
            filtered.add(new Heading(heading.level, cleanText, heading.page));
        }
        
        return filtered;
    }
    
    private static boolean isCommonFalsePositive(String text) {
        String lower = text.toLowerCase();
        return lower.contains("page") && lower.matches(".*\\d+.*") ||
               lower.equals("table of contents") ||
               lower.equals("references") ||
               lower.equals("bibliography") ||
               lower.equals("index") ||
               lower.matches("^\\d+$") ||
               lower.length() < 3;
    }
    
    private static void writeOutlineToJson(DocumentOutline outline, File outputFile) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("title", outline.title);
        
        ArrayNode outlineArray = objectMapper.createArrayNode();
        for (Heading heading : outline.headings) {
            ObjectNode headingNode = objectMapper.createObjectNode();
            headingNode.put("level", heading.level);
            headingNode.put("text", heading.text);
            headingNode.put("page", heading.page);
            outlineArray.add(headingNode);
        }
        
        root.set("outline", outlineArray);
        
        try (FileWriter writer = new FileWriter(outputFile)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, root);
        }
    }
    
    // Helper classes
    static class PageContent {
        final int pageNumber;
        final String text;
        
        PageContent(int pageNumber, String text) {
            this.pageNumber = pageNumber;
            this.text = text;
        }
    }
    
    static class DocumentOutline {
        final String title;
        final List<Heading> headings;
        
        DocumentOutline(String title, List<Heading> headings) {
            this.title = title;
            this.headings = headings;
        }
    }
    
    static class Heading {
        final String level;
        final String text;
        final int page;
        
        Heading(String level, String text, int page) {
            this.level = level;
            this.text = text;
            this.page = page;
        }
    }
}