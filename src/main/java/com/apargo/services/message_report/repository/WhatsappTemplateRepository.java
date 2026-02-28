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

    @Query("""
    SELECT t FROM WhatsappTemplate t
    WHERE t.projectId  = :projectId
      AND t.name       = :name
      AND t.language   = :language
      AND t.deletedAt IS NULL
    """)
    Optional<WhatsappTemplate> findWithComponentsByProjectAndNameAndLanguage(
            @Param("projectId") Long projectId,
            @Param("name")      String name,
            @Param("language")  String language
    );
    /**
     * Batch load templates for a set of names.
     *
     * JOIN FETCH is intentionally REMOVED for the batch query.
     * Reason: Hibernate 6 throws "Could not generate fetch" when DISTINCT +
     * multiple @OneToMany JOIN FETCHes + @OrderBy are combined.
     *
     * Collections are loaded via @BatchSize on the entity fields instead,
     * which issues one SQL per collection level (not one per row).
     * Force-initialization happens inside TemplateLoaderService (REQUIRES_NEW
     * transaction) so collections are safe to access after the transaction ends.
     */
    @Query("""
    SELECT t FROM WhatsappTemplate t
    WHERE t.projectId = :projectId
      AND t.name      IN :names
      AND t.deletedAt IS NULL
    """)
    List<WhatsappTemplate> findBatchByProjectAndNames(
            @Param("projectId") Long projectId,
            @Param("names")     List<String> names
    );
}