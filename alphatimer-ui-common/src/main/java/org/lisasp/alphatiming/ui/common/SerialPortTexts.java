package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatimer.serial.com.InaccessiblePort;
import org.lisasp.alphatimer.serial.com.SerialPorts;
import org.lisasp.alphatimer.serial.com.UserGroups;
import org.lisasp.alphatimer.serial.com.UserGroups.Membership;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SerialPortTexts {

    private static final String COMMAND_INDENT = "    ";

    /** The hint below the port list and the info behind its icon; both empty if every port is accessible. */
    public record Access(String hint, String info) {
    }

    private SerialPortTexts() {
    }

    public static Access ofThisMachine() {
        List<InaccessiblePort> ports = SerialPorts.withoutAccess();
        if (ports.isEmpty()) {
            return new Access("", "");
        }
        String user = System.getProperty("user.name");
        UserGroups groups = UserGroups.ofSystem();
        return new Access(accessHint(ports), accessInfo(ports, user, group -> groups.membership(user, group)));
    }

    /** Names the ports missing from the port list; empty if none is missing. */
    public static String accessHint(List<InaccessiblePort> ports) {
        if (ports.isEmpty()) {
            return "";
        }
        return I18n.get("input.serialPort.noAccess", names(ports));
    }

    /** How the user gets access to the ports: per group whether and how to join it, then that a new login is needed. */
    public static String accessInfo(List<InaccessiblePort> ports, String user, Function<String, Membership> membership) {
        Map<String, List<InaccessiblePort>> portsByGroup = ports.stream()
                .collect(Collectors.groupingBy(InaccessiblePort::group, LinkedHashMap::new, Collectors.toList()));
        List<String> lines = new ArrayList<>();
        boolean groupToJoin = false;
        for (Map.Entry<String, List<InaccessiblePort>> entry : portsByGroup.entrySet()) {
            String group = entry.getKey();
            if (group.isEmpty()) {
                lines.add(I18n.get("input.serialPort.noGroup", names(entry.getValue())));
                continue;
            }
            groupToJoin = true;
            switch (membership.apply(group)) {
                case MEMBER -> lines.add(I18n.get("input.serialPort.member", user, group));
                case NOT_MEMBER -> lines.addAll(List.of(I18n.get("input.serialPort.notMember", user, group), command(user, group)));
                case UNKNOWN -> lines.addAll(List.of(I18n.get("input.serialPort.maybeNotMember", user, group), command(user, group)));
            }
        }
        if (groupToJoin) {
            lines.add(I18n.get("input.serialPort.relogin"));
        }
        return String.join("\n", lines);
    }

    private static String command(String user, String group) {
        return COMMAND_INDENT + UserGroups.addCommand(user, group);
    }

    private static String names(List<InaccessiblePort> ports) {
        return ports.stream().map(InaccessiblePort::name).collect(Collectors.joining(", "));
    }
}
