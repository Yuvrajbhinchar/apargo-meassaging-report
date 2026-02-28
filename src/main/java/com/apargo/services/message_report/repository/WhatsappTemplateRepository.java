package com.apargo.services.message_report.repository;

import com.apargo.services.message_report.entity.WhatsappTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Read-only access to apargo_wa_template.whatsapp_templates.
 *
 * Two load strategies:
 *  1. findByProjectAndNameAndLanguage  – single template (used when rendering one message)
 *  2. findAllByProjectAndNamesAndLanguages – batch load for a full conversation page (avoids N+1)
 */
@Repository
public interface WhatsappTemplateRepository extends JpaRepository<WhatsappTemplate, Long> {

    // ── Single template with full component tree ──────────────────────────
    // EntityGraph eager-loads: components → buttons + carouselCards → cardComponents → buttons
    // This replaces lazy-load chains and keeps it to 1 round-trip.
    @Query("""
        SELECT DISTINCT t FROM WhatsappTemplate t
        LEFT JOIN FETCH t.components c
        LEFT JOIN FETCH c.buttons
        LEFT JOIN FETCH c.carouselCards cc
        LEFT JOIN FETCH cc.cardComponents ccc
        LEFT JOIN FETCH ccc.buttons
        WHERE t.projectId   = :projectId
          AND t.name        = :name
          AND t.language    = :language
          AND t.deletedAt  IS NULL
        ORDER BY t.id ASC
        """)
    Optional<WhatsappTemplate> findWithComponentsByProjectAndNameAndLanguage(
            @Param("projectId") Long projectId,
            @Param("name")      String name,
            @Param("language")  String language
    );

    // ── Batch: all templates for a set of (name, language) pairs ─────────
    // Used to pre-load every template referenced in a conversation page in one query.
    // Caller filters by projectId + (name, language) combinations from the message list.
    @Query("""
        SELECT DISTINCT t FROM WhatsappTemplate t
        LEFT JOIN FETCH t.components c
        LEFT JOIN FETCH c.buttons
        LEFT JOIN FETCH c.carouselCards cc
        LEFT JOIN FETCH cc.cardComponents ccc
        LEFT JOIN FETCH ccc.buttons
        WHERE t.projectId = :projectId
          AND t.name      IN :names
          AND t.deletedAt IS NULL
        ORDER BY t.id ASC
        """)
    List<WhatsappTemplate> findBatchByProjectAndNames(
            @Param("projectId") Long projectId,
            @Param("names")     List<String> names
    );
}