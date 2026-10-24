package com.wallet.core.waitlist.service;

import com.wallet.core.waitlist.config.WaitlistProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The command that sends the opening announcement, run on the server in place of the API. It only
 * exists when {@code wallet.waitlist.announce.id} and {@code .file} are both set, so the running
 * core never has it. When it is done the process exits (code 1 if any e-mail failed, so a script
 * notices; running it again sends only what is missing):
 *
 * <pre>
 * java -jar core.jar --spring.main.web-application-type=none \
 *     --wallet.waitlist.announce.id=abertura --wallet.waitlist.announce.file=/path/message.txt
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wallet.waitlist.announce", name = {"id", "file"})
public class WaitlistAnnouncementRunner implements ApplicationRunner {

    private final WaitlistAnnouncer announcer;
    private final WaitlistProperties properties;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        WaitlistProperties.Announce announce = properties.announce();
        AnnouncementMessage message = read(announce.file());
        log.info("[WAITLIST] Announcing {} to everyone on the list who has not had it", announce.id());

        WaitlistAnnouncer.Totals totals = announcer.announce(announce.id(), message);

        log.info("[WAITLIST] Announcement {} finished: {} sent, {} failed", announce.id(), totals.sent(), totals.failed());
        System.exit(SpringApplication.exit(context, () -> totals.failed() > 0 ? 1 : 0));
    }

    /** A file that cannot be read stops the command before any e-mail goes out. */
    private static AnnouncementMessage read(String file) {
        try {
            return AnnouncementFiles.parse(Files.readString(Path.of(file), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read wallet.waitlist.announce.file (" + file + "): " + ex.getMessage(), ex);
        }
    }
}
