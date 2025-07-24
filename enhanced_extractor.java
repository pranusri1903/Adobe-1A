package com.pdfextractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced PDF Outline Extractor with OCR support and advanced text analysis
 * Optimized for heading detection accuracy and multilingual support
 */
public class EnhancedPDFOutlineExtractor {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Enhanced patterns for better accuracy
    private static final Pattern[] HEADING_PATTERNS = {
        // Numbered hierarchical patterns
        Pattern.compile("^\\s*(\\d+\\.\\s+[A-ZÀ-ÿ\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF][^\\n]{4,80})\\s*$", Pattern.MULTILINE),
        Pattern.compile("^\\s*(\\d+\\.\\d+\\.\\s+[A-ZÀ-ÿ\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF][^\\n]{4,80})\\s*$", Pattern.MULTILINE),
        Pattern.compile("^\\s*(\\d+\\.\\d+\\.\\d+\\.\\s+[A-ZÀ-ÿ\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF][^\\n]{4,80})\\s*$", Pattern.MULTILINE),
        
        // Roman numerals
        Pattern.compile("^\\s*([IVX]+\\.[\\s]*[A-ZÀ-ÿ\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF][^\\n]{4,80})\\s*$", Pattern.MULTILINE),
        
        // Chapter/Section patterns - multilingual
        Pattern.compile("^\\s*((?:Chapter|Chapitre|Kapitel|章)\\s*\\d+[:.\\s]*[A-ZÀ-ÿ\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF][^\\n]{4,80})\\s*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE),
        Pattern.compile("^\\s*((?:Section|セクション)\\s*\\d+