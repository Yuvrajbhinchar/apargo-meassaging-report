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
 *  1. findWithComponentsByProjectAndNameAndLanguage – single template
 *  2. findBatchByProjectAndNames – all templates for a conversation page (no N+1)
 *
 * NOTE on ORDER BY:
 *  Hibernate 6 throws "Could not generate fetch" when ORDER BY is combined
 *  with multiple collection JOIN FETCHes on a DISTINCT query.
 *  Ordering is handled at the collection level via @OrderBy on the entity
 *  relationships (componentOrder ASC, buttonIndex ASC) — no ORDER BY needed here.
 */
@Repository
public interface WhatsappTemplateRepository extends JpaRepository<WhatsappTemplate, Long> {

    // ── Single template with full component tree ──────────────────────────
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
        """)
    Optional<WhatsappTemplate> findWithComponentsByProjectAndNameAndLanguage(
            @Param("projectId") Long projectId,
            @Param("name")      String name,
            @Param("language")  String language
    );

    // ── Batch: all templates for a set of names on a conversation page ────
    //
    // ORDER BY t.id ASC intentionally REMOVED.
    // Reason: Hibernate 6 cannot generate a valid fetch join when ORDER BY
    // is present alongside multiple collection JOIN FETCHes in a DISTINCT query.
    // It throws: "Could not generate fetch: WhatsappTemplate(t) -> components"
    //
    // Component ordering is preserved by @OrderBy("componentOrder ASC") on
    // WhatsappTemplate.components, and @OrderBy("buttonIndex ASC") on buttons.
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
        """)
    List<WhatsappTemplate> findBatchByProjectAndNames(
            @Param("projectId") Long projectId,
            @Param("names")     List<String> names
    );
}