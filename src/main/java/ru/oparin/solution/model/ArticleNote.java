package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сущность заметки к артикулу.
 */
@Entity
@Table(name = "article_notes", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleNote {

    /**
     * Уникальный идентификатор заметки.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Артикул WB (nmId).
     */
    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    /**
     * Продавец (SELLER), которому принадлежит артикул.
     */
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /**
     * Пользователь, создавший заметку.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Текст заметки.
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * Файлы, прикрепленные к заметке.
     */
    @OneToMany(mappedBy = "note", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ArticleNoteFile> files = new ArrayList<>();

    /**
     * Дата создания заметки.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления заметки.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

