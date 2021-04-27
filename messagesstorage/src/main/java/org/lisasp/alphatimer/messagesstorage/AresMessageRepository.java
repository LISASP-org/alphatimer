package org.lisasp.alphatimer.messagesstorage;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface AresMessageRepository extends CrudRepository<AresMessage, String> {
    List<AresMessage> findAllByCompetitionKeyAndEventAndHeat(String competitionKey, short event, byte heat);
}