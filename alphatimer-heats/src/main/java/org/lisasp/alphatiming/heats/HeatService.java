package org.lisasp.alphatiming.heats;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DidNotFinishMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DidNotStartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.OfficialEndMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.StartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedRefinedMessage;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Slf4j
public final class HeatService implements Consumer<RefinedMessage> {

    private static final int LANE_COUNT = 10;

    private final HeatRepository store;
    private final Consumer<RuntimeException> onError;

    private final List<Consumer<LiveHeats>> listeners = new CopyOnWriteArrayList<>();
    private final SortedSet<Integer> usedLanes = new TreeSet<>();
    private final Map<HeatKey, HeatDto> known = new LinkedHashMap<>();
    private volatile LiveHeats live = LiveHeats.NONE;

    public HeatService(HeatRepository store) {
        this(store, ex -> { });
    }

    public HeatService(HeatRepository store, Consumer<RuntimeException> onError) {
        this.store = store;
        this.onError = onError;
        usedLanes.addAll(store.findUsedLanes());
        store.findAll().forEach(h -> known.put(HeatKey.of(h), h));
        List<HeatDto> recent = store.findRecent(2);
        if (!recent.isEmpty()) {
            live = new LiveHeats(recent.get(0), recent.size() > 1 ? recent.get(1) : null, usedLanes, List.copyOf(known.values()));
        }
    }

    private static boolean isRepeatOfAPreviousRace(RefinedMessage message) {
        return (message instanceof StartMessage m && m.getMessageType() == RefinedMessageType.PreviousRaceResult)
                || (message instanceof TimeMessage t && t.getMessageType() == RefinedMessageType.PreviousRaceResult);
    }

    private record HeatKey(int event, int heat) {

        static HeatKey of(HeatDto heat) {
            return new HeatKey(heat.event(), heat.heat());
        }
    }

    private static boolean sameHeat(HeatDto a, HeatDto b) {
        return a.event() == b.event() && a.heat() == b.heat();
    }

    public synchronized void register(Consumer<LiveHeats> listener) {
        LiveHeats snapshot = live;
        if (snapshot.current() != null) {
            listener.accept(snapshot);
        }
        listeners.add(listener);
    }

    @Override
    public synchronized void accept(RefinedMessage message) {
        if (message instanceof DroppedRefinedMessage) {
            return;
        }
        if (message.getEvent() <= 0 || message.getHeat() <= 0) {
            return;
        }
        if (isRepeatOfAPreviousRace(message)) {
            return;
        }
        try {
            Optional<HeatDto> stored = store.find(message.getEvent(), message.getHeat());
            HeatDto before = stored.orElseGet(() -> HeatDto.empty(message.getEvent(), message.getHeat(), LANE_COUNT));
            Heat heat = new Heat(before);
            if (message instanceof StartMessage m) {
                heat.start(m);
            } else if (message instanceof UsedLanesMessage m) {
                heat.usedLanes(m);
            } else if (message instanceof TimeMessage m) {
                heat.touch(m);
            } else if (message instanceof DidNotStartMessage m) {
                heat.didNotStart(m);
            } else if (message instanceof DidNotFinishMessage m) {
                heat.didNotFinish(m);
            } else if (message instanceof OfficialEndMessage m) {
                heat.finish(m);
            } else {
                log.debug("Did not apply message '{}'.", message);
                return;
            }
            HeatDto after = heat.createDto();
            if (after.equals(before)) {
                return;
            }
            if (stored.isPresent()) {
                store.save(after);
            } else {
                store.create(after);
            }
            LiveHeats shown = live;
            HeatDto previous = shown.current() != null && !sameHeat(shown.current(), after) ? shown.current() : shown.previous();
            after.lanes().stream().filter(l -> l.status() == LaneStatus.Used).forEach(l -> usedLanes.add(l.number()));
            known.put(HeatKey.of(after), after);
            estimateEndsOfOtherHeats(after, message.getTimestamp());
            live = new LiveHeats(after, previous == null ? null : known.get(HeatKey.of(previous)), usedLanes, List.copyOf(known.values()));
        } catch (RuntimeException ex) {
            log.warn("Could not apply message '{}'.", message, ex);
            onError.accept(ex);
            return;
        }
        announce(live);
    }

    /**
     * The device does not always send the official end; a heat that another heat follows is over by then at the latest,
     * and when it fell silent long before (a stopped recording, an older project), by the time its clock stopped.
     */
    private void estimateEndsOfOtherHeats(HeatDto active, LocalDateTime at) {
        List<HeatDto> unended = known.values()
                                     .stream()
                                     .filter(h -> !sameHeat(h, active) && h.status() == HeatStatus.Started && h.ended() == null)
                                     .toList();
        for (HeatDto before : unended) {
            Heat heat = new Heat(before);
            LocalDateTime last = before.lastMessage() != null ? before.lastMessage() : before.started();
            LocalDateTime stale = last == null ? at : last.plus(RunningClock.STALE_AFTER);
            heat.estimateEnd(at.isBefore(stale) ? at : stale);
            HeatDto after = heat.createDto();
            store.save(after);
            known.put(HeatKey.of(after), after);
        }
    }

    private void announce(LiveHeats heats) {
        for (Consumer<LiveHeats> listener : listeners) {
            try {
                listener.accept(heats);
            } catch (RuntimeException ex) {
                log.warn("Heat listener failed", ex);
            }
        }
    }
}
