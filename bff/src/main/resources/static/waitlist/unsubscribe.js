// The unsubscribe page of the waitlist's e-mails: <public URL>/waitlist/unsubscribe?token=...
// The token is in the query because the mail provider's one-click button posts to that exact URL
// (RFC 8058). Opening the page removes nothing: mail scanners open every link. The token leaves
// the address bar as soon as it is read, and goes to the BFF only when the button is pressed.
(function () {
  'use strict';

  const DONE = 'Pronto, você saiu da lista. Não vamos mais te mandar e-mails.';
  const ERRORS = {
    'rate_limit.exceeded': 'Muitas tentativas seguidas. Espere um pouco e tente de novo.',
  };
  const UNAVAILABLE = 'Não deu para sair da lista agora. Confira a internet e tente de novo em instantes.';

  const message = document.getElementById('message');
  const button = document.getElementById('unsubscribe');
  const token = new URLSearchParams(window.location.search).get('token');
  if (window.location.search) {
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
    show('Saindo da lista…');
    try {
      const response = await fetch('/api/waitlist/unsubscribe?token=' + encodeURIComponent(token), {
        method: 'POST',
        credentials: 'omit',
      });
      if (response.ok) {
        show(DONE, 'done');
        button.hidden = true;
        return;
      }
      const body = await response.json().catch(function () { return {}; });
      show(ERRORS[body.code] || UNAVAILABLE, 'error');
    } catch (error) {
      show(UNAVAILABLE, 'error');
    }
    button.disabled = false;
  });
})();
