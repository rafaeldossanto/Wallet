package com.wallet.core.identity.service;

import com.wallet.core.mail.OutgoingEmail;
import com.wallet.core.shared.time.WalletTime;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * The texts of the account e-mails, in Portuguese: they are read by people, like the app's screens.
 * Plain and short, with no name or anything else a stranger typed in a form: whoever signs up with
 * someone else's e-mail cannot put words in a message from the Wallet.
 */
@UtilityClass
public class AccountEmails {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(WalletTime.ZONE);
    private static final String SIGNATURE = "\n\nWallet\n";

    public OutgoingEmail confirmAccount(String to, String link) {
        return OutgoingEmail.of(to, "Confirme seu e-mail no Wallet", """
                Olá!

                Recebemos um pedido para criar uma conta no Wallet com este e-mail. Para ativar a conta, abra o link abaixo em até 24 horas:

                %s

                Se não foi você, ignore esta mensagem. Sem a confirmação a conta não é ativada, e ela é apagada em alguns dias.""".formatted(link)
                + SIGNATURE, "account-confirmation");
    }

    public OutgoingEmail signUpAttempt(String to) {
        return OutgoingEmail.of(to, "Tentativa de cadastro no Wallet", """
                Olá!

                Alguém tentou criar uma conta no Wallet com este e-mail, mas ele já tem uma conta. Se foi você, é só entrar no app com a sua senha.

                Se não foi você, nada muda: ninguém entra na sua conta sem a sua senha.""" + SIGNATURE, "sign-up-attempt");
    }

    public OutgoingEmail locked(String to, LoginThrottle.Lockout lockout) {
        String who = LoginThrottle.Lockout.Scope.ACCOUNT.equals(lockout.scope())
                ? "bloqueamos novas tentativas de entrar na conta, de qualquer lugar,"
                : "bloqueamos novas tentativas vindas da mesma rede (nos outros lugares a conta continua entrando normalmente)";
        return OutgoingEmail.of(to, "Sua conta do Wallet foi bloqueada por um tempo", """
                Olá!

                Houve muitas tentativas de entrar na sua conta com a senha errada, então %s até %s (horário de Brasília).

                Se foi você, espere esse horário e tente de novo. Se não foi, a sua senha continua protegendo a conta; para mais segurança, ligue a verificação em duas etapas em Ajustes.""".formatted(who, WHEN.format(lockout.until()))
                + SIGNATURE, "account-locked");
    }

    public OutgoingEmail confirmNewEmail(String to, String link) {
        return OutgoingEmail.of(to, "Confirme seu novo e-mail no Wallet", """
                Olá!

                Recebemos um pedido para trocar o e-mail de uma conta do Wallet para este endereço. Para confirmar, abra o link abaixo em até 24 horas:

                %s

                Se não foi você, ignore esta mensagem: nada muda.""".formatted(link) + SIGNATURE, "email-change-confirmation");
    }

    public OutgoingEmail emailChanged(String oldAddress, String newAddress, Instant when) {
        return OutgoingEmail.of(oldAddress, "O e-mail da sua conta do Wallet foi trocado", """
                Olá!

                Em %s (horário de Brasília) o e-mail da sua conta do Wallet foi trocado para %s. Daqui em diante, entre com o novo endereço e a mesma senha.

                Se não foi você, alguém usou a sua senha. Entre no Wallet com o endereço acima e a sua senha, troque o e-mail de volta em Ajustes e ligue a verificação em duas etapas.""".formatted(WHEN.format(when), newAddress)
                + SIGNATURE, "email-changed");
    }
}
