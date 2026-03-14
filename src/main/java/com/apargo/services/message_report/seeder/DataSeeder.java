package com.apargo.services.message_report.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  Production-Scale Data Seeder — 1 Crore (10,000,000) Messages
 * ══════════════════════════════════════════════════════════════════════════════
 *
 *  HOW TO RUN:
 *  ──────────
 *
 *    Full seed (contacts + conversations + messages):
 *      --seed
 *
 *    Messages only (you already have contacts/conversations from earlier seed):
 *      --seed --messages-only
 *
 *    Quick smoke test (100k messages only):
 *      --seed --messages-only --quick
 *
 *  Terminal:
 *    mvn spring-boot:run "-Dspring-boot.run.arguments=--seed --messages-only"
 *
 *  ══════════════════════════════════════════════════════════════════════════
 *  HOW IT WORKS:
 *  ─────────────
 *  1. Loads existing conversation IDs from DB (--messages-only skips contacts/convs)
 *  2. Seeds messages in CHUNK_SIZE (100,000) outer loops
 *     → Each chunk is itself split into DB_BATCH_SIZE (5,000) INSERT batches
 *     → After every chunk: logs progress, ETA, rows/sec
 *     → Backfills last_message_id after each chunk (keeps conversations fresh)
 *  3. At the end: prints final summary + verification counts
 * ══════════════════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final JdbcTemplate      jdbc;
    private final ApplicationContext ctx;

    // ══════════════════════════════════════════════════════════════════════
    //  CONFIG
    // ══════════════════════════════════════════════════════════════════════

    /** Total messages to insert in --messages-only full run */
    private static final int TARGET_MESSAGES_FULL  = 10_000_000;   // 1 crore

    /** Total messages to insert in --quick run */
    private static final int TARGET_MESSAGES_QUICK =    100_000;   // quick smoke test

    /** How many messages per outer loop iteration (logged as one "chunk") */
    private static final int CHUNK_SIZE            =    100_000;   // 1 lakh per chunk

    /** How many rows per JDBC batchUpdate call inside each chunk */
    private static final int DB_BATCH_SIZE         =      5_000;

    private static final int NUM_CONTACTS          =     50_000;
    private static final int NUM_CONVERSATIONS     =    100_000;

    private static final long ORG_ID     = 1L;
    private static final long PROJECT_ID = 1L;
    private static final long WABA_ID    = 1L;

    // ══════════════════════════════════════════════════════════════════════
    //  RANDOM DATA POOLS
    // ══════════════════════════════════════════════════════════════════════

    private static final String[] FIRST_NAMES = {
            "Raj","Priya","Amit","Sunita","Vikram","Anita","Suresh","Meena",
            "Ravi","Kavita","Deepak","Neha","Rahul","Pooja","Arun","Sonia",
            "Mahesh","Divya","Kiran","Geeta","Arjun","Nisha","Sanjay","Rekha",
            "Mohit","Sneha","Varun","Kavya","Rohit","Mansi","Ajay","Smita"
    };
    private static final String[] LAST_NAMES = {
            "Sharma","Patel","Singh","Kumar","Verma","Gupta","Yadav","Mishra",
            "Chauhan","Joshi","Pandey","Tiwari","Shah","Mehta","Kapoor","Bose",
            "Nair","Reddy","Rao","Iyer","Das","Banerjee","Mukherjee","Ghosh",
            "Chaudhary","Agarwal","Srivastava","Dubey","Tripathi","Malhotra"
    };
    private static final String[] STATUSES       = {
            "OPEN","OPEN","OPEN","OPEN","CLOSED","ARCHIVED"
    };
    private static final String[] ASSIGNED_TYPES = {
            "UNASSIGNED","USER","USER","TEAM"
    };
    private static final String[] DIRECTIONS     = {"INBOUND","OUTBOUND"};
    private static final String[] MSG_STATUSES   = {
            "SENT","DELIVERED","READ","READ","READ","FAILED"
    };
    private static final String[] BY_TYPES       = {
            "USER","USER","USER","SYSTEM","AUTOMATION","CAMPAIGN"
    };
    private static final String[] TEMPLATE_NAMES = {
            "order_confirmed","otp_verification","payment_received",
            "delivery_update","welcome_message","refund_initiated",
            "appointment_reminder","feedback_request","subscription_renewal",
            "account_blocked","promotional_sale","loyalty_points",
            "ticket_raised","ticket_resolved","shipping_delay",
            "product_back_in_stock","flash_sale_alert","review_request",
            "invoice_ready","kyc_reminder"
    };
    private static final String[] WORDS = {
            "Hello","Thanks","Please","Order","Delivery","Payment",
            "Confirm","Update","Status","Issue","Help","Support",
            "Query","Resolved","Pending","Invoice","Refund","Track",
            "Account","Dispatch"
    };

    /**
     * Dynamically loaded from DB at startup.
     * Weighted: TEXT appears 3x, TEMPLATE 2x more than others.
     * Falls back to {"TEXT"} if DB detection fails.
     */
    private String[] MSG_TYPES;

    private final Random rng = new Random();

    // ══════════════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void run(String... args) throws Exception {
        List<String> argList = Arrays.asList(args);
        if (!argList.contains("--seed")) return;

        initEnumValues();   // detect valid message_type ENUM values from DB

        boolean quick        = argList.contains("--quick");
        boolean messagesOnly = argList.contains("--messages-only");
        int     totalTarget  = quick ? TARGET_MESSAGES_QUICK : TARGET_MESSAGES_FULL;

        printBanner(quick, messagesOnly, totalTarget);
        long globalStart = System.currentTimeMillis();

        try {
            if (messagesOnly) {
                runMessagesOnly(totalTarget);
            } else {
                runFullSeed(totalTarget);
            }
        } catch (Exception e) {
            log.error("❌ Seeder failed: {}", e.getMessage(), e);
        }

        printSummary(globalStart);
        SpringApplication.exit(ctx, () -> 0);
        System.exit(0);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FULL SEED (contacts + conversations + messages)
    // ══════════════════════════════════════════════════════════════════════

    private void runFullSeed(int totalMessages) {
        seedTemplates();
        List<Long> contactIds = seedContacts();

        if (contactIds.isEmpty()) {
            log.error("❌ No contacts were seeded. Aborting full seed.");
            return;
        }

        List<Long> convIds = seedConversations(contactIds);

        if (convIds.isEmpty()) {
            log.error("❌ No conversations were seeded. Aborting message seed.");
            return;
        }

        seedMessagesInChunks(convIds, totalMessages);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MESSAGES ONLY (reuses existing contacts + conversations)
    // ══════════════════════════════════════════════════════════════════════

    private void runMessagesOnly(int totalMessages) {
        log.info("━━━ Loading existing conversation IDs from DB...");
        List<Long> convIds = jdbc.queryForList(
                "SELECT id FROM conversations WHERE project_id = ?", Long.class, PROJECT_ID
        );

        if (convIds.isEmpty()) {
            log.error("❌ No conversations found for project_id={}. Run full seed first (remove --messages-only).", PROJECT_ID);
            return;
        }

        log.info("  ✓ Found {} existing conversations", convIds.size());

        Long existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE project_id = ?", Long.class, PROJECT_ID
        );
        log.info("  ℹ Existing messages in DB: {}", fmt(existing != null ? existing : 0));
        log.info("  ℹ Will INSERT {} more messages", fmt(totalMessages));
        log.info("  ℹ Final total will be approx {}", fmt((existing != null ? existing : 0) + totalMessages));

        seedMessagesInChunks(convIds, totalMessages);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CORE: seed messages in CHUNK_SIZE outer loops
    // ══════════════════════════════════════════════════════════════════════

    private void seedMessagesInChunks(List<Long> convIds, int totalMessages) {

        if (convIds == null || convIds.isEmpty()) {
            log.error("❌ convIds is empty — cannot seed messages.");
            return;
        }

        log.info("");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  Seeding {} messages in chunks of {}",
                fmt(totalMessages), fmt(CHUNK_SIZE));
        log.info("  DB batch size per INSERT: {}", fmt(DB_BATCH_SIZE));
        log.info("  Total chunks: {}", (int) Math.ceil((double) totalMessages / CHUNK_SIZE));
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Pre-load contact_id map for all conversations
        Map<Long, Long> convContactMap = loadConvContactMap();

        // Disable FK/unique checks for bulk insert speed
        setMysqlBulkMode(true);

        String msgSql = """
            INSERT INTO messages
              (uuid, organization_id, project_id, conversation_id,
               waba_account_id, contact_id,
               direction, message_type,
               template_name, template_language, template_vars,
               body_text, provider_message_id,
               status, created_by_type, created_by_id,
               created_at, sent_at, delivered_at, read_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        String rollupSql = """
            INSERT INTO message_status_rollup
              (message_id, is_sent, is_delivered, is_read, is_failed, last_updated_at)
            VALUES (?,?,?,?,?,?)
            """;

        int chunksTotal   = (int) Math.ceil((double) totalMessages / CHUNK_SIZE);
        int totalInserted = 0;
        Map<Long, Long> globalLastMsg = new HashMap<>();
        long globalStart = System.currentTimeMillis();

        for (int chunkNum = 1; chunkNum <= chunksTotal; chunkNum++) {

            int chunkTarget = Math.min(CHUNK_SIZE, totalMessages - totalInserted);
            if (chunkTarget <= 0) break;

            long chunkStart = System.currentTimeMillis();
            log.info("");
            log.info("┌─ Chunk {}/{} — inserting {} messages ─────────────────",
                    chunkNum, chunksTotal, fmt(chunkTarget));

            Map<Long, Long> chunkLastMsg = insertChunk(
                    convIds, convContactMap, chunkTarget,
                    msgSql, rollupSql, chunkNum
            );

            chunkLastMsg.forEach((convId, msgId) ->
                    globalLastMsg.merge(convId, msgId, (a, b) -> b > a ? b : a)
            );

            totalInserted += chunkTarget;

            backfillLastMessageId(chunkLastMsg);

            double chunkSec   = (System.currentTimeMillis() - chunkStart) / 1000.0;
            double totalSec   = (System.currentTimeMillis() - globalStart) / 1000.0;
            double rowsPerSec = totalSec > 0 ? totalInserted / totalSec : 0;
            double remaining  = totalMessages - totalInserted;
            double etaSec     = rowsPerSec > 0 ? remaining / rowsPerSec : 0;
            double pct        = (totalInserted * 100.0) / totalMessages;

            log.info("└─ Chunk {}/{} done in {}s | Total: {}/{} ({}%) | {} rows/s | ETA: {}",
                    chunkNum, chunksTotal,
                    String.format("%.1f", chunkSec),
                    fmt(totalInserted), fmt(totalMessages),
                    String.format("%.1f", pct),
                    String.format("%.0f", rowsPerSec),
                    formatEta((long) etaSec));
        }

        setMysqlBulkMode(false);

        log.info("");
        log.info("━━━ All {} chunks complete. Total messages inserted: {}",
                chunksTotal, fmt(totalInserted));

        verifyInsertedData();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INSERT ONE CHUNK
    // ══════════════════════════════════════════════════════════════════════

    private Map<Long, Long> insertChunk(
            List<Long>       convIds,
            Map<Long, Long>  convContactMap,
            int              chunkTarget,
            String           msgSql,
            String           rollupSql,
            int              chunkNum
    ) {
        Map<Long, Long> lastMsgPerConv = new HashMap<>();
        List<Object[]>  msgBatch       = new ArrayList<>(DB_BATCH_SIZE);
        List<Object[]>  rollupBatch    = new ArrayList<>(DB_BATCH_SIZE);
        Timestamp       now            = Timestamp.from(Instant.now());
        int             inserted       = 0;

        for (int i = 0; i < chunkTarget; i++) {
            long convId    = pickFromList(convIds);
            long contactId = convContactMap.getOrDefault(convId, 1L);

            String    msgType   = pickFromArray(MSG_TYPES);
            String    direction = pickFromArray(DIRECTIONS);
            String    status    = pickFromArray(MSG_STATUSES);
            String    byType    = pickFromArray(BY_TYPES);
            Timestamp ca        = randomTimestamp();

            boolean isTmpl = "TEMPLATE".equals(msgType);
            boolean isSent = status.equals("SENT") || status.equals("DELIVERED") || status.equals("READ");
            boolean isDel  = status.equals("DELIVERED") || status.equals("READ");
            boolean isRead = status.equals("READ");
            boolean isFail = status.equals("FAILED");

            Timestamp sentAt      = isSent ? plusSeconds(ca, rng.nextInt(30)  + 1)  : null;
            Timestamp deliveredAt = isDel  ? plusSeconds(ca, rng.nextInt(90)  + 30) : null;
            Timestamp readAt      = isRead ? plusSeconds(ca, rng.nextInt(480) + 120): null;

            msgBatch.add(new Object[]{
                    UUID.randomUUID().toString(),
                    ORG_ID,
                    PROJECT_ID,
                    convId,
                    WABA_ID,
                    contactId,
                    direction,
                    msgType,
                    isTmpl ? pickFromArray(TEMPLATE_NAMES) : null,
                    isTmpl ? "en" : null,
                    isTmpl ? randomTemplateVars() : null,
                    "TEXT".equals(msgType) ? randSentence() : null,
                    "wamid." + shortUuid(),
                    status,
                    byType,
                    "USER".equals(byType) ? (long)(rng.nextInt(100) + 1) : null,
                    ca,
                    sentAt,
                    deliveredAt,
                    readAt
            });

            rollupBatch.add(new Object[]{
                    0L,   // placeholder — filled after INSERT
                    isSent ? 1 : 0,
                    isDel  ? 1 : 0,
                    isRead ? 1 : 0,
                    isFail ? 1 : 0,
                    now
            });

            if (msgBatch.size() >= DB_BATCH_SIZE) {
                int flushed = flushBatch(msgBatch, rollupBatch, msgSql, rollupSql, lastMsgPerConv);
                inserted += flushed;
                msgBatch.clear();
                rollupBatch.clear();

                if ((inserted / DB_BATCH_SIZE) % 10 == 0) {
                    log.info("  │  Chunk {} sub-progress: {}/{} messages",
                            chunkNum, fmt(inserted), fmt(chunkTarget));
                }
            }
        }

        if (!msgBatch.isEmpty()) {
            flushBatch(msgBatch, rollupBatch, msgSql, rollupSql, lastMsgPerConv);
        }

        return lastMsgPerConv;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FLUSH ONE DB BATCH
    // ══════════════════════════════════════════════════════════════════════

    private int flushBatch(
            List<Object[]>  msgBatch,
            List<Object[]>  rollupBatch,
            String          msgSql,
            String          rollupSql,
            Map<Long, Long> lastMsgPerConv
    ) {
        jdbc.batchUpdate(msgSql, msgBatch);

        Long maxId = jdbc.queryForObject("SELECT MAX(id) FROM messages", Long.class);
        if (maxId == null) return 0;

        long firstId = maxId - msgBatch.size() + 1;

        List<Object[]> actualRollup = new ArrayList<>(msgBatch.size());
        for (int j = 0; j < msgBatch.size(); j++) {
            long mid    = firstId + j;
            long convId = (long) msgBatch.get(j)[3];   // conversation_id is at index 3
            Object[] r  = rollupBatch.get(j);

            lastMsgPerConv.merge(convId, mid, (a, b) -> b > a ? b : a);
            actualRollup.add(new Object[]{ mid, r[1], r[2], r[3], r[4], r[5] });
        }

        jdbc.batchUpdate(rollupSql, actualRollup);

        return msgBatch.size();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BACKFILL last_message_id on conversations
    // ══════════════════════════════════════════════════════════════════════

    private void backfillLastMessageId(Map<Long, Long> lastMsgPerConv) {
        if (lastMsgPerConv.isEmpty()) return;

        log.info("  │  Backfilling last_message_id for {} conversations...",
                fmt(lastMsgPerConv.size()));

        String sql = "UPDATE conversations SET last_message_id=?, updated_at=NOW() WHERE id=?";
        List<Object[]> batch = new ArrayList<>(DB_BATCH_SIZE);

        for (Map.Entry<Long, Long> e : lastMsgPerConv.entrySet()) {
            batch.add(new Object[]{ e.getValue(), e.getKey() });
            if (batch.size() >= DB_BATCH_SIZE) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(sql, batch);
        }

        log.info("  │  ✓ Backfill done");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TEMPLATES
    // ══════════════════════════════════════════════════════════════════════

    private void seedTemplates() {
        log.info("━━━ [1/4] Seeding templates...");

        String[][] coreTemplates = {
                {"order_confirmed",   "UTILITY",        "en"},
                {"otp_verification",  "AUTHENTICATION", "en"},
                {"payment_received",  "UTILITY",        "en"},
                {"delivery_update",   "UTILITY",        "en"},
                {"welcome_message",   "MARKETING",      "en"},
        };

        for (String[] t : coreTemplates) {
            Timestamp ca = daysAgo(300, 365);
            jdbc.update("""
                INSERT IGNORE INTO whatsapp_templates
                  (organization_id, project_id, waba_account_id, name, category,
                   language, status, created_by, created_at, updated_at)
                VALUES (?,?,?,?,?,?,'APPROVED',1,?,?)
                """,
                    ORG_ID, PROJECT_ID, String.valueOf(WABA_ID),
                    t[0], t[1], t[2], ca, ca);

            Long tid = jdbc.queryForObject(
                    "SELECT id FROM whatsapp_templates WHERE name=? AND project_id=?",
                    Long.class, t[0], PROJECT_ID);

            if (tid == null) continue;

            jdbc.update("""
                INSERT IGNORE INTO whatsapp_template_components
                  (template_id, component_type, format, text, component_order, created_at)
                VALUES (?,'BODY','TEXT',?,0,?)
                """,
                    tid, "Hi {{1}}, this is a message about your " + t[0] + ".", ca);
        }

        String[] extras = {
                "refund_initiated","appointment_reminder","feedback_request",
                "subscription_renewal","account_blocked","promotional_sale",
                "loyalty_points","ticket_raised","ticket_resolved","shipping_delay",
                "product_back_in_stock","flash_sale_alert","review_request",
                "invoice_ready","kyc_reminder"
        };

        for (String name : extras) {
            Timestamp ca = daysAgo(100, 365);
            jdbc.update("""
                INSERT IGNORE INTO whatsapp_templates
                  (organization_id, project_id, waba_account_id, name, category,
                   language, status, created_by, created_at, updated_at)
                VALUES (?,?,?,?,'MARKETING','en','APPROVED',1,?,?)
                """,
                    ORG_ID, PROJECT_ID, String.valueOf(WABA_ID), name, ca, ca);

            Long tid = jdbc.queryForObject(
                    "SELECT id FROM whatsapp_templates WHERE name=? AND project_id=?",
                    Long.class, name, PROJECT_ID);

            if (tid != null) {
                jdbc.update("""
                    INSERT IGNORE INTO whatsapp_template_components
                      (template_id, component_type, format, text, component_order, created_at)
                    VALUES (?,'BODY','TEXT',?,0,?)
                    """,
                        tid, "Hello {{1}}, update regarding " + name + ".", ca);
            }
        }

        log.info("  ✓ Templates seeded");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONTACTS
    // ══════════════════════════════════════════════════════════════════════

    private List<Long> seedContacts() {
        log.info("━━━ [2/4] Seeding {} contacts...", fmt(NUM_CONTACTS));
        long t0 = System.currentTimeMillis();

        String sql = """
            INSERT IGNORE INTO contacts
              (organization_id, wa_phone_e164, wa_id, display_name, source,
               first_seen_at, last_seen_at, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            """;

        List<Object[]> batch = new ArrayList<>(DB_BATCH_SIZE);

        for (int i = 0; i < NUM_CONTACTS; i++) {
            String phone = "+91" + (7000000000L + (long)(rng.nextDouble() * 2999999999L));
            String name  = pickFromArray(FIRST_NAMES) + " " + pickFromArray(LAST_NAMES);
            Timestamp ca = daysAgo(180, 730);

            batch.add(new Object[]{
                    ORG_ID, phone, phone.replace("+", ""), name,
                    rng.nextInt(4), ca, daysAgo(0, 30), ca, ca
            });

            if (batch.size() >= DB_BATCH_SIZE) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(sql, batch);

        // Verify how many contacts landed in DB
        Long dbCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM contacts WHERE organization_id=?", Long.class, ORG_ID);
        log.info("  ✓ DB reports {} contacts for org_id={}", fmt(dbCount != null ? dbCount : 0L), ORG_ID);

        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM contacts WHERE organization_id=?", Long.class, ORG_ID);

        double elapsed = (System.currentTimeMillis() - t0) / 1000.0;
        log.info("  ✓ {} contacts ready ({} s)", fmt(ids.size()), String.format("%.1f", elapsed));

        if (ids.isEmpty()) {
            log.error("  ❌ contacts list is empty after seed! " +
                    "Check: (1) INSERT IGNORE may have skipped all rows due to unique constraint, " +
                    "(2) organization_id mismatch. Run: SELECT COUNT(*) FROM contacts;");
        }

        return ids;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONVERSATIONS
    // ══════════════════════════════════════════════════════════════════════

    private List<Long> seedConversations(List<Long> contactIds) {

        // ── Guard: never call rng.nextInt(0) ─────────────────────────────
        if (contactIds == null || contactIds.isEmpty()) {
            log.error("❌ contactIds is empty — cannot seed conversations.");
            return Collections.emptyList();
        }

        log.info("━━━ [3/4] Seeding {} conversations...", fmt(NUM_CONVERSATIONS));
        long t0 = System.currentTimeMillis();

        String sql = """
            INSERT IGNORE INTO conversations
              (organization_id, project_id, waba_account_id, contact_id,
               status, assigned_type, assigned_id,
               last_message_at, last_message_direction, last_message_preview,
               unread_count, conversation_open_until,
               last_inbound_at, last_outbound_at, is_locked,
               created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?)
            """;

        List<Object[]> batch = new ArrayList<>(DB_BATCH_SIZE);

        for (int i = 0; i < NUM_CONVERSATIONS; i++) {
            long      contactId  = pickFromList(contactIds);
            String    status     = pickFromArray(STATUSES);
            String    assignType = pickFromArray(ASSIGNED_TYPES);
            Long      assignId   = "UNASSIGNED".equals(assignType) ? null : (long)(rng.nextInt(50) + 1);
            Timestamp lastMsg    = daysAgo(0, 180);
            String    direction  = pickFromArray(DIRECTIONS);
            int       unread     = "OPEN".equals(status) ? rng.nextInt(25) : 0;
            Timestamp openUntil  = "INBOUND".equals(direction)
                    ? Timestamp.from(lastMsg.toInstant().plus(24, ChronoUnit.HOURS)) : null;
            Timestamp createdAt  = Timestamp.from(
                    lastMsg.toInstant().minus(rng.nextInt(60) + 1, ChronoUnit.DAYS));

            batch.add(new Object[]{
                    ORG_ID, PROJECT_ID, WABA_ID, contactId,
                    status, assignType, assignId,
                    lastMsg, direction, randSentence(),
                    unread, openUntil,
                    "INBOUND".equals(direction)  ? lastMsg : null,
                    "OUTBOUND".equals(direction) ? lastMsg : null,
                    createdAt, lastMsg
            });

            if (batch.size() >= DB_BATCH_SIZE) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(sql, batch);

        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM conversations WHERE project_id=?", Long.class, PROJECT_ID);

        double elapsed = (System.currentTimeMillis() - t0) / 1000.0;
        log.info("  ✓ {} conversations ready ({} s)", fmt(ids.size()), String.format("%.1f", elapsed));

        if (ids.isEmpty()) {
            log.error("  ❌ conversations list is empty after seed! " +
                    "Check for INSERT IGNORE silently skipping all rows.");
        }

        return ids;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DETECT VALID ENUM VALUES FROM DB
    // ══════════════════════════════════════════════════════════════════════

    private void initEnumValues() {
        try {
            String enumDef = jdbc.queryForObject(
                    "SELECT COLUMN_TYPE FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "  AND TABLE_NAME   = 'messages' " +
                            "  AND COLUMN_NAME  = 'message_type'",
                    String.class
            );

            if (enumDef != null && enumDef.toLowerCase().startsWith("enum(")) {
                String inner = enumDef.substring(5, enumDef.length() - 1);
                Set<String> valid = new LinkedHashSet<>();
                for (String token : inner.split(",")) {
                    valid.add(token.replace("'", "").trim().toUpperCase());
                }
                log.info("  Detected message_type ENUM: {}", valid);

                // Weighted pool — TEXT 3x, TEMPLATE 2x, others 1x
                List<String> weighted = new ArrayList<>();
                String[] preferred = {
                        "TEXT","TEXT","TEXT",
                        "TEMPLATE","TEMPLATE",
                        "IMAGE","DOCUMENT","AUDIO","VIDEO",
                        "INTERACTIVE","REACTION","SYSTEM"
                };
                for (String v : preferred) {
                    if (valid.contains(v)) weighted.add(v);
                }
                if (weighted.isEmpty()) weighted.addAll(valid);

                MSG_TYPES = weighted.toArray(new String[0]);
                log.info("  MSG_TYPES pool (weighted): {}", Arrays.toString(MSG_TYPES));
            } else {
                log.warn("  Unexpected COLUMN_TYPE '{}', falling back to TEXT only", enumDef);
                MSG_TYPES = new String[]{"TEXT"};
            }
        } catch (Exception e) {
            log.warn("  Could not detect message_type ENUM ({}). Falling back to TEXT only.", e.getMessage());
            MSG_TYPES = new String[]{"TEXT"};
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  VERIFICATION
    // ══════════════════════════════════════════════════════════════════════

    private void verifyInsertedData() {
        log.info("");
        log.info("━━━ POST-SEED VERIFICATION ─────────────────────────────────");

        try {
            long contacts    = safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM contacts", Long.class));
            long convs       = safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Long.class));
            long msgs        = safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class));
            long rollup      = safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM message_status_rollup", Long.class));
            long templates   = safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM whatsapp_templates", Long.class));
            long openConvs   = safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE status='OPEN'", Long.class));
            long unread      = safe(() -> jdbc.queryForObject("SELECT COALESCE(SUM(unread_count),0) FROM conversations WHERE status='OPEN'", Long.class));
            long withLastMsg = safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE last_message_id IS NOT NULL", Long.class));
            long templateMsgs = safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE message_type='TEMPLATE'", Long.class));
            long textMsgs    = safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE message_type='TEXT'", Long.class));

            log.info("  contacts              : {}", fmt(contacts));
            log.info("  conversations (total) : {}", fmt(convs));
            log.info("  conversations (OPEN)  : {}", fmt(openConvs));
            log.info("  conversations w/ last_message_id: {}", fmt(withLastMsg));
            log.info("  total unread count    : {}", fmt(unread));
            log.info("  messages (total)      : {}", fmt(msgs));
            log.info("  messages TEXT         : {}", fmt(textMsgs));
            log.info("  messages TEMPLATE     : {}", fmt(templateMsgs));
            log.info("  message_status_rollup : {}", fmt(rollup));
            log.info("  whatsapp_templates    : {}", fmt(templates));

            if (msgs != rollup) {
                log.warn("  ⚠ messages ({}) != rollup ({}) — some rollup rows may be missing",
                        fmt(msgs), fmt(rollup));
            } else {
                log.info("  ✓ messages == rollup — counts match perfectly");
            }

            if (withLastMsg < convs) {
                log.warn("  ⚠ {} conversations still have last_message_id=NULL",
                        fmt(convs - withLastMsg));
            } else {
                log.info("  ✓ All conversations have last_message_id set");
            }

        } catch (Exception e) {
            log.warn("  Could not complete verification: {}", e.getMessage());
        }

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MYSQL BULK MODE
    // ══════════════════════════════════════════════════════════════════════

    private void setMysqlBulkMode(boolean on) {
        if (on) {
            log.info("  ⚡ MySQL bulk mode ON  (FK checks disabled for speed)");
            jdbc.execute("SET foreign_key_checks=0");
            jdbc.execute("SET unique_checks=0");
        } else {
            log.info("  ✓ MySQL bulk mode OFF (FK checks re-enabled)");
            jdbc.execute("SET foreign_key_checks=1");
            jdbc.execute("SET unique_checks=1");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LOAD CONVERSATION → CONTACT MAP
    // ══════════════════════════════════════════════════════════════════════

    private Map<Long, Long> loadConvContactMap() {
        log.info("  Loading conversation→contact map...");
        Map<Long, Long> map = new HashMap<>();
        jdbc.query(
                "SELECT id, contact_id FROM conversations WHERE project_id=?",
                ps -> ps.setLong(1, PROJECT_ID),
                (RowCallbackHandler) rs -> map.put(rs.getLong("id"), rs.getLong("contact_id"))
        );
        log.info("  ✓ Loaded {} conversation entries", fmt(map.size()));
        return map;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private Timestamp daysAgo(int minDays, int maxDays) {
        if (maxDays <= minDays) maxDays = minDays + 1;
        long ms = ((long)(rng.nextInt((maxDays - minDays) * 86400)) + (long)(minDays * 86400L)) * 1000L;
        return Timestamp.from(Instant.now().minusMillis(ms));
    }

    /**
     * Returns a random timestamp spread over the last 365 days.
     * 70% of messages in last 90 days, 30% in 90–365 days.
     */
    private Timestamp randomTimestamp() {
        int maxDays = rng.nextInt(100) < 70 ? 90 : 365;
        return daysAgo(0, maxDays);
    }

    private Timestamp plusSeconds(Timestamp base, int seconds) {
        return Timestamp.from(base.toInstant().plusSeconds(seconds));
    }

    /**
     * Pick a random element from a String array.
     * Never throws for non-empty arrays.
     */
    private String pickFromArray(String[] arr) {
        if (arr == null || arr.length == 0) {
            throw new IllegalStateException("Cannot pick from an empty array");
        }
        return arr[rng.nextInt(arr.length)];
    }

    /**
     * Pick a random Long from a list.
     * Guards against empty list — throws a clear error instead of
     * the cryptic "bound must be positive" from rng.nextInt(0).
     */
    private long pickFromList(List<Long> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot pick from an empty list. Make sure contacts/conversations were seeded first.");
        }
        return list.get(rng.nextInt(list.size()));
    }

    private String randSentence() {
        int len = rng.nextInt(8) + 3;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(' ');
            sb.append(pickFromArray(WORDS));
        }
        return sb.toString();
    }

    private String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private String randomTemplateVars() {
        String[] names   = {"John","Priya","Rahul","Meena","Amit","Sunita","Raj","Kavita"};
        String[] orders  = {"ORD-1234","ORD-5678","ORD-9012","ORD-3456","INV-7890"};
        String[] dates   = {"Tomorrow","Today","In 2 days","This Friday","Monday"};
        String[] amounts = {"INR500","INR1200","INR2500","INR499","INR999"};

        int variant = rng.nextInt(4);
        return switch (variant) {
            case 0 -> String.format("{\"body\":[[\"'%s'\",\"%s\",\"%s\"]]}",
                    pickFromArray(names), pickFromArray(orders), pickFromArray(dates));
            case 1 -> String.format("{\"body\":[[\"'%s'\",\"%s\"]]}",
                    pickFromArray(names), pickFromArray(amounts));
            case 2 -> String.format("{\"body\":[[\"'%s'\"]]}",
                    pickFromArray(names));
            default -> "{\"body\":[[\"Customer\",\"ORD-0000\",\"Soon\"]]}";
        };
    }

    private String fmt(long n) {
        return String.format("%,d", n);
    }

    private String formatEta(long seconds) {
        if (seconds < 60)   return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    @FunctionalInterface
    private interface Supplier<T> { T get() throws Exception; }

    private long safe(Supplier<Long> s) {
        try { Long v = s.get(); return v != null ? v : 0L; }
        catch (Exception e) { return 0L; }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PRINT BANNER + SUMMARY
    // ══════════════════════════════════════════════════════════════════════

    private void printBanner(boolean quick, boolean messagesOnly, int totalMessages) {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║          Apargo DB Seeder — Production Scale             ║");
        log.info("╠══════════════════════════════════════════════════════════╣");
        log.info("║  Mode         : {}",
                messagesOnly ? "MESSAGES ONLY (reuse existing convs)" : "FULL SEED (contacts+convs+msgs)");
        log.info("║  Scale        : {}", quick ? "QUICK (100k)" : "FULL (1 crore)");
        log.info("║  Target msgs  : {}", fmt(totalMessages));
        log.info("║  Chunk size   : {} (outer loop)", fmt(CHUNK_SIZE));
        log.info("║  DB batch     : {} (rows per INSERT)", fmt(DB_BATCH_SIZE));
        log.info("║  Chunks total : {}", (int) Math.ceil((double) totalMessages / CHUNK_SIZE));
        log.info("╚══════════════════════════════════════════════════════════╝");
    }

    private void printSummary(long globalStart) {
        long elapsed = (System.currentTimeMillis() - globalStart) / 1000;
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  SEEDER COMPLETE                                          ║");
        log.info("║  Total time: {} min {} sec", elapsed / 60, elapsed % 60);
        log.info("╠══════════════════════════════════════════════════════════╣");
        log.info("║  Test your APIs:                                          ║");
        log.info("║  GET /api/chats/inbox?projectId=1&size=20                 ║");
        log.info("║  GET /api/v1/get-messages-history?projectId=1             ║");
        log.info("║  GET /api/chats/conversation/{id}/messages                ║");
        log.info("╚══════════════════════════════════════════════════════════╝");
    }
}