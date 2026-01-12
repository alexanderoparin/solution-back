package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Сущность файла, прикрепленного к заметке.
 */
@Entity
@Table(name = "article_note_files", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleNoteFile {

    /**
     * Уникальный идентификатор файла.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Заметка, к которой прикреплен файл.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    private ArticleNote note;

    /**
     * Оригинальное имя файла.
     */
    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    /**
     * Путь к файлу на сервере.
     */
    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    /**
     * Размер файла в байтах.
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * MIME-тип файла.
     */
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /**
     * Дата загрузки файла.
     */
    @CreatedDate
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;
}

