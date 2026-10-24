// The confirmation page of the waitlist's e-mailed link: <public URL>/waitlist/confirm#token=...
// Like the account's (/email/confirm): the token sits after the "#", so the browser never sends it
// with the page request; it leaves the address bar as soon as it is read, and goes to the BFF only
// when the person presses the button, so mail scanners that open every link confirm nothing.
(function () {
  'use strict';

  const DONE = 'Pronto! Você está na lista do Wallet.';
  const ERRORS = {
    'waitlist.invalid_token': 'Este link venceu ou já foi usado.',
    'rate_limit.exceeded': 'Muitas tentativas seguidas. Espere um pouco e tente de novo.',
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
      const response = await fetch('/api/waitlist/confirm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: token }),
        credentials: 'omit',
      });
      if (response.ok) {
        show(DONE, 'done');
        button.hidden = true;
        return;
      }
      const body = await response.json().catch(function () { return {}; });
      show(ERRORS[body.code] || UNAVAILABLE, 'error');
      // A link that is gone stays gone; anything else may work on a second try.
      button.hidden = body.code === 'waitlist.invalid_token';
    } catch (error) {
      show(UNAVAILABLE, 'error');
    }
    button.disabled = false;
  });
})();
