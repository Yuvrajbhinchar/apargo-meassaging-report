package com.apargo.services.message_report.service;

import com.apargo.services.message_report.entity.WhatsappTemplate;
import com.apargo.services.message_report.repository.WhatsappTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Isolated template loader.
 *
 * WHY a separate bean?
 *  ChatService.getMessages() runs in a @Transactional(readOnly = true) transaction.
 *  If the template JPQL fetch fails (schema missing, bad JOIN FETCH, etc.), Spring
 *  marks that transaction as rollback-only — even if the caller catches the exception.
 *  Result: UnexpectedRollbackException on commit, 500 to the client.
 *
 *  By moving the template fetch into REQUIRES_NEW, it runs in its own independent
 *  transaction. A failure rolls back only that inner transaction, not the outer one.
 *  The outer transaction (messages) commits normally and the client gets messages
 *  without templateDetail instead of a 500 error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateLoaderService {

    private final WhatsappTemplateRepository templateRepo;

    /**
     * Batch-load templates for all names referenced on a message page.
     *
     * Runs in REQUIRES_NEW — completely isolated from the caller's transaction.
     * On any failure, returns empty map (caller skips enrichment gracefully).
     *
     * @param projectId    project to scope the lookup
     * @param names        distinct template names from the current page
     * @return map of "name|language" → WhatsappTemplate entity (with components loaded)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Map<String, WhatsappTemplate> loadBatch(Long projectId, List<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<WhatsappTemplate> templates = templateRepo.findBatchByProjectAndNames(
                    projectId, names
            );

            if (templates.isEmpty()) {
                log.debug("No templates found for project={} names={}", projectId, names);
                return Collections.emptyMap();
            }

            // Key: "name|language" — allows different languages on the same page
            Map<String, WhatsappTemplate> result = templates.stream()
                    .collect(Collectors.toMap(
                            t -> t.getName() + "|" + t.getLanguage(),
                            Function.identity(),
                            (a, b) -> a  // keep first on name+language conflict
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