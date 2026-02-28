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
 * Production-scale data seeder.
 *
 * HOW TO RUN:
 *   Pass --seed as a program argument in IntelliJ:
 *   Run → Edit Configurations → Program Arguments → --seed
 *
 *   OR run from terminal:
 *   mvn spring-boot:run "-Dspring-boot.run.arguments=--seed"
 *
 *   For quick smoke test (50k messages):
 *   mvn spring-boot:run "-Dspring-boot.run.arguments=--seed --quick"
 *
 * It will exit automatically when done.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final ApplicationContext ctx;

    // ── Config ────────────────────────────────────────────────────────────
    private static final int NUM_CONTACTS      =  50_000;
    private static final int NUM_CONVERSATIONS = 100_000;
    private static final int NUM_MESSAGES_FULL = 2_000_000;
    private static final int NUM_MESSAGES_QUICK =  50_000;

    private static final int BATCH_SIZE = 2_000;   // rows per INSERT

    private static final long ORG_ID     = 1L;
    private static final long PROJECT_ID = 1L;
    private static final long WABA_ID    = 1L;

    // ── Random data pools ─────────────────────────────────────────────────
    private static final String[] FIRST_NAMES = {
            "Raj","Priya","Amit","Sunita","Vikram","Anita","Suresh","Meena",
            "Ravi","Kavita","Deepak","Neha","Rahul","Pooja","Arun","Sonia",
            "Mahesh","Divya","Kiran","Geeta","Arjun","Nisha","Sanjay","Rekha"
    };
    private static final String[] LAST_NAMES = {
            "Sharma","Patel","Singh","Kumar","Verma","Gupta","Yadav","Mishra",
            "Chauhan","Joshi","Pandey","Tiwari","Shah","Mehta","Kapoor","Bose",
            "Nair","Reddy","Rao","Iyer","Das","Banerjee","Mukherjee","Ghosh"
    };
    private static final String[] STATUSES       = {"OPEN","OPEN","OPEN","CLOSED","ARCHIVED"};
    private static final String[] ASSIGNED_TYPES = {"UNASSIGNED","USER","USER","TEAM"};
    private static final String[] DIRECTIONS     = {"INBOUND","OUTBOUND"};
    private static final String[] MSG_STATUSES   = {"SENT","DELIVERED","READ","READ","FAILED"};
    private static final String[] BY_TYPES       = {"USER","USER","SYSTEM","AUTOMATION","CAMPAIGN"};
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
            "Confirm","Update","Status","Issue","Help","Support"
    };

    /**
     * Dynamically loaded from DB at runtime.
     * Weighted so TEXT appears ~3x and TEMPLATE ~2x more than others.
     * Falls back to {"TEXT"} if detection fails.
     */
    private String[] MSG_TYPES;

    private final Random rng = new Random();

    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void run(String... args) throws Exception {
        List<String> argList = Arrays.asList(args);

        // Only run when --seed flag is passed
        if (!argList.contains("--seed")) {
            return;
        }

        // ── Detect valid ENUM values from DB before any seeding ──────────
        initEnumValues();

        boolean quick = argList.contains("--quick");
        int numMessages = quick ? NUM_MESSAGES_QUICK : NUM_MESSAGES_FULL;

        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║        Apargo DB Seeder — Starting               ║");
        log.info("║  Mode     : {}                         ", quick ? "QUICK (50k)" : "FULL  (2M) ");
        log.info("║  Contacts : {}                              ", NUM_CONTACTS);
        log.info("║  Convs    : {}                             ", NUM_CONVERSATIONS);
        log.info("║  Messages : {}                          ", numMessages);
        log.info("╚══════════════════════════════════════════════════╝");

        long totalStart = System.currentTimeMillis();

        try {
            seedTemplates();
            List<Long> contactIds   = seedContacts();
            List<Long> convIds      = seedConversations(contactIds);
            Map<Long,Long> lastMsgs = seedMessages(convIds, numMessages);
            backfillLastMessageId(lastMsgs);
        } catch (Exception e) {
            log.error("❌ Seeder failed: {}", e.getMessage(), e);
        }

        long elapsed = (System.currentTimeMillis() - totalStart) / 1000;
        log.info("✅ Done! Total time: {} min {} sec", elapsed / 60, elapsed % 60);

        // Exit after seeding — don't keep the server running
        SpringApplication.exit(ctx, () -> 0);
        System.exit(0);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INIT — detect valid message_type ENUM values from DB
    //
    //  Why dynamic?
    //   ddl-auto: validate only checks column *kind* (string-like), NOT the
    //   exact set of ENUM literals. So Hibernate boots fine even when the DB
    //   ENUM is a subset of what the entity declares. Inserting a value not in
    //   the DB ENUM causes "Data truncated for column 'message_type'".
    //   Reading the actual ENUM definition at startup makes the seeder
    //   self-healing regardless of schema state.
    // ══════════════════════════════════════════════════════════════════════

    private void initEnumValues() {
        try {
            String enumDef = jdbc.queryForObject(
                    "SELECT COLUMN_TYPE " +
                            "FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "  AND TABLE_NAME   = 'messages' " +
                            "  AND COLUMN_NAME  = 'message_type'",
                    String.class
            );

            // enumDef looks like: enum('TEXT','IMAGE','VIDEO',...)
            if (enumDef != null && enumDef.toLowerCase().startsWith("enum(")) {
                String inner = enumDef.substring(5, enumDef.length() - 1); // strip enum( and )
                Set<String> valid = new LinkedHashSet<>();
                for (String token : inner.split(",")) {
                    valid.add(token.replace("'", "").trim().toUpperCase());
                }

                log.info("  Detected message_type ENUM values from DB: {}", valid);

                // Build a weighted list — prefer TEXT (3x) and TEMPLATE (2x)
                // so the seeded data feels realistic; only include values the DB actually supports
                List<String> weighted = new ArrayList<>();
                String[] preferred = {"TEXT", "TEXT", "TEXT", "TEMPLATE", "TEMPLATE",
                        "IMAGE", "DOCUMENT", "AUDIO", "VIDEO", "INTERACTIVE",
                        "REACTION", "SYSTEM"};

                for (String v : preferred) {
                    if (valid.contains(v)) {
                        weighted.add(v);
                    }
                }

                if (weighted.isEmpty()) {
                    // Last resort: use whatever the DB has
                    weighted.addAll(valid);
                }

                MSG_TYPES = weighted.toArray(new String[0]);
                log.info("  MSG_TYPES weight pool: {}", Arrays.toString(MSG_TYPES));

            } else {
                log.warn("  Unexpected COLUMN_TYPE value '{}', falling back to TEXT only", enumDef);
                MSG_TYPES = new String[]{"TEXT"};
            }

        } catch (Exception e) {
            log.warn("  Could not detect message_type ENUM from DB ({}). " +
                    "Falling back to TEXT only.", e.getMessage());
            MSG_TYPES = new String[]{"TEXT"};
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STEP 1 — TEMPLATES
    // ══════════════════════════════════════════════════════════════════════

    private void seedTemplates() {
        log.info("[1/5] Seeding templates...");

        String[][] templates = {
                {"order_confirmed",   "UTILITY",        "en"},
                {"otp_verification",  "AUTHENTICATION", "en"},
                {"payment_received",  "UTILITY",        "en"},
                {"delivery_update",   "UTILITY",        "en"},
                {"welcome_message",   "MARKETING",      "en"},
        };

        for (String[] t : templates) {
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

            // BODY component
            jdbc.update("""
                INSERT IGNORE INTO whatsapp_template_components
                  (template_id, component_type, format, text, component_order, created_at)
                VALUES (?,'BODY','TEXT',?,0,?)
                """,
                    tid, "Hi {{1}}, this is a message about your " + t[0] + ".", ca);
        }

        // Extra templates — BODY only
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
    //  STEP 2 — CONTACTS
    // ══════════════════════════════════════════════════════════════════════

    private List<Long> seedContacts() {
        log.info("[2/5] Seeding {} contacts...", NUM_CONTACTS);
        long t0 = System.currentTimeMillis();

        String sql = """
            INSERT IGNORE INTO contacts
              (organization_id, wa_phone_e164, wa_id, display_name, source,
               first_seen_at, last_seen_at, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            """;

        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

        for (int i = 0; i < NUM_CONTACTS; i++) {
            String phone = "+91" + (7000000000L + (long)(rng.nextDouble() * 2999999999L));
            String name  = pick(FIRST_NAMES) + " " + pick(LAST_NAMES);
            Timestamp ca = daysAgo(180, 730);
            Timestamp ls = daysAgo(0, 30);

            batch.add(new Object[]{
                    ORG_ID, phone, phone.replace("+",""), name,
                    rng.nextInt(4), ca, ls, ca, ca
            });

            if (batch.size() >= BATCH_SIZE) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
                logProgress("contacts", i + 1, NUM_CONTACTS, t0);
            }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(sql, batch);

        List<Long> ids = jdbc.queryForList("SELECT id FROM contacts ORDER BY id", Long.class);
        log.info("\n  ✓ {} contacts ready", ids.size());
        return ids;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STEP 3 — CONVERSATIONS
    // ══════════════════════════════════════════════════════════════════════

    private List<Long> seedConversations(List<Long> contactIds) {
        log.info("[3/5] Seeding {} conversations...", NUM_CONVERSATIONS);
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

        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

        for (int i = 0; i < NUM_CONVERSATIONS; i++) {
            long contactId    = pick(contactIds);
            String status     = pick(STATUSES);
            String assignType = pick(ASSIGNED_TYPES);
            Long assignId     = assignType.equals("UNASSIGNED") ? null : (long) rng.nextInt(50) + 1;
            Timestamp lastMsg = daysAgo(0, 180);
            String direction  = pick(DIRECTIONS);
            int unread        = status.equals("OPEN") ? rng.nextInt(20) : 0;
            Timestamp openUntil = direction.equals("INBOUND")
                    ? Timestamp.from(lastMsg.toInstant().plus(24, ChronoUnit.HOURS)) : null;
            Timestamp createdAt = Timestamp.from(
                    lastMsg.toInstant().minus(rng.nextInt(60) + 1, ChronoUnit.DAYS));

            batch.add(new Object[]{
                    ORG_ID, PROJECT_ID, WABA_ID, contactId,
                    status, assignType, assignId,
                    lastMsg, direction, randSentence(),
                    unread, openUntil,
                    direction.equals("INBOUND")  ? lastMsg : null,
                    direction.equals("OUTBOUND") ? lastMsg : null,
                    createdAt, lastMsg
            });

            if (batch.size() >= BATCH_SIZE) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
                logProgress("conversations", i + 1, NUM_CONVERSATIONS, t0);
            }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(sql, batch);

        List<Long> ids = jdbc.queryForList("SELECT id FROM conversations ORDER BY id", Long.class);
        log.info("\n  ✓ {} conversations ready", ids.size());
        return ids;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STEP 4 — MESSAGES + ROLLUP
    // ══════════════════════════════════════════════════════════════════════

    private Map<Long, Long> seedMessages(List<Long> convIds, int numMessages) {
        log.info("[4/5] Seeding {} messages + rollup...", numMessages);
        long t0 = System.currentTimeMillis();

        // Load contact_id per conv (need it for messages.contact_id)
        Map<Long, Long> convContactMap = new HashMap<>();
        jdbc.query("SELECT id, contact_id FROM conversations",
                (RowCallbackHandler) rs -> convContactMap.put(
                        rs.getLong("id"), rs.getLong("contact_id")));

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

        Map<Long, Long> lastMsgPerConv = new HashMap<>();
        List<Object[]>  msgBatch    = new ArrayList<>(BATCH_SIZE);
        List<Object[]>  rollupBatch = new ArrayList<>(BATCH_SIZE);
        int done = 0;
        Timestamp now = Timestamp.from(Instant.now());

        // Disable FK checks for speed
        jdbc.execute("SET foreign_key_checks=0");
        jdbc.execute("SET unique_checks=0");

        for (int i = 0; i < numMessages; i++) {
            long convId    = pick(convIds);
            long contactId = convContactMap.getOrDefault(convId, 1L);
            String msgType  = pick(MSG_TYPES);   // ← uses dynamically loaded valid values
            String direction= pick(DIRECTIONS);
            String status   = pick(MSG_STATUSES);
            String byType   = pick(BY_TYPES);
            Timestamp ca    = daysAgo(0, 365);

            boolean isTmpl = msgType.equals("TEMPLATE");
            boolean isSent = status.equals("SENT") || status.equals("DELIVERED") || status.equals("READ");
            boolean isDel  = status.equals("DELIVERED") || status.equals("READ");
            boolean isRead = status.equals("READ");
            boolean isFail = status.equals("FAILED");

            Timestamp sentAt      = isSent ? plusSeconds(ca, rng.nextInt(30)  + 1)  : null;
            Timestamp deliveredAt = isDel  ? plusSeconds(ca, rng.nextInt(90)  + 30) : null;
            Timestamp readAt      = isRead ? plusSeconds(ca, rng.nextInt(480) + 120): null;

            msgBatch.add(new Object[]{
                    UUID.randomUUID().toString(), ORG_ID, PROJECT_ID, convId,
                    WABA_ID, contactId,
                    direction, msgType,
                    isTmpl ? pick(TEMPLATE_NAMES) : null,
                    isTmpl ? "en" : null,
                    isTmpl ? "{\"body\":[[\"John\",\"ORD-12345\",\"Tomorrow\"]]}" : null,
                    msgType.equals("TEXT") ? randSentence() : null,
                    "wamid." + UUID.randomUUID().toString().replace("-","").substring(0, 24),
                    status,
                    byType, byType.equals("USER") ? (long)(rng.nextInt(100) + 1) : null,
                    ca, sentAt, deliveredAt, readAt
            });

            // placeholder — we fill message_id after batch insert
            rollupBatch.add(new Object[]{0L, isSent?1:0, isDel?1:0, isRead?1:0, isFail?1:0, now});
            lastMsgPerConv.put(convId, (long) i); // temp; updated after insert

            if (msgBatch.size() >= BATCH_SIZE) {
                flushMessageBatch(msgBatch, rollupBatch, msgSql, rollupSql, lastMsgPerConv);
                done += msgBatch.size();
                msgBatch.clear();
                rollupBatch.clear();
                logProgress("messages", done, numMessages, t0);
            }
        }

        // Final batch
        if (!msgBatch.isEmpty()) {
            flushMessageBatch(msgBatch, rollupBatch, msgSql, rollupSql, lastMsgPerConv);
            done += msgBatch.size();
        }

        jdbc.execute("SET foreign_key_checks=1");
        jdbc.execute("SET unique_checks=1");

        log.info("\n  ✓ {} messages + rollup seeded", done);
        return lastMsgPerConv;
    }

    private void flushMessageBatch(
            List<Object[]> msgBatch,
            List<Object[]> rollupBatch,
            String msgSql, String rollupSql,
            Map<Long, Long> lastMsgPerConv
    ) {
        // Insert messages
        jdbc.batchUpdate(msgSql, msgBatch);

        // Get the last inserted ID range
        Long maxId = jdbc.queryForObject("SELECT MAX(id) FROM messages", Long.class);

        if (maxId == null) return;

        long firstId = maxId - msgBatch.size() + 1;

        // Build rollup with actual IDs
        List<Object[]> actualRollup = new ArrayList<>(msgBatch.size());
        for (int j = 0; j < msgBatch.size(); j++) {
            long mid    = firstId + j;
            Object[] m  = msgBatch.get(j);
            Object[] r  = rollupBatch.get(j);
            long convId = (long) m[3];  // conversation_id is index 3

            lastMsgPerConv.put(convId, mid);
            actualRollup.add(new Object[]{ mid, r[1], r[2], r[3], r[4], r[5] });
        }

        jdbc.batchUpdate(rollupSql, actualRollup);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STEP 5 — BACKFILL last_message_id
    // ══════════════════════════════════════════════════════════════════════

    private void backfillLastMessageId(Map<Long, Long> lastMsgPerConv) {
        log.info("[5/5] Back-filling last_message_id on {} conversations...", lastMsgPerConv.size());
        long t0 = System.currentTimeMillis();

        String sql = "UPDATE conversations SET last_message_id=? WHERE id=?";

        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int done = 0;

        for (Map.Entry<Long, Long> e : lastMsgPerConv.entrySet()) {
            batch.add(new Object[]{ e.getValue(), e.getKey() });

            if (batch.size() >= BATCH_SIZE) {
                jdbc.batchUpdate(sql, batch);
                done += batch.size();
                batch.clear();
                logProgress("backfill", done, lastMsgPerConv.size(), t0);
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(sql, batch);
        }

        log.info("\n  ✓ Back-fill complete");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private Timestamp daysAgo(int minDays, int maxDays) {
        long ms = (long)(rng.nextInt((maxDays - minDays) * 86400) + minDays * 86400L) * 1000;
        return Timestamp.from(Instant.now().minusMillis(ms));
    }

    private Timestamp plusSeconds(Timestamp base, int seconds) {
        return Timestamp.from(base.toInstant().plusSeconds(seconds));
    }

    private <T> T pick(T[] arr) {
        return arr[rng.nextInt(arr.length)];
    }

    private long pick(List<Long> list) {
        return list.get(rng.nextInt(list.size()));
    }

    private String randSentence() {
        int len = rng.nextInt(6) + 3;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(' ');
            sb.append(pick(WORDS));
        }
        return sb.toString();
    }

    private void logProgress(String label, int done, int total, long t0) {
        double elapsed = (System.currentTimeMillis() - t0) / 1000.0;
        double rate    = done / elapsed;
        double eta     = (total - done) / rate;
        double pct     = (done * 100.0) / total;
        log.info("  [{}] {}/{} ({:.1f}%)  {:.0f} rows/s  ETA {:.0f}s",
                label, done, total, pct, rate, eta);
    }
}