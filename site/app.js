// Waitlist forms: the hero and the final call share the same markup, so one handler serves both.
(function () {
  "use strict";

  var MESSAGES = {
    success: "Quase lá! Enviamos um link para o seu e-mail. Confirme por lá para entrar na lista.",
    invalidEmail: "Confira o e-mail digitado.",
    tooMany: "Muitas tentativas. Tente de novo mais tarde.",
    failure: "Não foi possível enviar agora. Tente de novo em instantes."
  };

  var REQUEST_TIMEOUT_MS = 15000;

  function endpoint() {
    var base = typeof window.WALLET_API_URL === "string" ? window.WALLET_API_URL : "";
    return base.replace(/\/+$/, "") + "/api/waitlist";
  }

  function setupForm(form) {
    var email = form.querySelector("[data-email]");
    var consent = form.querySelector("[data-consent]");
    var hint = form.querySelector("[data-hint]");
    var submit = form.querySelector("[data-submit]");
    var status = form.querySelector("[data-status]");
    var sending = false;

    function showStatus(state, text) {
      status.dataset.state = state;
      status.textContent = text;
    }

    function clearStatus() {
      delete status.dataset.state;
      status.textContent = "";
    }

    function setConsentMissing(missing) {
      hint.hidden = !missing;
      if (missing) {
        consent.setAttribute("aria-invalid", "true");
      } else {
        consent.removeAttribute("aria-invalid");
      }
    }

    consent.addEventListener("change", function () {
      if (consent.checked) {
        setConsentMissing(false);
      }
    });

    form.addEventListener("submit", function (event) {
      event.preventDefault();
      if (sending) {
        return;
      }

      email.value = email.value.trim();
      if (!email.checkValidity()) {
        setConsentMissing(false);
        showStatus("error", MESSAGES.invalidEmail);
        email.focus();
        return;
      }
      if (!consent.checked) {
        clearStatus();
        setConsentMissing(true);
        consent.focus();
        return;
      }

      setConsentMissing(false);
      clearStatus();
      sending = true;
      submit.disabled = true;
      form.setAttribute("aria-busy", "true");

      var controller = new AbortController();
      var timer = window.setTimeout(function () { controller.abort(); }, REQUEST_TIMEOUT_MS);

      fetch(endpoint(), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.value, consent: true }),
        credentials: "omit",
        signal: controller.signal
      }).then(function (response) {
        if (response.status === 202) {
          email.value = "";
          consent.checked = false;
          showStatus("success", MESSAGES.success);
        } else if (response.status === 400) {
          showStatus("error", MESSAGES.invalidEmail);
        } else if (response.status === 429) {
          showStatus("error", MESSAGES.tooMany);
        } else {
          showStatus("error", MESSAGES.failure);
        }
      }).catch(function () {
        showStatus("error", MESSAGES.failure);
      }).then(function () {
        window.clearTimeout(timer);
        sending = false;
        submit.disabled = false;
        form.removeAttribute("aria-busy");
      });
    });
  }

  Array.prototype.forEach.call(document.querySelectorAll("[data-waitlist]"), setupForm);

  // The header button jumps to the hero form; hand the keyboard to its first field as well.
  var heroEmail = document.getElementById("email-hero");
  Array.prototype.forEach.call(document.querySelectorAll('a[href="#lista"]'), function (link) {
    link.addEventListener("click", function () {
      if (heroEmail) {
        heroEmail.focus({ preventScroll: true });
      }
    });
  });
})();
