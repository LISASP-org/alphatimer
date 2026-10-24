# Heat-Timing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Swing desktop app that records Quantum/Ares data over TCP into one SQLite project file (JPA/Hibernate), shows the live heat, and lets all former YAML settings be changed in the UI between recordings.

**Architecture:** Copied alphatimer protocol/refiner modules + new `heattiming-core` (settings, project document on a temp SQLite working DB, heat model, recording session, REST) + `heattiming-app` (JUtils-`JButtonBar` Swing UI). A `RecordingSession` builds a fresh pipeline per start from a settings snapshot; a `ProjectDocument` owns the working DB, dirty flag, save/auto-save.

**Tech Stack:** Java 25 (jutils is compiled for 25), Maven, Hibernate 6.6.45.Final (+ `hibernate-community-dialects`), `sqlite-jdbc` 3.53.2.0, Jackson, Lombok, JUnit 5/AssertJ (Spring Boot BOM 3.5.13 used **only** as a version BOM), `jutils-swing` 18.0.2, JDK `HttpServer`.

**Spec:** `docs/superpowers/specs/2026-10-16-heat-timing-design.md` (spec says Java 21; this plan uses 25 because `jutils-legacy` requires it — spec is amended in Task 1).

## Global Constraints

- No Spring / Spring Boot at runtime. The Boot BOM is imported in `dependencyManagement` for versions only.
- Group `org.lisasp.heattiming`, project dir `/workspace/eclipse-workspace/Heat-Timing`, own Git repo, branch `main` (work directly on `main`; repo has only the spec so far).
- Persistence is JPA via Hibernate native bootstrap (`Configuration` → `SessionFactory`), no `persistence.xml`; SQLite file extension `.heat`.
- Copied alphatimer code keeps its packages (`org.lisasp.alphatimer.*`); new code uses `org.lisasp.heattiming.core.*` / `org.lisasp.heattiming.app.*`.
- UI uses `de.df.jutils.gui.JButtonBar` (jutils-swing, **not** l2fprod), vertical, left; two panels: "Settings & Recording" and "Live Heat".
- All Swing updates on the EDT. Recording callbacks never throw into the reader thread.
- Settings are locked while recording. Defaults live in `~/.heat-timing/defaults.json`.
- Closing/New/Open: stop recording first, then Save/Discard/Cancel when dirty.
- Alphatimer's `data/*.serial` files contain event data: never commit or paste them. Test data = `DataHandlingMessageTestData` bytes only.
- Commit after each task; commit message ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Repo-local git identity is already set.

## Review Focus

- Garbled/unknown bytes or a message for a lane that does not exist must not kill the reader thread (session logs and continues).
- Stop → Start → Stop repeatedly (reader thread, REST port, DB use) leaves no stale threads and no "port in use".
- Save while data is arriving never produces a corrupt/half-written `.heat` file; a failed Save keeps the old file and the dirty flag.
- Opening a non-SQLite / foreign / empty file shows an error and leaves the current document untouched.
- Closing with recording active and unsaved data: recording stops first; Cancel keeps the app open and stopped.
- Invalid settings (blank host, port 0/70000, REST port in use) → ERROR state with message, no exception dialog crash.
- Two heats with the same number in different events, and a competition-key change between recordings, keep data separate.
- Heat with no finish time / DNS / DNF lanes shows no bogus placing; ties share a place.

## File Structure

```
Heat-Timing/
  pom.xml                                   parent (modules, BOM, versions)
  alphatimer-ares-serial-api/ ares-serial/ refinedmessages-api/ refinedmessages/ serial-api/ serial-tcp/   (copied, renamed dirs without "alphatimer-" prefix is NOT done: keep names)
  heattiming-core/
    src/main/java/org/lisasp/heattiming/core/
      db/        ProjectDatabase, RawDataChunkEntity, AresMessageEntity, HeatEntity, LaneEntity, LaneTimeEntity, SettingsEntity
      settings/  Settings, DefaultSettingsStore
      project/   ProjectDocument, AutoSaver
      heats/     HeatDto, LaneDto, HeatStatus, LaneStatus, Penalty, Heat, Lane, HeatStore, HeatService, RunningClock
      legacy/    LegacyService, LaneTimeStore, dto/{Heat,Lane,LaneStatus}, LegacySerialization, LegacyHttpServer
      recording/ RecordingSession, RecordingState, RawDataRecorder, MessageStore
  heattiming-app/
    src/main/java/org/lisasp/heattiming/app/
      Main, AppController, SavePrompt, TimeFormat, HeatTableModel, MainFrame, SettingsRecordingPanel, LiveHeatPanel
```

---

### Task 1: Repository skeleton and copied modules

**Files:**
- Create: `pom.xml`, `.gitignore`, copies of `alphatimer-{ares-serial-api,ares-serial,refinedmessages-api,refinedmessages,serial-api,serial-tcp}` from `../alphatimer`
- Modify: `docs/superpowers/specs/2026-10-16-heat-timing-design.md` (Java 21 → 25)
- Modify: `alphatimer-serial-api/.../SerialPortReader.java`, `alphatimer-serial-tcp/.../TcpReader.java`
- Test: `alphatimer-serial-tcp/src/test/java/org/lisasp/alphatimer/serial/tcp/TcpReaderTest.java`

**Interfaces:**
- Produces: `SerialPortReader` gains `default void start() {}` and `default boolean isConnected() { return true; }`; `TcpReader(String server, int port)` no longer starts a thread in the constructor — `start()` does; `close()` is idempotent and safe if never connected; `isConnected()` true while the socket is connected.

- [ ] **Step 1: Copy modules and write parent pom**

```bash
cd /workspace/eclipse-workspace/Heat-Timing
for m in ares-serial-api ares-serial refinedmessages-api refinedmessages serial-api serial-tcp; do
  cp -r ../alphatimer/alphatimer-$m ./alphatimer-$m; rm -rf ./alphatimer-$m/target ./alphatimer-$m/*.iml
done
```
Create `pom.xml` (packaging pom, `groupId org.lisasp.heattiming`, `artifactId heattiming-parent`, `1.0.0-SNAPSHOT`, **no `<parent>`**) with: modules (six copied + `heattiming-core` + `heattiming-app`, add the last two when they exist), properties `java.version=25`, `maven.compiler.release=25`, `project.build.sourceEncoding=UTF-8`, versions `basics.version=1.0.3`, `lombok.version=1.18.46`, `hibernate.version=6.6.45.Final`, `sqlite.version=3.53.2.0`, `jutils.version=18.0.2`; `dependencyManagement` importing `org.springframework.boot:spring-boot-dependencies:3.5.13` (type pom, scope import) plus every `org.lisasp.alphatimer:*` module at `${project.version}`, `org.lisasp:basics-jre|basics-notification:${basics.version}`, `org.xerial:sqlite-jdbc`, `org.hibernate.orm:hibernate-community-dialects`; `repositories` copied from `../alphatimer/pom.xml` (central + the java-basics GitHub Packages one); `maven-compiler-plugin` with Lombok annotation processor path; `maven-surefire-plugin` (BOM-managed). Copy plugin/lombok config and the copied modules' `groupId/version` handling from `../alphatimer/pom.xml`. In each copied module's `pom.xml`, replace `<parent>` by the new parent (`org.lisasp.heattiming:heattiming-parent`) and change `groupId` of the module to `org.lisasp.alphatimer` kept as is (so inter-module dependencies keep working).

`.gitignore`: `target/`, `*.iml`, `.idea/`, `*.work.sqlite`.

- [ ] **Step 2: Build copied modules with their tests**

Run: `mvn -q -B install` (from repo root)
Expected: BUILD SUCCESS, copied tests pass. If a module needs a dependency version previously supplied by the Boot parent, add it to `dependencyManagement`.

- [ ] **Step 3: Write the failing TcpReader test**

```java
package org.lisasp.alphatimer.serial.tcp;

import org.junit.jupiter.api.Test;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TcpReaderTest {

    @Test
    void doesNotConnectBeforeStartAndCloseIsSafe() {
        TcpReader reader = new TcpReader("localhost", 1);
        assertThat(reader.isConnected()).isFalse();
        reader.close();
        reader.close();
    }

    @Test
    void readsBytesAfterStartAndStopsAfterClose() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CopyOnWriteArrayList<Byte> received = new CopyOnWriteArrayList<>();
            TcpReader reader = new TcpReader("localhost", server.getLocalPort());
            reader.register(received::add);
            reader.start();
            try (Socket s = server.accept()) {
                OutputStream out = s.getOutputStream();
                out.write(new byte[]{1, 2, 3});
                out.flush();
                await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> assertThat(received).containsExactly((byte) 1, (byte) 2, (byte) 3));
                assertThat(reader.isConnected()).isTrue();
            }
            reader.close();
            reader.close();
            assertThat(reader.isConnected()).isFalse();
        }
    }

    @Test
    void canBeRecreatedOnTheSamePort() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            for (int i = 0; i < 3; i++) {
                TcpReader reader = new TcpReader("localhost", server.getLocalPort());
                reader.start();
                server.accept().close();
                reader.close();
            }
        }
    }
}
```
Add `org.awaitility:awaitility` (BOM-managed) and `assertj-core`/`junit-jupiter` test deps to `alphatimer-serial-tcp/pom.xml`.

- [ ] **Step 4: Run to verify failure**

Run: `mvn -q -B -pl alphatimer-serial-tcp test`
Expected: compile errors (`start`, `isConnected` missing).

- [ ] **Step 5: Implement**

`SerialPortReader`: add
```java
default void start() {}
default boolean isConnected() { return true; }
```
`TcpReader`: make `reader` a non-final `Thread`; constructor only stores fields (remove thread start and the log line moves to `start()`); add
```java
@Override
public synchronized void start() {
    if (reader != null) return;
    log.info("Starting reader at tcp://{}:{}", server, port);
    reader = new Thread(this::read, "tcp-reader-" + server + ":" + port);
    reader.setDaemon(true);
    reader.start();
}

@Override
public boolean isConnected() {
    Socket s = socket;
    return s != null && s.isConnected() && !s.isClosed();
}
```
Make `socket`/`input` `volatile`. `close()`: interrupt `reader` if non-null; close `input` and `socket` each guarded by `!= null` and try/catch; set both to `null`. In `read()`, `closeConnection()` must null-check `socket`, and the loop must exit when interrupted: catch `InterruptedException` in `sleep` (replace `@SneakyThrows` with try/catch that re-sets the interrupt flag) and `readOnce()` must stop when `input == null` (return) and when `amount < 0` (server closed → `closeConnection()`).

- [ ] **Step 6: Run tests, amend spec, commit**

Run: `mvn -q -B install` → all pass. In the spec replace "Java 21" with "Java 25 (required by jutils-legacy)".
```bash
git add -A && git commit -m "Add skeleton with copied alphatimer modules; make TcpReader restartable" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Core module and ProjectDatabase (SQLite + Hibernate risk proof)

**Files:**
- Create: `heattiming-core/pom.xml`, `.../db/ProjectDatabase.java`, `.../db/SettingsEntity.java` (minimal, used for the proof; finished in Task 3)
- Test: `heattiming-core/src/test/java/org/lisasp/heattiming/core/db/ProjectDatabaseTest.java`

**Interfaces:**
- Produces:
```java
public final class ProjectDatabase implements AutoCloseable {
    public static ProjectDatabase createNew(Path workDir) throws IOException;           // fresh empty work DB in workDir
    public static ProjectDatabase openCopy(Path projectFile, Path workDir) throws IOException; // copies file to workDir, validates it is SQLite
    public static ProjectDatabase adopt(Path workFile);                                  // open an existing work file (recovery)
    public <T> T read(Function<EntityManager, T> work);                                  // serialized by a lock, no dirty
    public <T> T write(Function<EntityManager, T> work);                                 // transaction, commit, sets dirty
    public void snapshotTo(Path target) throws IOException;                              // VACUUM INTO tmp + atomic move; does NOT clear dirty
    public boolean isDirty();  public void markClean();
    public void addDirtyListener(Consumer<Boolean> l);
    public Path workFile();
    @Override public void close();                                                       // closes factory; keeps work file
    public void closeAndDelete();                                                        // close + delete work file
}
```
Dependencies in pom: `hibernate-core`, `hibernate-community-dialects`, `sqlite-jdbc`, `jakarta.persistence-api` (via hibernate), `lombok` (provided), `slf4j-api`, and the copied modules `alphatimer-ares-serial-api`, `alphatimer-refinedmessages-api`, `alphatimer-ares-serial`, `alphatimer-refinedmessages`, `alphatimer-serial-api`, `alphatimer-serial-tcp`, `basics-jre`, `basics-notification`; test: `junit-jupiter`, `assertj-core`, `awaitility`, `slf4j-simple`.

- [ ] **Step 1: Write the failing test**

```java
class ProjectDatabaseTest {

    @TempDir Path dir;

    private static long countSettings(ProjectDatabase db) {
        return db.read(em -> em.createQuery("select count(s) from SettingsEntity s", Long.class).getSingleResult());
    }

    @Test
    void writeSetsDirtyAndSnapshotRoundTrips() throws Exception {
        Path file = dir.resolve("a.heat");
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            assertThat(db.isDirty()).isFalse();
            db.write(em -> { em.persist(new SettingsEntity("host", 1234, "KEY", 60, 1999)); return null; });
            assertThat(db.isDirty()).isTrue();
            db.snapshotTo(file);
            db.markClean();
            assertThat(db.isDirty()).isFalse();
            db.closeAndDelete();
        }
        try (ProjectDatabase db = ProjectDatabase.openCopy(file, dir)) {
            assertThat(countSettings(db)).isEqualTo(1);
            assertThat(db.isDirty()).isFalse();
            db.closeAndDelete();
        }
    }

    @Test
    void snapshotReplacesExistingFileAtomicallyAndLeavesNoTempFile() throws Exception {
        Path file = dir.resolve("a.heat");
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            db.snapshotTo(file);
            db.write(em -> { em.persist(new SettingsEntity("h", 1, "k", 60, 0)); return null; });
            db.snapshotTo(file);
            db.closeAndDelete();
        }
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("a.heat");
        }
    }

    @Test
    void openRejectsNonSqliteFileAndCreatesNoWorkFile() throws Exception {
        Path bad = dir.resolve("bad.heat");
        Files.writeString(bad, "this is not a database");
        Path work = Files.createDirectory(dir.resolve("work"));
        assertThatThrownBy(() -> ProjectDatabase.openCopy(bad, work)).isInstanceOf(IOException.class);
        try (var files = Files.list(work)) { assertThat(files).isEmpty(); }
    }

    @Test
    void concurrentWritersAreSerialized() throws Exception {
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            var pool = Executors.newFixedThreadPool(4);
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                int n = i;
                fs.add(pool.submit(() -> db.write(em -> { em.persist(new SettingsEntity("h" + n, n + 1, "k", 60, 0)); return null; })));
            }
            for (Future<?> f : fs) f.get(20, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(countSettings(db)).isEqualTo(40);
            db.closeAndDelete();
        }
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -B -pl heattiming-core -am test -Dtest=ProjectDatabaseTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure (classes missing).

- [ ] **Step 3: Implement `SettingsEntity` (final form) and `ProjectDatabase`**

`SettingsEntity`: `@Entity(name="SettingsEntity") @Table(name="settings")`, `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id`, columns `tcpHost String`, `tcpPort int`, `competitionKey String`, `autoSaveSeconds int`, `restPort int`; Lombok `@Getter @Setter @NoArgsConstructor`, plus constructor `(String tcpHost, int tcpPort, String competitionKey, int autoSaveSeconds, int restPort)`.

`ProjectDatabase` (key points; write full class):
```java
private static final Class<?>[] ENTITIES = { SettingsEntity.class, /* more added in Task 3 */ };
private final ReentrantLock lock = new ReentrantLock();
private final AtomicBoolean dirty = new AtomicBoolean();
private final List<Consumer<Boolean>> dirtyListeners = new CopyOnWriteArrayList<>();
private final Path workFile; private final SessionFactory factory;

private static SessionFactory buildFactory(Path workFile) {
    Configuration c = new Configuration();
    c.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC");
    c.setProperty("hibernate.connection.url", "jdbc:sqlite:" + workFile.toAbsolutePath());
    c.setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
    c.setProperty("hibernate.hbm2ddl.auto", "update");
    c.setProperty("hibernate.connection.pool_size", "1");
    c.setProperty("hibernate.show_sql", "false");
    for (Class<?> e : ENTITIES) c.addAnnotatedClass(e);
    return c.buildSessionFactory();
}

public <T> T write(Function<EntityManager, T> work) {
    lock.lock();
    try (EntityManager em = factory.createEntityManager()) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try { T r = work.apply(em); tx.commit(); setDirty(true); return r; }
        catch (RuntimeException ex) { if (tx.isActive()) tx.rollback(); throw ex; }
    } finally { lock.unlock(); }
}
public <T> T read(Function<EntityManager, T> work) { lock.lock(); try (EntityManager em = factory.createEntityManager()) { return work.apply(em); } finally { lock.unlock(); } }

public void snapshotTo(Path target) throws IOException {
    Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
    Files.deleteIfExists(tmp);
    lock.lock();
    try (Session s = factory.openSession()) {
        s.doWork(conn -> {
            boolean auto = conn.getAutoCommit();
            conn.setAutoCommit(true);                       // VACUUM cannot run inside a transaction
            try (Statement st = conn.createStatement()) { st.execute("VACUUM INTO '" + tmp.toAbsolutePath().toString().replace("'", "''") + "'"); }
            finally { conn.setAutoCommit(auto); }
        });
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (RuntimeException ex) { Files.deleteIfExists(tmp); throw new IOException("Could not save project file", ex); }
    finally { lock.unlock(); }
}
```
(Wrap the `SQLException`s thrown inside `doWork` — Hibernate's `Work` allows `SQLException`; convert any failure to `IOException`.) `openCopy`: check header — the first 16 bytes must equal `"SQLite format 3\0"`, else `throw new IOException("Not a Heat-Timing project file: ...")` **before** creating any work file; then `Files.copy` to `workDir/heat-<UUID>.work.sqlite` and build the factory (on failure delete the work file and rethrow as `IOException`). `createNew`/`adopt` as named. `setDirty` notifies listeners only when the value changes. `close()` = `factory.close()`; `closeAndDelete()` = close + `Files.deleteIfExists(workFile)`.

- [ ] **Step 4: Run tests — this is the risk gate**

Run: `mvn -q -B -pl heattiming-core -am test -Dtest=ProjectDatabaseTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 4 tests PASS. **If Hibernate+SQLite cannot be made to work after reasonable effort (identity, `VACUUM INTO`, dialect), stop and switch to the H2 fallback described in the spec ("First implementation task (risk)")**: same `ProjectDatabase` API, in-memory H2 working DB, `SCRIPT TO … COMPRESSION GZIP` for `snapshotTo`, `RUNSCRIPT` for `openCopy`; record the switch in the final report.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add heattiming-core with ProjectDatabase (Hibernate on SQLite)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Entities and stores for raw data, messages, lane times

**Files:**
- Create: `db/RawDataChunkEntity.java`, `db/AresMessageEntity.java`, `db/LaneTimeEntity.java`, `recording/RawDataRecorder.java`, `recording/MessageStore.java`, `legacy/LaneTimeStore.java`
- Modify: `db/ProjectDatabase.java` (`ENTITIES`)
- Test: `.../recording/RawDataRecorderTest.java`, `.../recording/MessageStoreTest.java`

**Interfaces:**
- Consumes: `ProjectDatabase.read/write`.
- Produces:
  - `RawDataChunkEntity(LocalDateTime timestamp, byte[] data)` (`@Lob`-less `byte[]` column `data`).
  - `AresMessageEntity` — same columns as alphatimer's `AresMessage` (copy field list; plain JPA, ids `Long`), plus a constructor-less setter-based mapping inside `MessageStore`.
  - `LaneTimeEntity` — same columns as alphatimer `LaneTimeEntity` minus `BaseEntity`; `LaneStatus` enum for it is the legacy `LaneStatus` (Task 8 copies it; **create `org.lisasp.heattiming.core.legacy.dto.LaneStatus` here**, copied verbatim from alphatimer-legacy).
  - `RawDataRecorder implements ByteConsumer, AutoCloseable`: `RawDataRecorder(ProjectDatabase db, DateTimeFacade time, int flushBytes /*512*/, long flushMillis /*250*/)`; `accept(byte)` buffers; flushes into one `RawDataChunkEntity` when the buffer reaches `flushBytes` or the scheduled flusher fires; `flush()`; `close()` flushes and stops the flusher. `static List<byte[]> readAll(ProjectDatabase db)` for tests/replay.
  - `MessageStore implements DataHandlingMessageRepository` with the mapping code from alphatimer `Messages` (usedLanes `+/-` string) using `AresMessageEntity`; `findBy(competition,event,heat)` via JPQL.
  - `LaneTimeStore`: `void save(LaneTimeEntity)`, `List<LaneTimeEntity> findAllByCompetition(String)`.

- [ ] **Step 1: Write failing tests**

`RawDataRecorderTest`:
```java
@Test
void flushesBufferedBytesIntoChunksInOrder(@TempDir Path dir) throws Exception {
    try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
        RawDataRecorder rec = new RawDataRecorder(db, () -> LocalDateTime.of(2026, 1, 1, 10, 0), 4, 60_000);
        for (byte b = 1; b <= 10; b++) rec.accept(b);
        rec.close();
        byte[] all = RawDataRecorder.readAll(db).stream().reduce(new byte[0], (a, c) -> Bytes.concat(a, c));
        assertThat(all).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(db.isDirty()).isTrue();
        db.closeAndDelete();
    }
}
@Test
void timerFlushesPartialBuffer(@TempDir Path dir) throws Exception {
    try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
        RawDataRecorder rec = new RawDataRecorder(db, () -> LocalDateTime.now(), 1000, 50);
        rec.accept((byte) 7);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(RawDataRecorder.readAll(db)).hasSize(1));
        rec.close();
        db.closeAndDelete();
    }
}
```
(`DateTimeFacade` from `org.lisasp.basics.jre.date` has `now()`; use a lambda only if it is a functional interface, otherwise an anonymous class as in alphatimer's `SerialInterpreterTest`. Use `java.io.ByteArrayOutputStream` instead of `Bytes.concat`.)

`MessageStoreTest`: build a `DataHandlingMessage` like `SerialInterpreterTest.EXPECTED_DATA_MESSAGE` (copy `DataHandlingMessageTestData` from `../alphatimer/alphatimer/src/test/java/org/lisasp/alphatimer/server/` into `heattiming-core/src/test/java/org/lisasp/heattiming/core/testdata/`, fix its package), `put` it, then assert `findBy("TestWK", (short)1, (byte)1)` returns an equal message, and that `findBy` for another heat/competition returns empty.

- [ ] **Step 2: Run to verify failure** — `mvn -q -B -pl heattiming-core -am test -Dtest='RawDataRecorderTest,MessageStoreTest' -Dsurefire.failIfNoSpecifiedTests=false` → compile errors.

- [ ] **Step 3: Implement** the classes per the interfaces above. Entities: `@Entity(name = "...")`, `@Table`, `@Enumerated(EnumType.STRING)` for enums, `LocalDateTime` columns as is. `RawDataRecorder`: a `ScheduledExecutorService` (single daemon thread) calling `flush()` every `flushMillis`; `accept` and `flush` synchronized on an internal lock; `flush` is a no-op on empty buffer and logs-and-keeps-buffer if the DB write throws (retry at next flush).
- [ ] **Step 4: Run tests** → PASS.
- [ ] **Step 5: Commit** — `git add -A && git commit -m "Add raw data, message and lane time stores" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`

---

### Task 4: Settings and default settings

**Files:**
- Create: `settings/Settings.java`, `settings/DefaultSettingsStore.java`
- Test: `settings/SettingsTest.java`, `settings/DefaultSettingsStoreTest.java`

**Interfaces:**
- Produces:
```java
public record Settings(String tcpHost, int tcpPort, String competitionKey, int autoSaveSeconds, int restPort) {
    public static Settings defaults();               // ("", 8585, "", 60, 1999)
    public List<String> validate();                  // empty = valid; messages are plain English (UI text is built by the UI)
    public Settings withTcp(String host, int port);
}
public final class DefaultSettingsStore {
    public DefaultSettingsStore(Path file);
    public static DefaultSettingsStore inUserHome();   // ~/.heat-timing/defaults.json
    public Settings load();                            // never throws: missing/corrupt → Settings.defaults()
    public void save(Settings s);                      // creates parent dirs; failures are logged only
}
```
Validation: host not blank; `tcpPort` 1–65535; key not blank; `autoSaveSeconds` 5–3600; `restPort` 0–65535 (0 = REST off).

- [ ] **Step 1: Failing tests** — for each rule one assertion on `validate()` (e.g. `new Settings(" ", 8585, "K", 60, 0).validate()` has one entry mentioning "host"; port 0 and 70000 invalid; `autoSaveSeconds` 4 invalid; defaults() has blank host + key so is invalid until filled); `DefaultSettingsStoreTest`: round trip into a `@TempDir`, missing file → defaults, file containing `{not json` → defaults, nested missing parent dir is created.
- [ ] **Step 2: Run** → fails to compile.
- [ ] **Step 3: Implement** with Jackson `ObjectMapper` (`com.fasterxml.jackson.core:jackson-databind`, BOM-managed; records are supported natively; configure `FAIL_ON_UNKNOWN_PROPERTIES=false`).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `git add -A && git commit -m "Add Settings and default settings store" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`

---

### Task 5: ProjectDocument and AutoSaver

**Files:**
- Create: `project/ProjectDocument.java`, `project/AutoSaver.java`
- Test: `project/ProjectDocumentTest.java`, `project/AutoSaverTest.java`

**Interfaces:**
- Consumes: `ProjectDatabase`, `Settings`, `SettingsEntity`, `DefaultSettingsStore`.
- Produces:
```java
public final class ProjectDocument implements AutoCloseable {
    public static ProjectDocument create(Path workDir, Settings initial) throws IOException;   // dirty = true (unsaved new project)
    public static ProjectDocument open(Path file, Path workDir) throws IOException;           // dirty = false, file set
    public static List<Path> findRecoverable(Path workDir);                                    // *.work.sqlite
    public static ProjectDocument recover(Path workFile) ;                                     // dirty = true, no file
    public ProjectDatabase database();
    public Optional<Path> file();
    public Settings settings();                      // reads the single SettingsEntity row
    public void updateSettings(Settings s);          // write → dirty
    public boolean isDirty();
    public void addDirtyListener(Consumer<Boolean> l);
    public void save() throws IOException;           // IllegalStateException if file() empty
    public void saveAs(Path file) throws IOException;// snapshot, sets file, clears dirty
    @Override public void close();                   // closeAndDelete of the work DB
}
public final class AutoSaver implements AutoCloseable {
    public AutoSaver(ProjectDocumentHolder / Supplier<ProjectDocument> current, Consumer<Exception> onError);
    public void setIntervalSeconds(int seconds);     // reschedules
    void runOnce();                                  // package-private: saves if current != null && dirty && file present
    @Override public void close();
}
```
`create` writes the initial `SettingsEntity` and leaves `dirty=true` (a new project has never been saved). `save()` must clear dirty only when `snapshotTo` succeeded, and must not clear it if data was written during the snapshot: take `long version = db.writeCount()` before the snapshot and call `markClean()` only if unchanged — **add `public long writeCount()` (incremented in `write`) to `ProjectDatabase`**; if it changed, leave dirty.

- [ ] **Step 1: Failing tests**
  - create → `isDirty()`, `file()` empty; `saveAs(dir/"x.heat")` → not dirty, file present; `updateSettings` → dirty; `save()` → not dirty.
  - `open(file)` of the saved project returns equal `settings()`, not dirty; close deletes the work file (work dir empty).
  - `save()` on a project without file throws `IllegalStateException`.
  - failed save (target dir made read-only/non-existent: `saveAs(dir/"missing"/"x.heat")`) throws `IOException`, document stays dirty, no temp file left.
  - write during snapshot keeps dirty: use a `ProjectDatabase` subclass hook is not possible (final) — instead test `writeCount` semantics directly: `write; save; write; isDirty` true.
  - `findRecoverable` lists a work file left behind when a `ProjectDatabase` is only `close()`d (not deleted); `recover(workFile)` yields dirty doc with the data.
  - `AutoSaverTest`: with a document that has a file and is dirty, `runOnce()` saves (file modified time changes / dirty false); with a clean document nothing happens; with no file nothing happens and no error; with failing save `onError` is called and document stays dirty.
- [ ] **Step 2: Run** → compile failure.
- [ ] **Step 3: Implement.** `AutoSaver` uses a `ScheduledExecutorService` daemon thread; `setIntervalSeconds` cancels the old `ScheduledFuture` and schedules `runOnce` at fixed delay; exceptions inside `runOnce` go to `onError`, never escape.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `git add -A && git commit -m "Add ProjectDocument and AutoSaver" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`

---

### Task 6: Heat model, store and service (with placing)

**Files:**
- Create: `heats/{HeatStatus,LaneStatus,Penalty,LaneDto,HeatDto,Lane,Heat,HeatService,RunningClock}.java`, `db/HeatEntity.java`, `db/LaneEntity.java`, `heats/HeatStore.java`
- Modify: `db/ProjectDatabase.java` (`ENTITIES` + `HeatEntity`, `LaneEntity`)
- Test: `heats/HeatTest.java`, `heats/HeatServiceTest.java`, `heats/RunningClockTest.java`

**Interfaces:**
- Consumes: refined messages: `StartMessage(lapCount)`, `UsedLanesMessage(getUsedLanes())`, `TimeMessage(lane, currentLap, lapCount, rank, timeInMillis, kindOfTime ∈ {SplitTime, Finish})`, `DidNotStartMessage(lane)`, `DidNotFinishMessage(lane)`, `OfficialEndMessage(lapCount)`; `DroppedRefinedMessage` is ignored.
- Produces:
```java
public enum HeatStatus { Open, Started, Finished }
public enum LaneStatus { NotUsed, Used }
public enum Penalty { None, DidNotStart, DidNotFinish }
public record LaneDto(int number, int timeInMillis, int lap, LaneStatus status, Penalty penalty, int place, boolean finished) {}
public record HeatDto(String competition, int event, int heat, HeatStatus status, LocalDateTime started, LocalDateTime ended, int lapCount, List<LaneDto> lanes) {}
public final class HeatStore {                   // JPA
    public HeatStore(ProjectDatabase db);
    public Optional<HeatDto> find(String competition, int event, int heat);
    public HeatDto create(String competition, int event, int heat, int laneCount);   // lanes 1..laneCount, NotUsed
    public void save(HeatDto heat);                                                  // updates heat + lanes by number
    public Optional<HeatDto> findLatest(String competition);                         // most recently updated heat (for restoring the Live view after opening a file)
}
public final class HeatService implements Consumer<RefinedMessage> {
    public HeatService(HeatStore store);        // lane count 8
    public void register(Consumer<HeatDto> listener); // gets the current heat immediately if any, then every change
}
public final class RunningClock { public static long millis(HeatDto heat, LocalDateTime now); } // Open→0; Started→now-started; Finished→ended-started; never negative
```
`HeatEntity` columns: `competition, event, heat, status(String), started, ended, lapCount, updated(LocalDateTime)`, `@OneToMany(mappedBy="heat", cascade=ALL, orphanRemoval=true, fetch=EAGER) @OrderBy("number") List<LaneEntity> lanes`, unique constraint `(competition,event,heat)`. `LaneEntity`: `number, timeInMillis, lap, status, penalty, place, finished`, `@ManyToOne HeatEntity heat`.

Domain rules in `Heat` (mutable working copy built from a `HeatDto`; `createDto()` returns the new dto):
1. `assureStarted(msg)`: Open → Started; `started` = message timestamp if null.
2. `start`: `lapCount=msg.lapCount`; every lane: time 0, lap 0, place 0, finished=false, penalty None.
3. `usedLanes`: lane `n` used iff `UsedLanes.fromValue(msg.getUsedLanes()).isUsed(n-1)`; NotUsed lanes get time/lap/place reset to 0 and penalty None.
4. `touch(TimeMessage)`: assureStarted; lapCount from msg; lane must exist (else `NoSuchElementException` — caught in `HeatService`, logged); ignore `timeInMillis <= 0`; set time, `lap=currentLap`; `finished = kindOfTime==Finish`; `rank>0` → `place=rank`.
5. `didNotStart(lane)`: penalty DidNotStart, time/lap/place 0, finished=false. `didNotFinish(lane)`: penalty DidNotFinish, place 0, finished=false (time of last split kept).
6. `finish(OfficialEndMessage)`: assureStarted, `status=Finished`, `ended=timestamp`, `lapCount`. Then placing: lanes with `status==Used && finished && penalty==None && time>0` form the ranking set. If every lane in the set already has `place>0`, keep the device places; otherwise recompute all of them: place = 1 + number of ranking-set lanes with strictly smaller time (ties share a place). Lanes outside the set get place 0.
7. Time on a `NotUsed` lane is forced to 0.

`HeatService.accept` logic = alphatimer's (ignore `event<=0 || heat<=0`; find-or-create; apply message by type; save and notify listeners **if the dto changed**; unknown message type → warn). Differs from alphatimer: notifies also while status is `Open`, and catches `RuntimeException` from `Heat` application (log, no state change, no rethrow).

- [ ] **Step 1: Failing tests** (build refined messages with their `@Value` all-args constructors; `LocalDateTime` fixed):
  - `HeatTest`: start resets; usedLanes marks 1,2,3 used; split on lane 2 (`SplitTime`, 12.34 s, lap 1) → time/lap set, not finished; finish times lane1 20.00 (rank 0), lane2 19.50, lane3 19.50 → after OfficialEnd places lane2=1, lane3=1, lane1=3; device rank wins when all present (ranks 2,1,3 given → kept); DNS lane has place 0 and is not in ranking; DNF keeps last split time; unknown lane 9 → `NoSuchElementException`; time 0 ignored; NotUsed lane forced 0.
  - `HeatServiceTest` with a `ProjectDatabase.createNew(@TempDir)`: messages for event 1 heat 1 and event 2 heat 1 stay separate; listener registered late gets the current heat; second identical message does not re-notify; event 0 ignored; DB state survives `ProjectDatabase` reopen via `snapshotTo`/`openCopy`; `findLatest` returns the most recently changed heat of the given competition and not another competition's.
  - `RunningClockTest`: Open → 0; Started at 10:00:00, now 10:00:05.5 → 5500; Finished with ended 10:00:20 → 20000 independent of `now`; `now < started` → 0.
- [ ] **Step 2: Run** → compile failure.
- [ ] **Step 3: Implement** (adapting `../alphatimer/alphatimer-heats/.../domain/{Heat,Lane}.java` and `service/HeatService.java`).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `git add -A && git commit -m "Add heat model with placing, HeatStore and HeatService" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`

---

### Task 7: Legacy service and REST endpoint

**Files:**
- Create: `legacy/dto/{Heat,Lane}.java` (copied from `alphatimer-legacy/.../dto/`, package changed to `org.lisasp.heattiming.core.legacy.dto`), `legacy/LegacySerialization.java` (copied), `legacy/LegacyService.java`, `legacy/LegacyHttpServer.java`
- Test: `legacy/LegacyServiceTest.java`, `legacy/LegacyHttpServerTest.java`

**Interfaces:**
- Consumes: `LaneTimeStore` (Task 3), `dto.LaneStatus` (Task 3).
- Produces:
```java
public final class LegacyService implements DataHandlingMessageListener {
    public LegacyService(LaneTimeStore store, String competitionKey);
    public Heat[] getHeats();                // dto.Heat
}
public final class LegacyHttpServer implements AutoCloseable {
    public LegacyHttpServer(int port, Supplier<Heat[]> heats) throws IOException;   // port 0 allowed in tests → getPort()
    public int getPort();
    @Override public void close();           // stop(0) and releases the port immediately
}
```
Endpoints: `GET /legacy/heats.xml` (`application/xml; charset=UTF-8`, `LegacySerialization.toXML`), `GET /legacy/heats.json` (`application/json`, Jackson), other paths 404, other methods 405. `LegacyService.accept` = alphatimer's mapping logic verbatim (copy the `switch`), saving through `LaneTimeStore`.

- [ ] **Step 1: Failing tests** — `LegacyServiceTest`: port the relevant assertions from `../alphatimer/alphatimer-legacy/src/test` (read them first; reuse their message fixtures). `LegacyHttpServerTest`: start on port 0 with a fixed `Heat[]`, `HttpClient` GET both endpoints → 200 + expected body, `/nope` → 404, POST → 405; after `close()` the same port can be bound by a new `LegacyHttpServer` (start/stop/start).
- [ ] **Step 2: Run** → compile failure.
- [ ] **Step 3: Implement** with `com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0)` and a small cached executor shut down in `close()`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `git add -A && git commit -m "Add legacy service and REST endpoints" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`

---

### Task 8: RecordingSession

**Files:**
- Create: `recording/RecordingState.java`, `recording/RecordingSession.java`
- Test: `recording/RecordingSessionTest.java`

**Interfaces:**
- Consumes: everything above; copied classes `InputCollector(String competitionKey, DateTimeFacade)`, `MessageConverter`, `MessageAggregator`, `DataHandlingMessageRefiner`, `TcpReader`.
- Produces:
```java
public enum RecordingState { STOPPED, RUNNING, ERROR }
public final class RecordingSession implements AutoCloseable {
    public interface ReaderFactory { SerialPortReader create(Settings s); }
    public RecordingSession(ProjectDatabase db, ReaderFactory readers, DateTimeFacade time);
    public static ReaderFactory tcp();                              // s -> new TcpReader(s.tcpHost(), s.tcpPort())
    public void start(Settings snapshot);                           // validate; ERROR + message on failure, never throws
    public void stop();                                             // idempotent
    public RecordingState state();
    public String errorMessage();                                   // "" unless ERROR
    public boolean isConnected();
    public void addStateListener(Consumer<RecordingState> l);
    public void addHeatListener(Consumer<HeatDto> l);               // forwarded to HeatService
    public void addMessageListener(Consumer<String> l);             // short text per received message, for the log/status
    public Heat[] legacyHeats();                                    // current legacy view
    @Override public void close();                                  // = stop()
}
```
`start`: (1) `snapshot.validate()` non-empty → ERROR with joined messages. (2) Build `LaneTimeStore`, `LegacyService(store, key)`, `MessageStore`, `HeatService(new HeatStore(db))`, `RawDataRecorder`, `InputCollector`, `MessageConverter`, `MessageAggregator`, `DataHandlingMessageRefiner`. Wire: `aggregator.register(legacy); aggregator.register(messageStore::put); aggregator.register(refiner); refiner.register(heatService::accept); converter.register(e -> { notify message listeners; aggregator.accept(e); }); collector.register(converter); reader.register(safe(rawRecorder)).register(safe(collector));` where `safe(ByteConsumer)` catches `RuntimeException`, logs it and reports it to the message listeners (so a pipeline bug never kills the reader thread). (3) If `restPort != 0` start `LegacyHttpServer` (failure e.g. port in use → close everything built so far, ERROR "REST port N: …"). (4) `reader.start()`; state RUNNING. `stop`: `reader.close()` → `restServer.close()` → `rawRecorder.close()` → state STOPPED; each step guarded so a failure in one does not skip the rest. Listeners are retained across sessions (they are registered on the session object, not on the pipeline).

- [ ] **Step 1: Failing tests** with a `FakeReader implements SerialPortReader` (stores registered `ByteConsumer`s; test helper `push(byte[])`; counts `start/close`):
  - valid settings + fake reader: state RUNNING, `start` called once; pushed `message1`+`message2` bytes (from `DataHandlingMessageTestData`, same framing as `SerialInterpreterTest`) → `MessageStore` has the message, raw chunks present after `stop`, heat listener notified, `legacyHeats()` non-empty.
  - `start` with blank host → ERROR, message mentions "host", reader factory never called.
  - REST port already bound by a test `ServerSocket` → ERROR mentioning the port, `reader.close` called, state listener saw ERROR.
  - start→stop→start→stop×3 on the same port 0/fixed free port: no exception, `close` count == `start` count, REST port reusable.
  - garbage bytes (`new byte[]{0x01, (byte)0xFF, 0x00, …}` ×1000) pushed: no exception escapes, state stays RUNNING, a following valid message is still processed.
  - a consumer that throws (stub listener) does not break the reader (call `push` and assert no exception).
  - changing the competition key between sessions: second session's heats/legacy data are separate (`legacyHeats()` only for new key).
- [ ] **Step 2: Run** → compile failure.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `git add -A && git commit -m "Add RecordingSession" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`

---

### Task 9: End-to-end test through a real TCP socket

**Files:**
- Test: `heattiming-core/src/test/java/org/lisasp/heattiming/core/EndToEndTest.java`

- [ ] **Step 1: Write the test.** Local `ServerSocket` replays the `DataHandlingMessageTestData` messages (and a hand-built second heat, if the test data offers more messages; otherwise repeat with different event numbers by constructing bytes via the existing helper) to a `RecordingSession.tcp()`; `ProjectDocument.create` → `updateSettings(host=localhost, port=serverPort, key="TestWK", restPort=0)`; start; await heat listener; stop; `saveAs(dir/"e2e.heat")`; `close`; `open` the file; assert raw data bytes equal what the server sent, message count, heats, settings round trip; then start again against a second server with a changed port (settings changed between sessions without restart) and assert data is appended.
- [ ] **Step 2: Run** `mvn -q -B -pl heattiming-core -am test -Dtest=EndToEndTest -Dsurefire.failIfNoSpecifiedTests=false` → fix wiring bugs until PASS (this is the integration check; fix the code, not the test, unless the test is wrong).
- [ ] **Step 3: Commit** — `git add -A && git commit -m "Add end-to-end recording test" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`

---

### Task 10: App controller and UI models (no Swing windows)

**Files:**
- Create: `heattiming-app/pom.xml`, `app/{AppController,SavePrompt,TimeFormat,HeatTableModel}.java`
- Test: `app/AppControllerTest.java`, `app/TimeFormatTest.java`, `app/HeatTableModelTest.java`

**Interfaces:**
- Produces:
```java
public interface SavePrompt {
    enum Choice { SAVE, DISCARD, CANCEL }
    Choice askSaveChanges();
    Optional<Path> askSavePath();                 // for first save / Save As; empty = cancelled
    void showError(String title, String message);
    RecoveryChoice askRecover(Path workFile);     // enum RecoveryChoice { RECOVER, DISCARD }  (nested in SavePrompt)
}
public final class AppController {
    public AppController(Path workDir, DefaultSettingsStore defaults, SavePrompt prompt, Supplier<RecordingSession.ReaderFactory> readers, DateTimeFacade time);
    public void init();                           // offers recovery of leftover work files, else newProject()
    public void newProject();                     // closeCurrent() gate, then ProjectDocument.create(workDir, defaults.load())
    public void open(Path file);                  // closeCurrent() gate; on IOException showError and keep the current document
    public boolean save();                        // file present → save; else askSavePath → saveAs; false if cancelled/failed
    public boolean saveAs();
    public boolean requestClose();                // stops recording, asks if dirty; true when the app may exit (document closed)
    public void startRecording();                 // settings of the document → session.start; stores defaults
    public void stopRecording();
    public void applySettings(Settings s);        // IllegalStateException while RUNNING; otherwise document.updateSettings + autosaver interval
    public ProjectDocument document();  public RecordingSession session();  public Optional<HeatDto> currentHeat();
    public void addChangeListener(Runnable l);    // document replaced / dirty / file name changed
}
public final class TimeFormat { public static String time(int millis); public static String running(long millis); }
public final class HeatTableModel extends AbstractTableModel { public void setHeat(HeatDto h); public HeatDto heat(); /* columns: Lane, Time, Lap, Penalty, Place; rows = used lanes only */ }
```
`TimeFormat.time(0)` = `""`; `time(65_430)` = `1:05.43` (hundredths, floor); `time(5_000)` = `5.00`; `running(5_500)` = `0:05.5`; `running(3_725_000)` = `1:02:05.0`. Column headers are English constants here; the table model exposes `String[] COLUMNS`.

`AppController` rules: `defaults.save(settings)` after every successful open/save/start (spec: last loaded settings become defaults); `requestClose()` = `stopRecording()`, then if dirty → prompt: SAVE → `save()` (abort close on false), DISCARD → proceed, CANCEL → return false; on proceed `autoSaver.close()`, `document.close()`. `newProject()`/`open()` use the same gate. After `open()` the Live view is restored from `HeatStore.findLatest(settings.competitionKey())`. `startRecording()` on an unsaved new project is allowed.

- [ ] **Step 1: Failing tests** with a recording `SavePrompt` stub (queue of answers) and a fake `ReaderFactory`:
  - `requestClose` while RUNNING: stops first (fake reader `close` called before any prompt — record call order in a shared list); dirty + DISCARD → true and work dir empty; dirty + CANCEL → false, document still open, state STOPPED; dirty + SAVE with path → file written, true; SAVE with cancelled path dialog → false; clean document → no prompt.
  - `newProject` while dirty → prompt shown, CANCEL keeps current; new project uses `defaults.load()`; `open` after a successful open → `defaults.load()` equals the opened project's settings.
  - `open(corruptFile)` → `showError` called, `document()` unchanged, no leftover work files.
  - `applySettings` while RUNNING throws; while STOPPED persists to document (dirty).
  - `init()` with a leftover work file → `askRecover`: RECOVER yields a dirty document with that data; DISCARD deletes it.
  - `TimeFormatTest` the examples above plus negative → `""`. `HeatTableModelTest`: only used lanes as rows, penalty text `DNS`/`DNF`, place `0` shown as `""`, finished time vs intermediate (a `lap` marker like `Lap 2` appears in the Lap column only for non-finished lanes).
- [ ] **Step 2: Run** → compile failure.
- [ ] **Step 3: Implement.** `app/pom.xml` depends on `heattiming-core`, `de.dennisfabri.jutils:jutils-swing:${jutils.version}` (verify the groupId against `~/.m2/repository/de/dennisfabri/jutils/jutils-swing` and its `pom`; use that coordinates), `slf4j-simple` (runtime); add shade plugin producing `heattiming-app/target/heat-timing.jar` with `Main-Class org.lisasp.heattiming.app.Main`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `git add -A && git commit -m "Add AppController and UI models" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`

---

### Task 11: Swing UI

**Files:**
- Create: `app/{Main,MainFrame,SettingsRecordingPanel,LiveHeatPanel}.java`
- Test: `app/MainFrameSmokeTest.java` (skipped with `Assumptions.assumeFalse(GraphicsEnvironment.isHeadless())`)

- [ ] **Step 1: Smoke test** (runs under `xvfb-run` in CI/dev box, skipped headless): construct `MainFrame` with a controller built on stubs, assert the `JButtonBar` has exactly two buttons ("Settings & Recording", "Live Heat"), clicking the second shows `LiveHeatPanel` (`isShowing`), Start button disabled while the settings are invalid, settings fields disabled while the session is RUNNING (use the fake reader factory so no network is needed).
- [ ] **Step 2: Implement `MainFrame`:** `BorderLayout`; `WEST`: `new JButtonBar(SwingConstants.VERTICAL)` with two `JToggleButton`s in a `ButtonGroup` (text only); `CENTER`: `CardLayout` panel with the two panels; `SOUTH`: status bar labels (recording state, connection, last message time, file name, `*` when dirty); menu bar: File (New, Open…, Save, Save As…, Exit — accelerators Ctrl+N/O/S) and Recording (Start, Stop). Title `Heat-Timing – <file or "unsaved"><*>`. `setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)` + window listener → `if (controller.requestClose()) { dispose(); System.exit(0) }`. A `javax.swing.Timer` (1 s) refreshes connection state; controller change listeners and session callbacks marshal with `SwingUtilities.invokeLater`.
  `SettingsRecordingPanel`: form (`GridBagLayout`) with TCP host, TCP port (`JSpinner` 1–65535), competition key, auto-save seconds, REST port (0 = off), **Start / Stop** buttons, state label (colored text: STOPPED/RUNNING/ERROR + error message), connection label. Fields are bound to a `Settings` draft; edits call `controller.applySettings` (debounced via focus-lost/Enter and when pressing Start); everything editable is disabled while RUNNING; Start disabled while `draft.validate()` is non-empty and shows the first message in a tooltip/label. `SwingSetupHelper`-style helpers from jutils are optional.
  `LiveHeatPanel`: header labels (`Event N · Heat M`, status text, large running-time label) and a `JTable` bound to `HeatTableModel` (renderers: right-aligned time column, bold row for lanes with `finished`, light highlight for lanes with a time but not finished); a 100 ms `Timer` updates the running time via `RunningClock.millis(heat, LocalDateTime.now())` only while the heat is `Started`; the panel is updated by `session.addHeatListener` also while hidden; empty state text "No heat received yet".
  `Main`: sets FlatLaf (from jutils transitive deps: `FlatLightLaf.setup()`), builds `AppController(workDir=~/.heat-timing/work, DefaultSettingsStore.inUserHome(), SwingSavePrompt, RecordingSession::tcp, real time)`, `init()`, shows frame on EDT. `SwingSavePrompt` (inner class of Main or separate file) uses `JOptionPane` (`Save / Discard / Cancel`) and `JFileChooser` with a `.heat` filter that appends the extension.
- [ ] **Step 3: Build and run smoke test** — `mvn -q -B -pl heattiming-app -am package` then `xvfb-run -a mvn -q -B -pl heattiming-app test -Dtest=MainFrameSmokeTest -Dsurefire.failIfNoSpecifiedTests=false`. Fix until PASS.
- [ ] **Step 4: Visual check** — `xvfb-run -a java -jar heattiming-app/target/heat-timing.jar &`, take a screenshot with `import -window root /root/.claude/jobs/9de57a31/tmp/shot.png` (ImageMagick; if unavailable use `Robot` in a tiny throwaway Java snippet outside the repo), read the image, confirm layout (button bar left, content right); kill the process.
- [ ] **Step 5: Commit** — `git add -A && git commit -m "Add Swing UI with button bar, settings/recording and live heat panels" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`

---

### Task 12: Documentation and final verification

**Files:**
- Create: `README.md`, `AGENTS.md` (+ `CLAUDE.md` containing `@AGENTS.md`, like the sibling projects)

- [ ] **Step 1: Write `README.md`** (what it is, build `mvn clean install`, run `java -jar heattiming-app/target/heat-timing.jar`, workflow stop→change→start, project file contents, REST endpoints, where defaults/work files live) and `AGENTS.md` (module table, rules: copied modules originate from `../alphatimer` and are independent; Hibernate+SQLite details; no real event data in git; build/test commands; Java 25).
- [ ] **Step 2: Full verification** — `mvn -B clean install` (all modules, all tests) and `xvfb-run -a mvn -B -pl heattiming-app test`; read the output, confirm 0 failures. Run the jar once under xvfb for 5 s and confirm it starts without exceptions in the log.
- [ ] **Step 3: Spec coverage check** — walk through the spec sections and confirm each is implemented (settings in UI incl. REST port; stop→change→start; one project file with settings/raw/messages/heats; dirty + Save + periodic save; close flow; defaults; Start/Stop + host/port in the UI; live heat with single time, placing, running time; REST; crash recovery). Fix gaps.
- [ ] **Step 4: Commit** — `git add -A && git commit -m "Add documentation" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`
