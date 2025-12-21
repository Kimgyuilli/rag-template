package com.example.rag.model.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a chunk of a document with its embedding vector.
 * Uses pgvector extension for storing and querying embeddings.
 */
@Entity
@Table(name = "document_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * Unique identifier for the source document.
	 * Multiple chunks can share the same documentId.
	 */
	@Column(nullable = false)
	private String documentId;

	/**
	 * Original filename of the uploaded document.
	 */
	@Column(nullable = false)
	private String filename;

	/**
	 * Index of this chunk within the document (0-based).
	 */
	@Column(nullable = false)
	private Integer chunkIndex;

	/**
	 * Text content of this chunk.
	 */
	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	/**
	 * OpenAI embedding vector (1536 dimensions for text-embedding-ada-002).
	 * Stored using pgvector extension.
	 * -- GETTER --
	 *  Get the embedding as a float array.
	 *  Needed for pgvector compatibility.

	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "vector(1536)")
	private float[] embedding;

	/**
	 * Type of source document (txt, md, pdf, etc.).
	 */
	@Column(length = 50)
	private String sourceType;

	/**
	 * Timestamp when this chunk was created.
	 */
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/**
	 * Automatically set creation timestamp before persisting.
	 */
	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
