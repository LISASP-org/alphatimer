package org.lisasp.alphatimer.serial.com;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** The groups of a Linux user account, as configured in the user database. */
@Slf4j
public final class UserGroups {

    public enum Membership { MEMBER, NOT_MEMBER, UNKNOWN }

    private static final long TIMEOUT_SECONDS = 2;

    private final Function<List<String>, Optional<String>> command;

    UserGroups(Function<List<String>, Optional<String>> command) {
        this.command = command;
    }

    public static UserGroups ofSystem() {
        return new UserGroups(UserGroups::run);
    }

    /**
     * Asks for the user by name: without a name {@code id} reports the groups of this process, which lack a group that
     * was added after the login.
     */
    public Membership membership(String user, String group) {
        return command.apply(List.of("id", "-nG", user))
                      .map(groups -> Arrays.asList(groups.trim().split("\\s+")).contains(group) ? Membership.MEMBER : Membership.NOT_MEMBER)
                      .orElse(Membership.UNKNOWN);
    }

    public static String addCommand(String user, String group) {
        return "sudo usermod -aG " + group + " " + user;
    }

    private static Optional<String> run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.exitValue() == 0 ? Optional.of(output) : Optional.empty();
        } catch (IOException ex) {
            log.warn("Could not query the groups with {}", command, ex);
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
