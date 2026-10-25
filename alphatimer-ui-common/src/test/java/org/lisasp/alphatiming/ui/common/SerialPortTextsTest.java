package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.serial.com.InaccessiblePort;
import org.lisasp.alphatimer.serial.com.UserGroups.Membership;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SerialPortTextsTest {

    private static final InaccessiblePort USB0 = new InaccessiblePort("/dev/ttyUSB0", "dialout");
    private static final InaccessiblePort USB1 = new InaccessiblePort("/dev/ttyUSB1", "dialout");
    private static final String RELOGIN = "The group only takes effect after you log out and log in again (or restart the computer).\n"
            + "Restarting AlphaTiming is not enough.";

    private Locale original;

    @BeforeEach
    void useEnglish() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    void noHintWhenEveryPortIsAccessible() {
        assertThat(SerialPortTexts.accessHint(List.of())).isEmpty();
    }

    @Test
    void hintOnlyNamesThePorts() {
        assertThat(SerialPortTexts.accessHint(List.of(USB0, USB1))).isEqualTo("No access to /dev/ttyUSB0, /dev/ttyUSB1");
    }

    @Test
    void infoForAMissingGroupNamesTheCommandAndTheNewLogin() {
        String info = SerialPortTexts.accessInfo(List.of(USB0, USB1), "dennis", group -> Membership.NOT_MEMBER);

        assertThat(info).isEqualTo("Your user \"dennis\" is not a member of the group \"dialout\". Add it with:\n"
                                           + "    sudo usermod -aG dialout dennis\n"
                                           + RELOGIN);
    }

    @Test
    void infoForAGroupAddedAfterTheLoginOnlyAsksForTheNewLogin() {
        String info = SerialPortTexts.accessInfo(List.of(USB0), "dennis", group -> Membership.MEMBER);

        assertThat(info).isEqualTo("Your user \"dennis\" is already a member of the group \"dialout\", but not in this login.\n"
                                           + RELOGIN);
    }

    @Test
    void infoForAnUnknownMembershipDoesNotClaimTheGroupIsMissing() {
        String info = SerialPortTexts.accessInfo(List.of(USB0), "dennis", group -> Membership.UNKNOWN);

        assertThat(info).isEqualTo("If your user \"dennis\" is not a member of the group \"dialout\" yet, add it with:\n"
                                           + "    sudo usermod -aG dialout dennis\n"
                                           + RELOGIN);
    }

    @Test
    void infoHasOneEntryPerGroup() {
        Map<String, Membership> memberships = Map.of("dialout", Membership.NOT_MEMBER, "uucp", Membership.MEMBER);
        String info = SerialPortTexts.accessInfo(List.of(USB0, new InaccessiblePort("/dev/ttyACM0", "uucp")), "dennis", memberships::get);

        assertThat(info).isEqualTo("Your user \"dennis\" is not a member of the group \"dialout\". Add it with:\n"
                                           + "    sudo usermod -aG dialout dennis\n"
                                           + "Your user \"dennis\" is already a member of the group \"uucp\", but not in this login.\n"
                                           + RELOGIN);
    }

    @Test
    void infoForPortsWithoutGroupAccessNeedsAnAdministrator() {
        String info = SerialPortTexts.accessInfo(List.of(new InaccessiblePort("/dev/ttyUSB0", "")), "dennis", group -> Membership.UNKNOWN);

        assertThat(info).isEqualTo("No group may open /dev/ttyUSB0: an administrator has to allow the access.");
    }

    @Test
    void infoIsLocalized() {
        Locale.setDefault(Locale.GERMANY);

        String info = SerialPortTexts.accessInfo(List.of(USB0), "dennis", group -> Membership.NOT_MEMBER);

        assertThat(info).isEqualTo("Der Benutzer \"dennis\" ist nicht in der Gruppe \"dialout\". Hinzufügen mit:\n"
                                           + "    sudo usermod -aG dialout dennis\n"
                                           + "Die Gruppe gilt erst nach dem Ab- und erneuten Anmelden (oder einem Neustart des Computers).\n"
                                           + "Ein Neustart von AlphaTiming reicht nicht aus.");
    }
}
