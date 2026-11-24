package org.lisasp.alphatiming.lenex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class CoursesTest {

    @ParameterizedTest
    @CsvSource({
            "LCM, 50, 100, 2", "SCM, 25, 100, 4", "SCY, 25, 100, 4", "SCM20, 25, 100, 5", "SCM16, 50, 100, 6",
            "SCM33, 100, 100, 3", "SCY20, 20, 100, 5", "SCY27, 25, 100, 4", "SCY33, 100, 100, 3", "SCY36, 36, 110, 3"
    })
    void countsTheLapsOfADistance(String course, int split, int distance, int laps) {
        assertThat(Courses.laps(course, distance)).isEqualTo(OptionalInt.of(laps));
        assertThat(Courses.laps(course, split)).isPresent();
    }

    @Test
    void openWaterAndUnknownCoursesHaveNoLaps() {
        assertThat(Courses.laps("OPEN", 1500)).isEmpty();
        assertThat(Courses.laps("", 100)).isEmpty();
        assertThat(Courses.laps("XYZ", 100)).isEmpty();
    }
}
