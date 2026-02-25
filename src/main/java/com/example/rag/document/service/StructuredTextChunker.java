package com.example.rag.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;

/**
 * 구조 기반 텍스트 청커.
 * 마크다운 헤더/빈 줄 경계로 섹션을 분리한 뒤,
 * 작은 섹션은 병합하고 큰 섹션은 토큰 단위로 재분할한다.
 * 청크 간 overlap을 추가하여 문맥 끊김을 방지한다.
 */
public class StructuredTextChunker {

	private static final int MAX_TOKENS = 512;
	private static final int OVERLAP_TOKENS = 64;

	/** 마크다운 헤더 또는 연속 빈 줄로 섹션을 분리하는 패턴 */
	private static final Pattern SECTION_SPLIT = Pattern.compile("(?=^#{1,6} )|(?<=\\n)\\n(?=\\S)", Pattern.MULTILINE);

	private final Encoding encoding;

	public StructuredTextChunker() {
		this.encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
	}

	/**
	 * 텍스트를 구조 기반으로 청크 분할한다.
	 *
	 * @return 분할된 텍스트 청크 목록
	 */
	public List<String> chunk(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}

		// 1. 구조 경계로 섹션 분리
		List<String> sections = splitIntoSections(text);

		// 2. 섹션을 MAX_TOKENS 이내로 병합하거나 재분할
		List<String> rawChunks = mergeSections(sections);

		// 3. overlap 적용
		if (rawChunks.size() <= 1) {
			return rawChunks;
		}
		return applyOverlap(rawChunks);
	}

	private List<String> splitIntoSections(String text) {
		String[] parts = SECTION_SPLIT.split(text);
		List<String> sections = new ArrayList<>();
		for (String part : parts) {
			String trimmed = part.strip();
			if (!trimmed.isEmpty()) {
				sections.add(trimmed);
			}
		}
		return sections;
	}

	private List<String> mergeSections(List<String> sections) {
		List<String> chunks = new ArrayList<>();
		StringBuilder buffer = new StringBuilder();
		int bufferTokens = 0;

		for (String section : sections) {
			int sectionTokens = countTokens(section);

			// 단일 섹션이 MAX_TOKENS 초과 → 버퍼 플러시 후 토큰 단위 재분할
			if (sectionTokens > MAX_TOKENS) {
				if (bufferTokens > 0) {
					chunks.add(buffer.toString().strip());
					buffer.setLength(0);
					bufferTokens = 0;
				}
				chunks.addAll(splitByTokens(section));
				continue;
			}

			// 버퍼에 추가하면 초과 → 플러시
			if (bufferTokens + sectionTokens > MAX_TOKENS && bufferTokens > 0) {
				chunks.add(buffer.toString().strip());
				buffer.setLength(0);
				bufferTokens = 0;
			}

			if (buffer.length() > 0) {
				buffer.append("\n\n");
			}
			buffer.append(section);
			bufferTokens += sectionTokens;
		}

		if (bufferTokens > 0) {
			chunks.add(buffer.toString().strip());
		}

		return chunks;
	}

	/**
	 * 큰 텍스트를 토큰 단위로 MAX_TOKENS 크기로 분할한다.
	 */
	private List<String> splitByTokens(String text) {
		IntArrayList tokens = encoding.encode(text);
		List<String> chunks = new ArrayList<>();

		for (int start = 0; start < tokens.size(); start += MAX_TOKENS) {
			int end = Math.min(start + MAX_TOKENS, tokens.size());
			IntArrayList slice = new IntArrayList();
			for (int i = start; i < end; i++) {
				slice.add(tokens.get(i));
			}
			String chunk = encoding.decode(slice).strip();
			if (!chunk.isEmpty()) {
				chunks.add(chunk);
			}
		}

		return chunks;
	}

	/**
	 * 청크 간 overlap을 적용한다.
	 * 이전 청크의 마지막 OVERLAP_TOKENS을 다음 청크 앞에 붙인다.
	 */
	private List<String> applyOverlap(List<String> chunks) {
		List<String> result = new ArrayList<>();
		result.add(chunks.getFirst());

		for (int i = 1; i < chunks.size(); i++) {
			String prev = chunks.get(i - 1);
			IntArrayList prevTokens = encoding.encode(prev);

			String overlap = "";
			if (prevTokens.size() > OVERLAP_TOKENS) {
				IntArrayList overlapTokens = new IntArrayList();
				for (int j = prevTokens.size() - OVERLAP_TOKENS; j < prevTokens.size(); j++) {
					overlapTokens.add(prevTokens.get(j));
				}
				overlap = encoding.decode(overlapTokens).strip();
			}

			if (!overlap.isEmpty()) {
				result.add(overlap + "\n\n" + chunks.get(i));
			} else {
				result.add(chunks.get(i));
			}
		}

		return result;
	}

	private int countTokens(String text) {
		return encoding.encode(text).size();
	}
}
