package com.apargo.services.message_report.service;

import com.apargo.services.message_report.entity.WhatsappTemplate;
import com.apargo.services.message_report.repository.WhatsappTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateLoaderService {

    private final WhatsappTemplateRepository templateRepo;

    /**
     * Batch-load templates with all nested collections fully initialized.
     *
     * Runs in REQUIRES_NEW — completely isolated from the caller's transaction.
     *
     * WHY force-initialize here?
     *   The entities are @Immutable and returned to ChatService, which accesses
     *   their collections AFTER this transaction has ended. Without explicit
     *   initialization inside this transaction, accessing a lazy collection
     *   later would throw LazyInitializationException.
     *   @BatchSize on the collection fields means each level is loaded in one
     *   batch SQL, not one SQL per row (no N+1).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Map<String, WhatsappTemplate> loadBatch(Long projectId, List<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<WhatsappTemplate> templates =
                    templateRepo.findBatchByProjectAndNames(projectId, names);

            if (templates.isEmpty()) {
                log.debug("No templates found for project={} names={}", projectId, names);
                return Collections.emptyMap();
            }

            // Force-initialize all lazy collections while still inside this transaction.
            // @BatchSize ensures Hibernate loads each collection level in a single
            // batched IN-query rather than one query per template/component/card.
            for (WhatsappTemplate t : templates) {
                Hibernate.initialize(t.getComponents());
                for (var comp : t.getComponents()) {
                    Hibernate.initialize(comp.getButtons());
                    Hibernate.initialize(comp.getCarouselCards());
                    for (var card : comp.getCarouselCards()) {
                        Hibernate.initialize(card.getCardComponents());
                        for (var cardComp : card.getCardComponents()) {
                            Hibernate.initialize(cardComp.getButtons());
                        }
                    }
                }
            }

            Map<String, WhatsappTemplate> result = templates.stream()
                    .collect(Collectors.toMap(
                            t -> t.getName() + "|" + t.getLanguage(),
                            Function.identity(),
                            (a, b) -> a
                    ));

            log.debug("Loaded {} templates for project={}", result.size(), projectId);
            return result;

        } catch (Exception e) {
            log.warn("Could not batch-load templates for project={} names={}: {}. " +
                            "Messages will be returned without template detail.",
                    projectId, names, e.getMessage());
            return Collections.emptyMap();
        }
    }
}