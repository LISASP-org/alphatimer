package org.lisasp.alphatimer.serial.com;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserGroupsTest {

    @Test
    void userInTheGroupIsAMember() {
        UserGroups groups = new UserGroups(command -> Optional.of("dennis adm dialout plugdev\n"));

        assertThat(groups.membership("dennis", "dialout")).isEqualTo(UserGroups.Membership.MEMBER);
    }

    @Test
    void userNotInTheGroupIsNoMember() {
        UserGroups groups = new UserGroups(command -> Optional.of("dennis adm dialoutx plugdev\n"));

        assertThat(groups.membership("dennis", "dialout")).isEqualTo(UserGroups.Membership.NOT_MEMBER);
    }

    @Test
    void membershipIsUnknownWhenTheGroupsCannotBeQueried() {
        UserGroups groups = new UserGroups(command -> Optional.empty());

        assertThat(groups.membership("dennis", "dialout")).isEqualTo(UserGroups.Membership.UNKNOWN);
    }

    @Test
    void theConfiguredGroupsOfTheUserAreQueriedNotThoseOfThisProcess() {
        List<List<String>> commands = new ArrayList<>();
        new UserGroups(command -> {
            commands.add(command);
            return Optional.empty();
        }).membership("dennis", "dialout");

        assertThat(commands).containsExactly(List.of("id", "-nG", "dennis"));
    }

    @Test
    void theCommandAddsTheUserToTheGroup() {
        assertThat(UserGroups.addCommand("dennis", "dialout")).isEqualTo("sudo usermod -aG dialout dennis");
    }

    @Test
    void queryingThisMachineNeverFails() {
        assertThat(UserGroups.ofSystem().membership(System.getProperty("user.name"), "no-such-group-42"))
                .isIn(UserGroups.Membership.NOT_MEMBER, UserGroups.Membership.UNKNOWN);
    }
}
