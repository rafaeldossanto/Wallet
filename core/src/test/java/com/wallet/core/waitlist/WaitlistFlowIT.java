package com.wallet.core.waitlist;

import com.wallet.core.MutableClock;
import com.wallet.core.RecordingMailTransport;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestMailConfiguration;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.mail.OutgoingEmail;
import com.wallet.core.waitlist.service.AnnouncementMessage;
import com.wallet.core.waitlist.service.WaitlistAnnouncer;
import com.wallet.core.waitlist.service.WaitlistHousekeeping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.wallet.core.RecordingMailTransport.tokenIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The waitlist against a real Postgres: sign-up with a confirmation link, the welcome e-mail,
 * the one-click unsubscribe, the clean-up of what nobody confirmed and the opening announcement.
 *
 * <p>E-mails go to a {@link RecordingMailTransport}, where the tests read the links as their owner
 * would; the clock is moved by hand.
 */
@SpringBootTest(properties = "wallet.waitlist.announce.rate-per-second=1000")
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class, TestMailConfiguration.class})
class WaitlistFlowIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MutableClock clock;

    @Autowired
    private RecordingMailTransport mail;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WaitlistHousekeeping housekeeping;

    @Autowired
    private WaitlistAnnouncer announcer;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = TestMockMvc.of(context);
        clock.set(TestClockConfiguration.START);
        jdbc.update("delete from waitlist_entries");
    }

    @Test
    void aNewSignUpGetsTheConfirmationLink() throws Exception {
        String email = uniqueEmail();

        subscribe(email).andExpect(status().isAccepted()).andExpect(content().string(""));

        OutgoingEmail confirmation = mail.await(email);
        assertThat(confirmation.subject()).isEqualTo("Confirme seu e-mail na lista do Wallet");
        assertThat(confirmation.text()).contains("http://localhost:8080/waitlist/confirm#token=").contains("24 horas");
        assertThat(confirmation.hasUnsubscribeUrl()).isFalse();
        assertThat(confirmation.kind()).isEqualTo("waitlist-confirmation");
        assertThat(jdbc.queryForObject("select status from waitlist_entries where email = ?", String.class, email))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select consent_version from waitlist_entries where email = ?", String.class, email))
                .isEqualTo("waitlist-v1");
    }

    /** The address is stored in lower case, so the same person is never on the list twice. */
    @Test
    void theAddressIsNormalized() throws Exception {
        String email = uniqueEmail();

        subscribe(email.toUpperCase()).andExpect(status().isAccepted());
        subscribe(email).andExpect(status().isAccepted());

        mail.await(email);
        assertThat(jdbc.queryForObject("select count(*) from waitlist_entries where email = ?", Integer.class, email))
                .isEqualTo(1);
        mail.assertNothingMoreTo(email, 1);
    }

    @Test
    void anInvalidAddressIsRefused() throws Exception {
        subscribe("not-an-email").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation.failed"));
        post("/internal/waitlist/confirm", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void aRepeatedSignUpWithinTheHourDoesNotResendAndAfterItDoes() throws Exception {
        String email = uniqueEmail();
        subscribe(email).andExpect(status().isAccepted());
        String first = tokenIn(mail.await(email));

        clock.advance(Duration.ofMinutes(59));
        subscribe(email).andExpect(status().isAccepted());
        mail.assertNothingMoreTo(email, 1);

        clock.advance(Duration.ofMinutes(2));
        subscribe(email).andExpect(status().isAccepted());
        String second = tokenIn(mail.awaitNth(email, 2));

        assertThat(second).isNotEqualTo(first);
        // Only the newest link works.
        confirm(first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("waitlist.invalid_token"));
        confirm(second).andExpect(status().isNoContent());
    }

    @Test
    void severalSignUpsAtOnceSendOneLink() throws Exception {
        String email = uniqueEmail();

        List<CompletableFuture<Void>> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            requests.add(CompletableFuture.runAsync(() -> {
                try {
                    subscribe(email).andExpect(status().isAccepted());
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }));
        }
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();

        mail.await(email);
        mail.assertNothingMoreTo(email, 1);
        assertThat(jdbc.queryForObject("select count(*) from waitlist_entries where email = ?", Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    void theLinkConfirmsAndTheWelcomeCarriesTheUnsubscribeLink() throws Exception {
        String email = uniqueEmail();
        subscribe(email).andExpect(status().isAccepted());

        confirm(tokenIn(mail.await(email))).andExpect(status().isNoContent());

        OutgoingEmail welcome = mail.awaitNth(email, 2);
        assertThat(welcome.subject()).isEqualTo("Você está na lista do Wallet");
        assertThat(welcome.kind()).isEqualTo("waitlist-welcome");
        assertThat(welcome.unsubscribeUrl()).startsWith("http://localhost:8080/waitlist/unsubscribe?token=");
        assertThat(jdbc.queryForObject("select status from waitlist_entries where email = ?", String.class, email))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select confirmed_at from waitlist_entries where email = ?", Timestamp.class, email)
                .toInstant()).isEqualTo(TestClockConfiguration.START);
    }

    @Test
    void confirmingTwiceIsAnswered204AndSendsNothingNew() throws Exception {
        String email = uniqueEmail();
        subscribe(email).andExpect(status().isAccepted());
        String token = tokenIn(mail.await(email));
        confirm(token).andExpect(status().isNoContent());
        mail.awaitNth(email, 2);

        confirm(token).andExpect(status().isNoContent());

        mail.assertNothingMoreTo(email, 2);
    }

    @Test
    void anExpiredLinkIsRefused() throws Exception {
        String email = uniqueEmail();
        subscribe(email).andExpect(status().isAccepted());
        String token = tokenIn(mail.await(email));

        clock.advance(Duration.ofHours(25));

        confirm(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("waitlist.invalid_token"));
        assertThat(jdbc.queryForObject("select status from waitlist_entries where email = ?", String.class, email))
                .isEqualTo("PENDING");
    }

    @Test
    void anUnknownLinkIsRefused() throws Exception {
        confirm("never-issued").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("waitlist.invalid_token"));
    }

    @Test
    void someoneAlreadyOnTheListGetsNothingMore() throws Exception {
        String email = uniqueEmail();
        subscribe(email).andExpect(status().isAccepted());
        confirm(tokenIn(mail.await(email))).andExpect(status().isNoContent());
        mail.awaitNth(email, 2);

        clock.advance(Duration.ofHours(2));
        subscribe(email).andExpect(status().isAccepted());

        mail.assertNothingMoreTo(email, 2);
        assertThat(jdbc.queryForObject("select status from waitlist_entries where email = ?", String.class, email))
                .isEqualTo("CONFIRMED");
    }

    @Test
    void unsubscribingDeletesTheRowAndAnUnknownTokenIsAnswered204Too() throws Exception {
        String email = uniqueEmail();
        subscribe(email).andExpect(status().isAccepted());
        confirm(tokenIn(mail.await(email))).andExpect(status().isNoContent());
        String token = unsubscribeTokenIn(mail.awaitNth(email, 2));

        unsubscribe(token).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from waitlist_entries where email = ?", Integer.class, email))
                .isZero();
        unsubscribe(token).andExpect(status().isNoContent());
        unsubscribe("never-issued").andExpect(status().isNoContent());
    }

    /** Whoever left can come back: the address is free again. */
    @Test
    void anAddressThatLeftCanSignUpAgain() throws Exception {
        String email = uniqueEmail();
        subscribe(email).andExpect(status().isAccepted());
        confirm(tokenIn(mail.await(email))).andExpect(status().isNoContent());
        unsubscribe(unsubscribeTokenIn(mail.awaitNth(email, 2))).andExpect(status().isNoContent());

        subscribe(email).andExpect(status().isAccepted());

        mail.awaitNth(email, 3);
    }

    /** Run the way the scheduler runs it: the task {@code configureTasks} registers, not the Spring proxy. */
    @Test
    void theScheduledCleanUpDeletesOnlyWhatNobodyConfirmedInAWeek() throws Exception {
        Instant now = TestClockConfiguration.START;
        UUID stale = insert("PENDING", now.minus(Duration.ofDays(8)), null);
        UUID recent = insert("PENDING", now.minus(Duration.ofDays(6)), null);
        UUID oldButConfirmed = insert("CONFIRMED", now.minus(Duration.ofDays(30)), null);

        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        housekeeping.configureTasks(registrar);
        assertThat(registrar.getCronTaskList()).hasSize(1);
        registrar.getCronTaskList().getFirst().getRunnable().run();

        assertThat(exists(stale)).isFalse();
        assertThat(exists(recent)).isTrue();
        assertThat(exists(oldButConfirmed)).isTrue();
    }

    @Test
    void theAnnouncementGoesOnlyToConfirmedPeopleWhoHaveNotHadIt() throws Exception {
        Instant now = TestClockConfiguration.START;
        String fresh = uniqueEmail();
        String earlier = uniqueEmail();
        String alreadyTold = uniqueEmail();
        String pending = uniqueEmail();
        insert(fresh, "CONFIRMED", now.minus(Duration.ofDays(3)), null);
        insert(earlier, "CONFIRMED", now.minus(Duration.ofDays(3)), "beta");
        insert(alreadyTold, "CONFIRMED", now.minus(Duration.ofDays(3)), "abertura");
        insert(pending, "PENDING", now.minus(Duration.ofDays(1)), null);

        WaitlistAnnouncer.Totals totals = announcer.announce("abertura", new AnnouncementMessage("O Wallet abriu", "Já dá para entrar."));

        assertThat(totals).isEqualTo(new WaitlistAnnouncer.Totals(2, 0));
        OutgoingEmail toFresh = mail.await(fresh);
        assertThat(toFresh.subject()).isEqualTo("O Wallet abriu");
        assertThat(toFresh.text()).isEqualTo("Já dá para entrar.");
        assertThat(toFresh.kind()).isEqualTo("waitlist-announcement");
        assertThat(toFresh.unsubscribeUrl()).startsWith("http://localhost:8080/waitlist/unsubscribe?token=");
        assertThat(unsubscribeTokenIn(mail.await(earlier))).isNotEqualTo(unsubscribeTokenIn(toFresh));
        mail.assertNothingMoreTo(alreadyTold, 0);
        mail.assertNothingMoreTo(pending, 0);
        assertThat(announcedAs(fresh)).isEqualTo("abertura");
        assertThat(announcedAs(earlier)).isEqualTo("abertura");
        assertThat(jdbc.queryForObject("select last_announced_at from waitlist_entries where email = ?", Timestamp.class, fresh)
                .toInstant()).isEqualTo(now);
    }

    @Test
    void runningTheAnnouncementAgainResendsNothingAndAFailureIsNotMarked() throws Exception {
        Instant now = TestClockConfiguration.START;
        String told = uniqueEmail();
        String refused = uniqueEmail();
        insert(told, "CONFIRMED", now.minus(Duration.ofDays(3)), null);
        insert(refused, "CONFIRMED", now.minus(Duration.ofDays(3)), null);
        mail.refuse(refused);
        AnnouncementMessage message = new AnnouncementMessage("O Wallet abriu", "Já dá para entrar.");

        WaitlistAnnouncer.Totals first = announcer.announce("abertura", message);

        assertThat(first).isEqualTo(new WaitlistAnnouncer.Totals(1, 1));
        assertThat(announcedAs(told)).isEqualTo("abertura");
        assertThat(announcedAs(refused)).isNull();

        mail.accept(refused);
        WaitlistAnnouncer.Totals second = announcer.announce("abertura", message);

        assertThat(second).isEqualTo(new WaitlistAnnouncer.Totals(1, 0));
        mail.await(refused);
        mail.assertNothingMoreTo(told, 1);
        assertThat(announcedAs(refused)).isEqualTo("abertura");
        assertThat(announcer.announce("abertura", message)).isEqualTo(new WaitlistAnnouncer.Totals(0, 0));
    }

    private ResultActions subscribe(String email) throws Exception {
        return post("/internal/waitlist", "{\"email\":\"%s\"}".formatted(email));
    }

    private ResultActions confirm(String token) throws Exception {
        return post("/internal/waitlist/confirm", "{\"token\":\"%s\"}".formatted(token));
    }

    private ResultActions unsubscribe(String token) throws Exception {
        return post("/internal/waitlist/unsubscribe", "{\"token\":\"%s\"}".formatted(token));
    }

    private ResultActions post(String path, String body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String uniqueEmail() {
        return "waitlist+" + UUID.randomUUID() + "@example.com";
    }

    private static String unsubscribeTokenIn(OutgoingEmail email) {
        return UriComponentsBuilder.fromUriString(email.unsubscribeUrl()).build().getQueryParams().getFirst("token");
    }

    private UUID insert(String status, Instant createdAt, String announcement) {
        return insert(uniqueEmail(), status, createdAt, announcement);
    }

    private UUID insert(String email, String status, Instant createdAt, String announcement) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into waitlist_entries (id, email, status, consent_version, created_at, confirmed_at,
                                              unsubscribe_token, last_announcement)
                values (?, ?, ?, 'waitlist-v1', ?, ?, ?, ?)
                """, id, email, status, Timestamp.from(createdAt),
                "CONFIRMED".equals(status) ? Timestamp.from(createdAt) : null,
                "CONFIRMED".equals(status) ? id.toString().replace("-", "") : null, announcement);
        return id;
    }

    private boolean exists(UUID id) {
        return jdbc.queryForObject("select count(*) from waitlist_entries where id = ?", Integer.class, id) > 0;
    }

    private String announcedAs(String email) {
        return jdbc.queryForObject("select last_announcement from waitlist_entries where email = ?", String.class, email);
    }
}
