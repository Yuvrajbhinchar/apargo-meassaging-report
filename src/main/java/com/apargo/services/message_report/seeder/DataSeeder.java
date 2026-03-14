package com.apargo.services.message_report.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  Production-Scale Data Seeder — 1 Crore (10,000,000) Messages
 *  Multi-threaded: 8 worker threads on a 4-core CPU (hyper-threaded).
 * ══════════════════════════════════════════════════════════════════════════════
 *
 *  HOW TO RUN:
 *  ──────────
 *    Full seed (contacts + conversations + messages):
 *      --seed
 *
 *    Messages only (reuse existing contacts/conversations):
 *      --seed --messages-only
 *
 *    Quick smoke test (100k messages only):
 *      --seed --messages-only --quick
 *
 *  Terminal:
 *    mvn spring-boot:run "-Dspring-boot.run.arguments=--seed --messages-only"
 *
 *  ══════════════════════════════════════════════════════════════════════════
 *  THREADING MODEL:
 *  ────────────────
 *  • totalMessages is split evenly across NUM_THREADS workers.
 *  • Each worker holds ONE dedicated JDBC Connection for its entire lifetime
 *    so SET foreign_key_checks=0 stays pinned to that connection.
 *  • Generated keys (message IDs) are retrieved via
 *    PreparedStatement.RETURN_GENERATED_KEYS — no SELECT MAX(id) race condition.
 *  • AtomicLong tracks global insertion count; a monitor thread logs
 *    progress + rows/sec + ETA every 10 seconds.
 *  • After all workers finish, lastMsgPerConv maps are merged and
 *    backfill + verification run on the main thread.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final JdbcTemplate      jdbc;
    private final DataSource        dataSource;   // ← needed for per-thread connections
    private final ApplicationContext ctx;

    // ══════════════════════════════════════════════════════════════════════
    //  CONFIG
    // ══════════════════════════════════════════════════════════════════════

    /** Worker threads — 2× physical cores for I/O-bound JDBC work */
    private static final int NUM_THREADS           =      8;

    /** Total messages in --messages-only full run */
    private static final int TARGET_MESSAGES_FULL  = 10_000_000;

    /** Total messages in --quick run */
    private static final int TARGET_MESSAGES_QUICK =    100_000;

    /**
     * Rows per executeBatch() call inside each worker.
     * Smaller = more frequent commits + less memory; larger = faster throughput.
     * 5 000 is a good balance for MySQL.
     */
    private static final int DB_BATCH_SIZE         =      5_000;

    private static final int NUM_CONTACTS          =     50_000;
    private static final int NUM_CONVERSATIONS     =    100_000;

    private static final long ORG_ID     = 1L;
    private static final long PROJECT_ID = 1L;
    private static final long WABA_ID    = 1L;

    // ══════════════════════════════════════════════════════════════════════
    //  SQL TEMPLATES (shared across threads — read-only)
    // ══════════════════════════════════════════════════════════════════════

    private static final String MSG_SQL = """
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

    private static final String ROLLUP_SQL = """
            INSERT INTO message_status_rollup
              (message_id, is_sent, is_delivered, is_read, is_failed, last_updated_at)
            VALUES (?,?,?,?,?,?)
            """;

    // ══════════════════════════════════════════════════════════════════════
    //  RANDOM DATA POOLS  (immutable → safe to share across threads)
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
    private static final String[] STATUSES       = {"OPEN","OPEN","OPEN","OPEN","CLOSED","ARCHIVED"};
    private static final String[] ASSIGNED_TYPES = {"UNASSIGNED","USER","USER","TEAM"};
    private static final String[] DIRECTIONS     = {"INBOUND","OUTBOUND"};
    private static final String[] MSG_STATUSES   = {"SENT","DELIVERED","READ","READ","READ","FAILED"};
    private static final String[] BY_TYPES       = {"USER","USER","USER","SYSTEM","AUTOMATION","CAMPAIGN"};
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
    private static final String[] SOURCES = {"MANUAL","IMPORT","INTEGRATION","INBOUND"};

    /**
     * Detected dynamically from DB; set once in initEnumValues() before any thread starts.
     * Threads read it but never write — safe without synchronization.
     */
    private volatile String[] MSG_TYPES;

    // ══════════════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void run(String... args) throws Exception {
        List<String> argList = Arrays.asList(args);
        if (!argList.contains("--seed")) return;

        initEnumValues();

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
    //  FULL SEED
    // ══════════════════════════════════════════════════════════════════════

    private void runFullSeed(int totalMessages) {
        seedTemplates();
        List<Long> contactIds = seedContacts();
        if (contactIds.isEmpty()) { log.error("❌ No contacts seeded. Aborting."); return; }

        List<Long> convIds = seedConversations(contactIds);
        if (convIds.isEmpty()) { log.error("❌ No conversations seeded. Aborting."); return; }

        seedMessagesParallel(convIds, totalMessages);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MESSAGES ONLY
    // ══════════════════════════════════════════════════════════════════════

    private void runMessagesOnly(int totalMessages) {
        log.info("━━━ Loading existing conversation IDs from DB...");
        List<Long> convIds = jdbc.queryForList(
                "SELECT id FROM conversations WHERE project_id = ?", Long.class, PROJECT_ID);

        if (convIds.isEmpty()) {
            log.error("❌ No conversations found for project_id={}. Run full seed first.", PROJECT_ID);
            return;
        }

        Long existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE project_id = ?", Long.class, PROJECT_ID);
        log.info("  ✓ Found {} existing conversations", fmt(convIds.size()));
        log.info("  ℹ Existing messages: {}", fmt(existing != null ? existing : 0));
        log.info("  ℹ Inserting {} more messages across {} threads", fmt(totalMessages), NUM_THREADS);

        seedMessagesParallel(convIds, totalMessages);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PARALLEL MESSAGE SEEDING  ← main entry for threaded work
    // ══════════════════════════════════════════════════════════════════════

    private void seedMessagesParallel(List<Long> convIds, int totalMessages) {
        if (convIds == null || convIds.isEmpty()) {
            log.error("❌ convIds is empty — cannot seed messages.");
            return;
        }

        // Pre-load contact map once; all threads share it read-only.
        Map<Long, Long> convContactMap = loadConvContactMap();

        int perThread    = totalMessages / NUM_THREADS;
        int lastThreadEx = totalMessages - perThread * (NUM_THREADS - 1); // handles remainder

        log.info("");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  Threads       : {}", NUM_THREADS);
        log.info("  Target total  : {}", fmt(totalMessages));
        log.info("  Per thread    : {} (last thread: {})", fmt(perThread), fmt(lastThreadEx));
        log.info("  DB batch size : {}", fmt(DB_BATCH_SIZE));
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        AtomicLong globalInserted = new AtomicLong(0);
        long globalStart          = System.currentTimeMillis();

        // ── Progress monitor (logs every 10 s) ───────────────────────────
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(
                r -> { Thread t = new Thread(r, "seeder-monitor"); t.setDaemon(true); return t; }
        );
        monitor.scheduleAtFixedRate(() -> {
            long   ins = globalInserted.get();
            double pct = ins * 100.0 / totalMessages;
            double sec = (System.currentTimeMillis() - globalStart) / 1000.0;
            double rps = sec > 0 ? ins / sec : 0;
            double eta = rps > 0 ? (totalMessages - ins) / rps : 0;
            log.info("  📊 {}/{} ({}) | {} rows/s | ETA: {}",
                    fmt(ins), fmt(totalMessages),
                    String.format("%.1f%%", pct),
                    String.format("%.0f", rps),
                    formatEta((long) eta));
        }, 10, 10, TimeUnit.SECONDS);

        // ── Submit workers ────────────────────────────────────────────────
        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS,
                r -> { Thread t = new Thread(r, "seeder-worker-" + r.hashCode()); return t; });

        List<Callable<Map<Long, Long>>> tasks = new ArrayList<>();
        for (int t = 0; t < NUM_THREADS; t++) {
            final int threadId     = t + 1;
            final int threadTarget = (t == NUM_THREADS - 1) ? lastThreadEx : perThread;

            tasks.add(() -> insertWorker(
                    threadId, convIds, convContactMap, threadTarget, globalInserted));
        }

        List<Future<Map<Long, Long>>> futures;
        try {
            futures = pool.invokeAll(tasks);   // blocks until all workers finish
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Seeder interrupted: {}", e.getMessage());
            pool.shutdownNow();
            monitor.shutdownNow();
            return;
        } finally {
            pool.shutdown();
            monitor.shutdownNow();
        }

        // ── Merge lastMsgPerConv from all workers ─────────────────────────
        Map<Long, Long> globalLastMsg = new HashMap<>();
        int workersFailed = 0;
        for (int i = 0; i < futures.size(); i++) {
            try {
                futures.get(i).get().forEach((convId, msgId) ->
                        globalLastMsg.merge(convId, msgId, Math::max));
            } catch (Exception e) {
                workersFailed++;
                log.error("Worker {} threw exception: {}", i + 1, e.getMessage(), e);
            }
        }

        long elapsed = (System.currentTimeMillis() - globalStart) / 1000;
        log.info("");
        log.info("━━━ All {} threads done in {} | Total inserted: {} | Workers failed: {}",
                NUM_THREADS, formatEta(elapsed), fmt(globalInserted.get()), workersFailed);

        backfillLastMessageId(globalLastMsg);
        verifyInsertedData();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  WORKER — runs on its own thread with a dedicated JDBC connection
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Each worker:
     *  1. Borrows ONE connection from the pool for its entire run.
     *  2. Disables FK + unique checks on that connection (session-scoped).
     *  3. Inserts messages in DB_BATCH_SIZE batches.
     *  4. Retrieves generated IDs via RETURN_GENERATED_KEYS (no MAX(id) race).
     *  5. Inserts rollup rows using those exact IDs.
     *  6. Re-enables FK + unique checks before returning the connection.
     *
     * @return map of conversationId → highest messageId inserted by this worker
     */
    private Map<Long, Long> insertWorker(
            int             threadId,
            List<Long>      convIds,
            Map<Long, Long> convContactMap,
            int             targetCount,
            AtomicLong      globalInserted
    ) {
        Map<Long, Long> lastMsgPerConv = new HashMap<>();
        // Each thread has its own Random — ThreadLocalRandom would also work
        Random rng = new Random();

        log.info("  [T{}] Starting — target: {} messages", threadId, fmt(targetCount));
        long threadStart = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);   // auto-commit per executeBatch() call

            // ── Disable FK + unique checks for this session ───────────────
            try (Statement st = conn.createStatement()) {
                st.execute("SET foreign_key_checks=0");
                st.execute("SET unique_checks=0");
            }

            List<Object[]> msgBatch    = new ArrayList<>(DB_BATCH_SIZE);
            List<Object[]> rollupCache = new ArrayList<>(DB_BATCH_SIZE);
            int localInserted          = 0;

            for (int i = 0; i < targetCount; i++) {
                long convId    = pickFromList(convIds, rng);
                long contactId = convContactMap.getOrDefault(convId, 1L);

                String    msgType   = pickFromArray(MSG_TYPES, rng);
                String    direction = pickFromArray(DIRECTIONS, rng);
                String    status    = pickFromArray(MSG_STATUSES, rng);
                String    byType    = pickFromArray(BY_TYPES, rng);
                Timestamp ca        = randomTimestamp(rng);

                boolean isTmpl  = "TEMPLATE".equals(msgType);
                boolean isSent  = status.equals("SENT")      || status.equals("DELIVERED") || status.equals("READ");
                boolean isDel   = status.equals("DELIVERED") || status.equals("READ");
                boolean isRead  = status.equals("READ");
                boolean isFail  = status.equals("FAILED");

                Timestamp sentAt      = isSent ? plusSeconds(ca, rng.nextInt(30)  + 1)   : null;
                Timestamp deliveredAt = isDel  ? plusSeconds(ca, rng.nextInt(90)  + 30)  : null;
                Timestamp readAt      = isRead ? plusSeconds(ca, rng.nextInt(480) + 120) : null;
                Timestamp now         = Timestamp.from(Instant.now());

                msgBatch.add(new Object[]{
                        UUID.randomUUID().toString(),     // 1  uuid
                        ORG_ID,                           // 2  organization_id
                        PROJECT_ID,                       // 3  project_id
                        convId,                           // 4  conversation_id
                        WABA_ID,                          // 5  waba_account_id
                        contactId,                        // 6  contact_id
                        direction,                        // 7  direction
                        msgType,                          // 8  message_type
                        isTmpl ? pickFromArray(TEMPLATE_NAMES, rng) : null,  // 9  template_name
                        isTmpl ? "en" : null,             // 10 template_language
                        isTmpl ? randomTemplateVars(rng) : null,             // 11 template_vars
                        "TEXT".equals(msgType) ? randSentence(rng) : null,   // 12 body_text
                        "wamid." + shortUuid(rng),        // 13 provider_message_id
                        status,                           // 14 status
                        byType,                           // 15 created_by_type
                        "USER".equals(byType) ? (long)(rng.nextInt(100) + 1) : null, // 16 created_by_id
                        ca,                               // 17 created_at
                        sentAt,                           // 18 sent_at
                        deliveredAt,                      // 19 delivered_at
                        readAt                            // 20 read_at
                });

                // Store rollup flags; message_id filled after INSERT returns generated keys
                rollupCache.add(new Object[]{
                        isSent ? 1 : 0,   // is_sent
                        isDel  ? 1 : 0,   // is_delivered
                        isRead ? 1 : 0,   // is_read
                        isFail ? 1 : 0,   // is_failed
                        now               // last_updated_at
                });

                if (msgBatch.size() >= DB_BATCH_SIZE) {
                    int flushed = flushBatchOnConnection(conn, msgBatch, rollupCache, lastMsgPerConv);
                    localInserted += flushed;
                    globalInserted.addAndGet(flushed);
                    msgBatch.clear();
                    rollupCache.clear();

                    // Sub-progress every 10 batches
                    if ((localInserted / DB_BATCH_SIZE) % 10 == 0) {
                        log.info("  [T{}] {}/{}", threadId, fmt(localInserted), fmt(targetCount));
                    }
                }
            }

            // Flush tail batch
            if (!msgBatch.isEmpty()) {
                int flushed = flushBatchOnConnection(conn, msgBatch, rollupCache, lastMsgPerConv);
                localInserted += flushed;
                globalInserted.addAndGet(flushed);
            }

            // ── Re-enable FK + unique checks ──────────────────────────────
            try (Statement st = conn.createStatement()) {
                st.execute("SET foreign_key_checks=1");
                st.execute("SET unique_checks=1");
            }

            double sec = (System.currentTimeMillis() - threadStart) / 1000.0;
            log.info("  [T{}] ✓ Finished {} messages in {}s ({} rows/s)",
                    threadId, fmt(localInserted),
                    String.format("%.1f", sec),
                    String.format("%.0f", localInserted / sec));

        } catch (SQLException e) {
            log.error("  [T{}] ❌ JDBC error: {}", threadId, e.getMessage(), e);
        }

        return lastMsgPerConv;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FLUSH ONE BATCH ON A GIVEN CONNECTION
    //  Uses RETURN_GENERATED_KEYS — no SELECT MAX(id) race condition.
    // ══════════════════════════════════════════════════════════════════════

    /**
     * @return number of message rows actually inserted
     */
    private int flushBatchOnConnection(
            Connection      conn,
            List<Object[]>  msgBatch,
            List<Object[]>  rollupCache,
            Map<Long, Long> lastMsgPerConv
    ) throws SQLException {

        List<Long> generatedIds = new ArrayList<>(msgBatch.size());

        // ── Insert messages + collect generated PKs ───────────────────────
        try (PreparedStatement ps =
                     conn.prepareStatement(MSG_SQL, Statement.RETURN_GENERATED_KEYS)) {

            for (Object[] row : msgBatch) {
                ps.setObject(1,  row[0]);   // uuid
                ps.setObject(2,  row[1]);   // organization_id
                ps.setObject(3,  row[2]);   // project_id
                ps.setObject(4,  row[3]);   // conversation_id
                ps.setObject(5,  row[4]);   // waba_account_id
                ps.setObject(6,  row[5]);   // contact_id
                ps.setObject(7,  row[6]);   // direction
                ps.setObject(8,  row[7]);   // message_type
                ps.setObject(9,  row[8]);   // template_name
                ps.setObject(10, row[9]);   // template_language
                ps.setObject(11, row[10]);  // template_vars
                ps.setObject(12, row[11]);  // body_text
                ps.setObject(13, row[12]);  // provider_message_id
                ps.setObject(14, row[13]);  // status
                ps.setObject(15, row[14]);  // created_by_type
                ps.setObject(16, row[15]);  // created_by_id
                ps.setObject(17, row[16]);  // created_at
                ps.setObject(18, row[17]);  // sent_at
                ps.setObject(19, row[18]);  // delivered_at
                ps.setObject(20, row[19]);  // read_at
                ps.addBatch();
            }

            ps.executeBatch();

            // Collect actual auto-increment IDs assigned by MySQL
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next()) {
                    generatedIds.add(rs.getLong(1));
                }
            }
        }

        // ── Update lastMsgPerConv ─────────────────────────────────────────
        for (int j = 0; j < generatedIds.size() && j < msgBatch.size(); j++) {
            long msgId  = generatedIds.get(j);
            long convId = ((Number) msgBatch.get(j)[3]).longValue();   // conversation_id at index 3
            lastMsgPerConv.merge(convId, msgId, Math::max);
        }

        // ── Insert rollup rows with exact message IDs ─────────────────────
        try (PreparedStatement rps = conn.prepareStatement(ROLLUP_SQL)) {
            for (int j = 0; j < generatedIds.size() && j < rollupCache.size(); j++) {
                Object[] r = rollupCache.get(j);
                rps.setLong(1,   generatedIds.get(j));  // message_id
                rps.setObject(2, r[0]);                  // is_sent
                rps.setObject(3, r[1]);                  // is_delivered
                rps.setObject(4, r[2]);                  // is_read
                rps.setObject(5, r[3]);                  // is_failed
                rps.setObject(6, r[4]);                  // last_updated_at
                rps.addBatch();
            }
            rps.executeBatch();
        }

        return generatedIds.size();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BACKFILL last_message_id
    // ══════════════════════════════════════════════════════════════════════

    private void backfillLastMessageId(Map<Long, Long> lastMsgPerConv) {
        if (lastMsgPerConv.isEmpty()) return;

        log.info("  Backfilling last_message_id for {} conversations...", fmt(lastMsgPerConv.size()));
        String sql = "UPDATE conversations SET last_message_id=?, updated_at=NOW() WHERE id=?";
        List<Object[]> batch = new ArrayList<>(DB_BATCH_SIZE);

        for (Map.Entry<Long, Long> e : lastMsgPerConv.entrySet()) {
            batch.add(new Object[]{ e.getValue(), e.getKey() });
            if (batch.size() >= DB_BATCH_SIZE) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(sql, batch);

        log.info("  ✓ Backfill done");
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
                    ORG_ID, PROJECT_ID, String.valueOf(WABA_ID), t[0], t[1], t[2], ca, ca);

            Long tid = jdbc.queryForObject(
                    "SELECT id FROM whatsapp_templates WHERE name=? AND project_id=?",
                    Long.class, t[0], PROJECT_ID);

            if (tid == null) continue;
            jdbc.update("""
                INSERT IGNORE INTO whatsapp_template_components
                  (template_id, component_type, format, text, component_order, created_at)
                VALUES (?,'BODY','TEXT',?,0,?)
                """, tid, "Hi {{1}}, this is a message about your " + t[0] + ".", ca);
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
                """, ORG_ID, PROJECT_ID, String.valueOf(WABA_ID), name, ca, ca);

            Long tid = jdbc.queryForObject(
                    "SELECT id FROM whatsapp_templates WHERE name=? AND project_id=?",
                    Long.class, name, PROJECT_ID);

            if (tid != null) {
                jdbc.update("""
                    INSERT IGNORE INTO whatsapp_template_components
                      (template_id, component_type, format, text, component_order, created_at)
                    VALUES (?,'BODY','TEXT',?,0,?)
                    """, tid, "Hello {{1}}, update regarding " + name + ".", ca);
            }
        }

        log.info("  ✓ Templates seeded");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONTACTS
    // ══════════════════════════════════════════════════════════════════════

    private List<Long> seedContacts() {
        Long existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM contacts WHERE organization_id=?", Long.class, ORG_ID);
        if (existing != null && existing > 0) {
            log.info("━━━ [2/4] Found {} existing contacts — skipping", fmt(existing));
            return jdbc.queryForList(
                    "SELECT id FROM contacts WHERE organization_id=?", Long.class, ORG_ID);
        }

        log.info("━━━ [2/4] Seeding {} contacts...", fmt(NUM_CONTACTS));
        Random rng = new Random();
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
            String name  = pickFromArray(FIRST_NAMES, rng) + " " + pickFromArray(LAST_NAMES, rng);
            Timestamp ca = daysAgo(180, 730);
            batch.add(new Object[]{
                    ORG_ID, phone, phone.replace("+", ""), name,
                    pickFromArray(SOURCES, rng), ca, daysAgo(0, 30), ca, ca
            });
            if (batch.size() >= DB_BATCH_SIZE) { jdbc.batchUpdate(sql, batch); batch.clear(); }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(sql, batch);

        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM contacts WHERE organization_id=?", Long.class, ORG_ID);
        log.info("  ✓ {} contacts ready ({}s)",
                fmt(ids.size()), String.format("%.1f", (System.currentTimeMillis()-t0)/1000.0));
        return ids;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONVERSATIONS
    // ══════════════════════════════════════════════════════════════════════

    private List<Long> seedConversations(List<Long> contactIds) {
        if (contactIds == null || contactIds.isEmpty()) {
            log.error("❌ contactIds is empty — cannot seed conversations.");
            return Collections.emptyList();
        }

        Long existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversations WHERE project_id=?", Long.class, PROJECT_ID);
        if (existing != null && existing > 0) {
            log.info("━━━ [3/4] Found {} existing conversations — skipping", fmt(existing));
            return jdbc.queryForList(
                    "SELECT id FROM conversations WHERE project_id=?", Long.class, PROJECT_ID);
        }

        log.info("━━━ [3/4] Seeding {} conversations...", fmt(NUM_CONVERSATIONS));
        Random rng = new Random();
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
            long      contactId  = pickFromList(contactIds, rng);
            String    status     = pickFromArray(STATUSES, rng);
            String    assignType = pickFromArray(ASSIGNED_TYPES, rng);
            Long      assignId   = "UNASSIGNED".equals(assignType) ? null : (long)(rng.nextInt(50)+1);
            Timestamp lastMsg    = daysAgo(0, 180);
            String    direction  = pickFromArray(DIRECTIONS, rng);
            int       unread     = "OPEN".equals(status) ? rng.nextInt(25) : 0;
            Timestamp openUntil  = "INBOUND".equals(direction)
                    ? Timestamp.from(lastMsg.toInstant().plus(24, ChronoUnit.HOURS)) : null;
            Timestamp createdAt  = Timestamp.from(
                    lastMsg.toInstant().minus(rng.nextInt(60)+1, ChronoUnit.DAYS));

            batch.add(new Object[]{
                    ORG_ID, PROJECT_ID, WABA_ID, contactId,
                    status, assignType, assignId,
                    lastMsg, direction, randSentence(rng),
                    unread, openUntil,
                    "INBOUND".equals(direction)  ? lastMsg : null,
                    "OUTBOUND".equals(direction) ? lastMsg : null,
                    createdAt, lastMsg
            });
            if (batch.size() >= DB_BATCH_SIZE) { jdbc.batchUpdate(sql, batch); batch.clear(); }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(sql, batch);

        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM conversations WHERE project_id=?", Long.class, PROJECT_ID);
        log.info("  ✓ {} conversations ready ({}s)",
                fmt(ids.size()), String.format("%.1f", (System.currentTimeMillis()-t0)/1000.0));
        return ids;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DETECT message_type ENUM FROM DB
    // ══════════════════════════════════════════════════════════════════════

    private void initEnumValues() {
        try {
            String enumDef = jdbc.queryForObject(
                    "SELECT COLUMN_TYPE FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='messages' AND COLUMN_NAME='message_type'",
                    String.class);

            if (enumDef != null && enumDef.toLowerCase().startsWith("enum(")) {
                String inner = enumDef.substring(5, enumDef.length() - 1);
                Set<String> valid = new LinkedHashSet<>();
                for (String token : inner.split(",")) valid.add(token.replace("'","").trim().toUpperCase());

                List<String> weighted = new ArrayList<>();
                String[] preferred = {
                        "TEXT","TEXT","TEXT","TEMPLATE","TEMPLATE",
                        "IMAGE","DOCUMENT","AUDIO","VIDEO","INTERACTIVE","REACTION","SYSTEM"
                };
                for (String v : preferred) if (valid.contains(v)) weighted.add(v);
                if (weighted.isEmpty()) weighted.addAll(valid);

                MSG_TYPES = weighted.toArray(new String[0]);
                log.info("  MSG_TYPES pool (weighted): {}", Arrays.toString(MSG_TYPES));
            } else {
                MSG_TYPES = new String[]{"TEXT"};
            }
        } catch (Exception e) {
            log.warn("  Could not detect message_type ENUM ({}). Fallback: TEXT only.", e.getMessage());
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
            log.info("  contacts              : {}", fmt(safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM contacts",                    Long.class))));
            log.info("  conversations (total) : {}", fmt(safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM conversations",               Long.class))));
            log.info("  conversations (OPEN)  : {}", fmt(safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE status='OPEN'", Long.class))));
            log.info("  messages (total)      : {}", fmt(safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM messages",                   Long.class))));
            log.info("  messages TEXT         : {}", fmt(safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE message_type='TEXT'",     Long.class))));
            log.info("  messages TEMPLATE     : {}", fmt(safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE message_type='TEMPLATE'", Long.class))));
            log.info("  message_status_rollup : {}", fmt(safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM message_status_rollup",      Long.class))));
            log.info("  whatsapp_templates    : {}", fmt(safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM whatsapp_templates",         Long.class))));

            long msgs   = safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class));
            long rollup = safe(() -> jdbc.queryForObject("SELECT COUNT(*) FROM message_status_rollup", Long.class));
            if (msgs != rollup)
                log.warn("  ⚠ messages ({}) != rollup ({}) — rollup rows may be missing", fmt(msgs), fmt(rollup));
            else
                log.info("  ✓ messages == rollup — counts match perfectly");
        } catch (Exception e) {
            log.warn("  Could not complete verification: {}", e.getMessage());
        }
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LOAD CONVERSATION → CONTACT MAP
    // ══════════════════════════════════════════════════════════════════════

    private Map<Long, Long> loadConvContactMap() {
        log.info("  Loading conversation→contact map...");
        Map<Long, Long> map = new HashMap<>();
        jdbc.query("SELECT id, contact_id FROM conversations WHERE project_id=?",
                ps -> ps.setLong(1, PROJECT_ID),
                (RowCallbackHandler) rs -> map.put(rs.getLong("id"), rs.getLong("contact_id")));
        log.info("  ✓ Loaded {} conversation entries", fmt(map.size()));
        return map;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS  — each takes its own Random so threads don't share state
    // ══════════════════════════════════════════════════════════════════════

    /** daysAgo helper uses main-thread Random (only called before threads start) */
    private Timestamp daysAgo(int minDays, int maxDays) {
        if (maxDays <= minDays) maxDays = minDays + 1;
        long ms = ((long)(new Random().nextInt((maxDays-minDays)*86400)) + (long)(minDays*86400L))*1000L;
        return Timestamp.from(Instant.now().minusMillis(ms));
    }

    private Timestamp randomTimestamp(Random rng) {
        int maxDays = rng.nextInt(100) < 70 ? 90 : 365;
        int maxSec  = maxDays * 86400;
        return Timestamp.from(Instant.now().minusMillis((long)rng.nextInt(maxSec) * 1000L));
    }

    private Timestamp plusSeconds(Timestamp base, int seconds) {
        return Timestamp.from(base.toInstant().plusSeconds(seconds));
    }

    private String pickFromArray(String[] arr, Random rng) {
        if (arr == null || arr.length == 0) throw new IllegalStateException("Empty array");
        return arr[rng.nextInt(arr.length)];
    }

    private long pickFromList(List<Long> list, Random rng) {
        if (list == null || list.isEmpty())
            throw new IllegalStateException("Cannot pick from empty list");
        return list.get(rng.nextInt(list.size()));
    }

    private String randSentence(Random rng) {
        int len = rng.nextInt(8) + 3;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(' ');
            sb.append(pickFromArray(WORDS, rng));
        }
        return sb.toString();
    }

    private String shortUuid(Random rng) {
        return UUID.randomUUID().toString().replace("-","").substring(0, 24);
    }

    private String randomTemplateVars(Random rng) {
        String[] names   = {"John","Priya","Rahul","Meena","Amit","Sunita","Raj","Kavita"};
        String[] orders  = {"ORD-1234","ORD-5678","ORD-9012","ORD-3456","INV-7890"};
        String[] dates   = {"Tomorrow","Today","In 2 days","This Friday","Monday"};
        String[] amounts = {"INR500","INR1200","INR2500","INR499","INR999"};

        return switch (rng.nextInt(4)) {
            case 0 -> String.format("{\"body\":[[\"%s\",\"%s\",\"%s\"]]}",
                    pickFromArray(names,rng), pickFromArray(orders,rng), pickFromArray(dates,rng));
            case 1 -> String.format("{\"body\":[[\"%s\",\"%s\"]]}",
                    pickFromArray(names,rng), pickFromArray(amounts,rng));
            case 2 -> String.format("{\"body\":[[\"%s\"]]}", pickFromArray(names,rng));
            default -> "{\"body\":[[\"Customer\",\"ORD-0000\",\"Soon\"]]}";
        };
    }

    private String fmt(long n) { return String.format("%,d", n); }

    private String formatEta(long seconds) {
        if (seconds < 60)   return seconds + "s";
        if (seconds < 3600) return (seconds/60) + "m " + (seconds%60) + "s";
        return (seconds/3600) + "h " + ((seconds%3600)/60) + "m";
    }

    @FunctionalInterface private interface Supplier<T> { T get() throws Exception; }

    private long safe(Supplier<Long> s) {
        try { Long v = s.get(); return v != null ? v : 0L; } catch (Exception e) { return 0L; }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PRINT BANNER + SUMMARY
    // ══════════════════════════════════════════════════════════════════════

    private void printBanner(boolean quick, boolean messagesOnly, int totalMessages) {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║     Apargo DB Seeder — Production Scale (Multi-Thread)   ║");
        log.info("╠══════════════════════════════════════════════════════════╣");
        log.info("║  Mode      : {}", messagesOnly ? "MESSAGES ONLY" : "FULL SEED");
        log.info("║  Scale     : {}", quick ? "QUICK (100k)" : "FULL (1 crore)");
        log.info("║  Threads   : {} (on 4-core CPU)", NUM_THREADS);
        log.info("║  Target    : {}", fmt(totalMessages));
        log.info("║  DB batch  : {} rows/executeBatch", fmt(DB_BATCH_SIZE));
        log.info("╚══════════════════════════════════════════════════════════╝");
    }

    private void printSummary(long globalStart) {
        long elapsed = (System.currentTimeMillis() - globalStart) / 1000;
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  SEEDER COMPLETE  —  Total time: {} min {} sec", elapsed/60, elapsed%60);
        log.info("╠══════════════════════════════════════════════════════════╣");
        log.info("║  GET /api/chats/inbox?projectId=1&size=20                ║");
        log.info("║  GET /api/v1/get-messages-history?projectId=1            ║");
        log.info("║  GET /api/chats/conversation/{id}/messages               ║");
        log.info("╚══════════════════════════════════════════════════════════╝");
    }
}