package com.example.rag.document.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드된 파일에서 텍스트를 추출한다.
 * 지원 형식: PDF, TXT, MD
 */
@Service
public class FileParserService {

	public String extractText(MultipartFile file) throws IOException {
		String filename = file.getOriginalFilename();
		String ext = getExtension(filename).toLowerCase();

		return switch (ext) {
			case "pdf" -> extractPdf(file);
			case "txt", "md" -> new String(file.getBytes(), StandardCharsets.UTF_8);
			default -> throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + ext);
		};
	}

	private String extractPdf(MultipartFile file) throws IOException {
		try (PDDocument document = Loader.loadPDF(file.getBytes())) {
			return new PDFTextStripper().getText(document);
		}
	}

	private String getExtension(String filename) {
		if (filename == null || !filename.contains(".")) {
			throw new IllegalArgumentException("파일 확장자를 확인할 수 없습니다.");
		}
		return filename.substring(filename.lastIndexOf('.') + 1);
	}
}
