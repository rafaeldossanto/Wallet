// The confirmation page of the e-mailed links: <public URL>/email/confirm#token=...
// The token sits after the "#", so the browser never sends it with the page request; it leaves
// the address bar (and the history) as soon as it is read, and goes to the BFF only when the
// person presses the button, so mail scanners that open every link confirm nothing.
(function () {
  'use strict';

  const ANSWERS = {
    ACCOUNT_CONFIRMED: 'E-mail confirmado! Agora é só entrar no app do Wallet com o seu e-mail e a sua senha.',
    EMAIL_CHANGED: 'E-mail trocado! Daqui em diante, entre no Wallet com o novo endereço e a mesma senha.',
  };
  const ERRORS = {
    'auth.link_invalid': 'Este link venceu ou já foi usado. Se você já confirmou, é só entrar no app; se não, '
        + 'peça um novo link na tela de entrada do Wallet.',
    'auth.email_taken': 'Este e-mail já tem uma conta no Wallet, então a troca não foi feita.',
    'rate_limit.exceeded': 'Muitas tentativas seguidas. Espere um minuto e tente de novo.',
  };
  const UNAVAILABLE = 'Não deu para confirmar agora. Confira a internet e tente de novo em instantes.';

  const message = document.getElementById('message');
  const button = document.getElementById('confirm');
  const token = new URLSearchParams(window.location.hash.slice(1)).get('token');
  if (window.location.hash) {
    window.history.replaceState(null, '', window.location.pathname);
  }

  function show(text, kind) {
    message.textContent = text;
    message.className = kind || '';
  }

  if (!token) {
    show('Este link está incompleto. Abra de novo o link do e-mail, inteiro.', 'error');
    button.hidden = true;
    return;
  }

  button.addEventListener('click', async function () {
    button.disabled = true;
    show('Confirmando…');
    try {
      const response = await fetch('/api/auth/email/confirm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: token }),
        credentials: 'omit',
      });
      const body = await response.json().catch(function () { return {}; });
      if (response.ok && ANSWERS[body.result]) {
        show(ANSWERS[body.result], 'done');
        button.hidden = true;
        return;
      }
      show(ERRORS[body.code] || UNAVAILABLE, 'error');
      // A link that is gone stays gone; anything else may work on a second try.
      button.hidden = body.code === 'auth.link_invalid' || body.code === 'auth.email_taken';
    } catch (error) {
      show(UNAVAILABLE, 'error');
    }
    button.disabled = false;
  });
})();
