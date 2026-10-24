// The page of the "Esqueci minha senha" link: <public URL>/password/reset#token=...
// Like the e-mail confirmation page: the token sits after the "#", so the browser never sends it with
// the page request; it leaves the address bar as soon as it is read, and goes to the BFF only when the
// person has typed the new password twice and pressed the button, so mail scanners that open every
// link change nothing.
(function () {
  'use strict';

  const MIN_LENGTH = 8;
  const DONE = 'Senha trocada! Agora é só entrar no app do Wallet com a senha nova.';
  const ERRORS = {
    'auth.link_invalid': 'Este link venceu ou já foi usado. Peça um novo em "Esqueci minha senha", na tela de entrada do Wallet.',
    'auth.busy': 'O Wallet está com muito movimento agora. Tente de novo em alguns segundos.',
    'service.busy': 'O Wallet está com muito movimento agora. Tente de novo em alguns segundos.',
    'validation.failed': 'A senha precisa ter de 8 a 128 caracteres.',
    'rate_limit.exceeded': 'Muitas tentativas seguidas. Espere um minuto e tente de novo.',
  };
  const UNAVAILABLE = 'Não deu para trocar a senha agora. Confira a internet e tente de novo em instantes.';

  const message = document.getElementById('message');
  const form = document.getElementById('form');
  const password = document.getElementById('password');
  const repeat = document.getElementById('repeat');
  const submit = document.getElementById('submit');
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
    form.hidden = true;
    return;
  }

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    if (password.value.length < MIN_LENGTH) {
      show('A senha precisa ter pelo menos 8 caracteres.', 'error');
      return;
    }
    if (password.value !== repeat.value) {
      show('As senhas não são iguais.', 'error');
      return;
    }
    submit.disabled = true;
    show('Trocando a senha…');
    try {
      const response = await fetch('/api/auth/password/reset', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: token, newPassword: password.value }),
        credentials: 'omit',
      });
      if (response.ok) {
        show(DONE, 'done');
        form.hidden = true;
        return;
      }
      const body = await response.json().catch(function () { return {}; });
      show(ERRORS[body.code] || UNAVAILABLE, 'error');
      // A link that is gone stays gone; anything else may work on a second try.
      form.hidden = body.code === 'auth.link_invalid';
    } catch (error) {
      show(UNAVAILABLE, 'error');
    }
    submit.disabled = false;
  });
})();
