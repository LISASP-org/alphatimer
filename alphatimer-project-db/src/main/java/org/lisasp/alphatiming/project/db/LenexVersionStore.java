package org.lisasp.alphatiming.project.db;

import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The versions of the LENEX file a recording read. Every version with changed results is kept, and the newest version
 * always: a new version replaces the last one if that one did not change the results, since the new one holds all of it.
 */
@RequiredArgsConstructor
public class LenexVersionStore {

    private final TimingDatabase db;

    public void record(LocalDateTime arrival, byte[] xml, boolean resultsChanged) {
        byte[] compressed = compress(xml);
        db.writeRecorded(em -> {
            List<LenexVersionEntity> last = em.createQuery("select v from LenexVersionEntity v order by v.id desc", LenexVersionEntity.class)
                                             .setMaxResults(1)
                                             .getResultList();
            if (!last.isEmpty() && !last.getFirst().isResultsChanged()) {
                em.remove(last.getFirst());
            }
            em.persist(new LenexVersionEntity(arrival, resultsChanged, compressed));
            return null;
        });
    }

    public List<LenexVersion> findAll() {
        return db.read(em -> em.createQuery("select v from LenexVersionEntity v order by v.id", LenexVersionEntity.class)
                               .getResultList()
                               .stream()
                               .map(LenexVersionStore::toVersion)
                               .toList());
    }

    public Optional<LenexVersion> findLast() {
        return db.read(em -> em.createQuery("select v from LenexVersionEntity v order by v.id desc", LenexVersionEntity.class)
                               .setMaxResults(1)
                               .getResultStream()
                               .findFirst()
                               .map(LenexVersionStore::toVersion));
    }

    private static LenexVersion toVersion(LenexVersionEntity entity) {
        return new LenexVersion(entity.getTimestamp(), decompress(entity.getData()));
    }

    private static byte[] compress(byte[] data) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static byte[] decompress(byte[] data) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
