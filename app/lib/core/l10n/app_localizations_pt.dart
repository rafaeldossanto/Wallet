// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Portuguese (`pt`).
class AppLocalizationsPt extends AppLocalizations {
  AppLocalizationsPt([String locale = 'pt']) : super(locale);

  @override
  String get appTitle => 'Wallet';

  @override
  String get actionCancel => 'Cancelar';

  @override
  String get actionRefresh => 'Atualizar';

  @override
  String get actionRetry => 'Tentar de novo';

  @override
  String get actionSeeAll => 'Ver todos';

  @override
  String get actionSeeMore => 'Ver mais';

  @override
  String get actionSave => 'Salvar';

  @override
  String get actionContinue => 'Continuar';

  @override
  String get navOverview => 'Início';

  @override
  String get navTransactions => 'Extrato';

  @override
  String get navCards => 'Cartões';

  @override
  String get navInvestments => 'Investimentos';

  @override
  String get navInvestmentsShort => 'Investir';

  @override
  String get navInsights => 'Gastos';

  @override
  String get navPlanning => 'Planejamento';

  @override
  String get navConnections => 'Conexões';

  @override
  String get navSettings => 'Ajustes';

  @override
  String get navMore => 'Mais';

  @override
  String get dateToday => 'Hoje';

  @override
  String get dateYesterday => 'Ontem';

  @override
  String get monthPrevious => 'Mês anterior';

  @override
  String get monthNext => 'Próximo mês';

  @override
  String get timeAgoNow => 'agora há pouco';

  @override
  String timeAgoMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'há $count minutos',
      one: 'há 1 minuto',
    );
    return '$_temp0';
  }

  @override
  String timeAgoHours(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'há $count horas',
      one: 'há 1 hora',
    );
    return '$_temp0';
  }

  @override
  String timeAgoDays(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'há $count dias',
      one: 'há 1 dia',
    );
    return '$_temp0';
  }

  @override
  String updatedAgo(String when) {
    return 'Atualizado $when';
  }

  @override
  String get splashUnreachable =>
      'Não foi possível falar com o servidor do Wallet. Confira sua conexão.';

  @override
  String get loginSubtitle =>
      'Suas contas, cartões e investimentos num lugar só.';

  @override
  String get loginAction => 'Entrar';

  @override
  String get loginGoToRegister => 'Ainda não tenho conta';

  @override
  String serverLine(String address) {
    return 'Servidor: $address';
  }

  @override
  String get serverChange => 'Alterar';

  @override
  String get serverDialogTitle => 'Endereço do servidor';

  @override
  String get serverDialogHelp =>
      'No celular, use o endereço do computador onde o Wallet está rodando, na mesma rede Wi-Fi. Exemplo: 192.168.0.10:8080.';

  @override
  String get serverFieldLabel => 'Endereço';

  @override
  String get serverInvalid => 'Digite um endereço como 192.168.0.10:8080.';

  @override
  String get serverSave => 'Salvar';

  @override
  String get serverUseDefault => 'Usar o padrão';

  @override
  String get loginSessionExpired => 'Sua sessão expirou. Entre de novo.';

  @override
  String get loginSessionIdle =>
      'Por segurança, sua sessão terminou depois de 30 minutos sem uso.';

  @override
  String get loginAccountDeleted =>
      'Sua conta foi excluída, com todos os seus dados.';

  @override
  String get registerSubtitle => 'Crie sua conta do Wallet.';

  @override
  String get registerAction => 'Criar conta';

  @override
  String get registerGoToLogin => 'Já tenho conta';

  @override
  String get registerCheckEmailTitle => 'Confira seu e-mail';

  @override
  String registerCheckEmailBody(String email) {
    return 'Enviamos um link para $email. Abra o link para ativar a conta e depois entre aqui. Se não chegar em alguns minutos, olhe a caixa de spam.';
  }

  @override
  String get registerResend => 'Reenviar o link';

  @override
  String get registerBackToLogin => 'Voltar para entrar';

  @override
  String get confirmationResent =>
      'Se este e-mail estiver esperando confirmação, um link novo chega em instantes.';

  @override
  String get loginNoConfirmation => 'Não recebeu o e-mail de confirmação?';

  @override
  String get resendDialogTitle => 'Reenviar a confirmação';

  @override
  String get resendDialogHelp =>
      'Digite o e-mail da conta. Se ela estiver esperando confirmação, mandamos um link novo.';

  @override
  String get resendAction => 'Reenviar';

  @override
  String get loginTwoFactorHelp =>
      'Digite o código de 6 dígitos do seu app autenticador, ou um código de recuperação.';

  @override
  String get fieldTwoFactorCode => 'Código';

  @override
  String get fieldName => 'Nome';

  @override
  String get fieldEmail => 'E-mail';

  @override
  String get fieldPassword => 'Senha';

  @override
  String get validationRequired => 'Preencha este campo.';

  @override
  String get validationEmail => 'Digite um e-mail válido.';

  @override
  String validationPasswordLength(int count) {
    return 'Pelo menos $count caracteres.';
  }

  @override
  String get lockTitle => 'Wallet bloqueado';

  @override
  String get lockUnlock => 'Desbloquear';

  @override
  String get lockReason => 'Desbloqueie para ver suas finanças';

  @override
  String get partUnavailable =>
      'Esta parte não carregou agora. Puxe para atualizar.';

  @override
  String refreshFailed(String reason) {
    return 'Não deu para atualizar: $reason';
  }

  @override
  String get institutionUnknown => 'Instituição';

  @override
  String overviewGreeting(String name) {
    return 'Olá, $name!';
  }

  @override
  String get overviewEmpty =>
      'Conecte um banco para ver seus saldos, cartões e investimentos aqui.';

  @override
  String get overviewConnectFirst => 'Conectar banco';

  @override
  String get overviewNetWorth => 'Patrimônio';

  @override
  String get overviewCash => 'Em conta';

  @override
  String get overviewInvestments => 'Investimentos';

  @override
  String get overviewCardDebt => 'Faturas de cartão';

  @override
  String get overviewMonth => 'Este mês';

  @override
  String get overviewIncome => 'Entradas';

  @override
  String get overviewExpenses => 'Saídas';

  @override
  String get overviewMonthBalance => 'Saldo do mês';

  @override
  String get overviewAccounts => 'Contas';

  @override
  String get overviewNoBankAccounts =>
      'Nenhuma conta corrente ou poupança nesta conexão.';

  @override
  String get overviewRecent => 'Últimas movimentações';

  @override
  String get overviewSeeStatement => 'Ver extrato';

  @override
  String get overviewNoRecent => 'Nenhuma movimentação nos últimos 30 dias.';

  @override
  String get calendarTitle => 'Dias com gastos';

  @override
  String calendarSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count dias com gastos',
      one: '1 dia com gastos',
      zero: 'Nenhum dia com gastos',
    );
    return '$_temp0';
  }

  @override
  String calendarDaySpending(String date, String amount) {
    return '$date: $amount em gastos';
  }

  @override
  String calendarDayNoSpending(String date) {
    return '$date: nenhum gasto';
  }

  @override
  String calendarListTitle(String period) {
    return 'Gastos · $period';
  }

  @override
  String get calendarHintPick => 'Toque num dia para ver só os gastos dele.';

  @override
  String get calendarHintUnpick =>
      'Toque de novo no dia para voltar ao mês inteiro.';

  @override
  String get calendarMonthEmpty => 'Nenhum gasto neste mês.';

  @override
  String get calendarDayEmpty => 'Nenhum gasto neste dia.';

  @override
  String get themeToLight => 'Tema claro';

  @override
  String get themeToDark => 'Tema escuro';

  @override
  String get settingsAppearance => 'Aparência';

  @override
  String get themeLight => 'Claro';

  @override
  String get themeDark => 'Escuro';

  @override
  String get themeSystem => 'Automático';

  @override
  String get transactionsSearch => 'Buscar pela descrição';

  @override
  String get transactionsEmpty => 'Nenhuma movimentação com esses filtros.';

  @override
  String get transactionPending => 'Pendente';

  @override
  String transactionInstallment(String installment) {
    return 'Parcela $installment';
  }

  @override
  String get filterAll => 'Tudo';

  @override
  String get filterInflow => 'Entradas';

  @override
  String get filterOutflow => 'Saídas';

  @override
  String get filterAccount => 'Conta';

  @override
  String get filterAllAccounts => 'Todas as contas';

  @override
  String get cardsEmpty => 'Nenhum cartão de crédito conectado.';

  @override
  String get cardCurrentBill => 'Fatura atual';

  @override
  String get cardDueDate => 'Vencimento';

  @override
  String get cardClosingDate => 'Fechamento';

  @override
  String get cardMinimumPayment => 'Pagamento mínimo';

  @override
  String get cardNoBill => 'O banco ainda não mandou faturas deste cartão.';

  @override
  String cardAvailable(String available, String limit) {
    return 'Disponível $available de $limit';
  }

  @override
  String cardCurrentBalance(String amount) {
    return 'Gasto em aberto no cartão: $amount';
  }

  @override
  String get cardSeeBills => 'Faturas';

  @override
  String get cardSeePurchases => 'Compras';

  @override
  String get cardBills => 'Faturas';

  @override
  String get cardBillsEmpty => 'Nenhuma fatura ainda.';

  @override
  String cardBillDue(String date) {
    return 'Vence em $date';
  }

  @override
  String cardBillPastDue(String date) {
    return 'Venceu em $date';
  }

  @override
  String get cardLastBill => 'Última fatura';

  @override
  String cardBillClosed(String date) {
    return 'fechou em $date';
  }

  @override
  String cardBillMinimum(String amount) {
    return 'mínimo $amount';
  }

  @override
  String get cardBillOpen => 'Em aberto';

  @override
  String get investmentsEmpty => 'Nenhum investimento conectado.';

  @override
  String get investmentsTotal => 'Total investido';

  @override
  String investmentDue(String date) {
    return 'vence em $date';
  }

  @override
  String get investmentKindFixedIncome => 'Renda fixa';

  @override
  String get investmentKindTreasury => 'Tesouro Direto';

  @override
  String get investmentKindFund => 'Fundos';

  @override
  String get investmentKindEquity => 'Ações e ETFs';

  @override
  String get investmentKindRetirement => 'Previdência';

  @override
  String get investmentKindOther => 'Outros';

  @override
  String get investmentsAllocation => 'Distribuição dos investimentos';

  @override
  String get donutHintPick =>
      'Toque numa fatia para ver a porcentagem e o valor dela.';

  @override
  String get donutHintUnpick => 'Toque de novo na fatia para voltar ao total.';

  @override
  String get investmentsEvolution => 'Evolução dos investimentos';

  @override
  String get investmentsEvolutionGrowing =>
      'O gráfico começa no primeiro dia de uso e ganha um ponto a cada dia em que o Wallet atualiza seus investimentos.';

  @override
  String investmentsChange(String amount, String percent) {
    return '$amount ($percent) no período';
  }

  @override
  String get investmentPeriodOneMonth => '1M';

  @override
  String get investmentPeriodOneMonthHint => '1 mês';

  @override
  String get investmentPeriodThreeMonths => '3M';

  @override
  String get investmentPeriodThreeMonthsHint => '3 meses';

  @override
  String get investmentPeriodSixMonths => '6M';

  @override
  String get investmentPeriodSixMonthsHint => '6 meses';

  @override
  String get investmentPeriodOneYear => '1A';

  @override
  String get investmentPeriodOneYearHint => '1 ano';

  @override
  String get investmentPeriodAll => 'Tudo';

  @override
  String get investmentPeriodAllHint => 'Todo o período';

  @override
  String get insightsSpending => 'Gastos por categoria';

  @override
  String get insightsTotal => 'Total';

  @override
  String get insightsNoSpending => 'Nenhum gasto neste mês.';

  @override
  String get insightsOtherCategories => 'Outras categorias';

  @override
  String get insightsNetWorth => 'Evolução do patrimônio';

  @override
  String get insightsNetWorthGrowing =>
      'O gráfico começa no primeiro dia de uso e ganha um ponto a cada dia em que o Wallet atualiza seus dados.';

  @override
  String get planningLeftover => 'Sobra do mês';

  @override
  String get planningLeftoverFormula => 'Salário − faturas − gastos fixos';

  @override
  String planningShortfall(String amount) {
    return 'Faltam $amount para fechar o mês.';
  }

  @override
  String get planningNoSalaryHint =>
      'Sem salário para este mês. Toque em Ajustar no salário e informe quanto você recebe.';

  @override
  String get planningLegendFixed => 'Gastos fixos';

  @override
  String get planningLegendLeftover => 'Sobra';

  @override
  String get planningSalary => 'Salário líquido';

  @override
  String get planningSalaryAdjust => 'Ajustar';

  @override
  String get planningSalaryPredicted => 'Previsto';

  @override
  String planningSalaryReceived(String date) {
    return 'Caiu na conta em $date.';
  }

  @override
  String planningSalaryFromPreviousMonth(String date) {
    return 'Previsto pelo salário do mês anterior. Deve cair por volta de $date.';
  }

  @override
  String get planningSalaryExpected =>
      'Valor que você definiu. Vale até o salário do mês cair na conta.';

  @override
  String get planningSalaryNone =>
      'Nenhum salário no extrato deste mês nem dos anteriores.';

  @override
  String get planningSalaryDialogTitle => 'Salário líquido esperado';

  @override
  String get planningSalaryDialogHelp =>
      'Usado enquanto o salário do mês não cai na conta. Quando ele cair, vale o valor do extrato.';

  @override
  String get planningSalaryField => 'Valor líquido';

  @override
  String get planningSalaryUseAutomatic => 'Usar o automático';

  @override
  String get planningSalarySaved => 'Salário esperado salvo.';

  @override
  String get planningSalaryCleared =>
      'O salário voltou a ser previsto pelo mês anterior.';

  @override
  String get planningAmountInvalid => 'Digite um valor como 1.234,56.';

  @override
  String get planningBillOpen => 'Aberta';

  @override
  String planningBillOpenHelp(String date) {
    return 'Fecha por volta de $date e ainda pode crescer.';
  }

  @override
  String planningBillMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'mais $count compras',
      one: 'mais 1 compra',
    );
    return '$_temp0';
  }

  @override
  String get planningBillLess => 'Mostrar menos';

  @override
  String get planningBillRefund => 'Estorno';

  @override
  String get planningBillOtherCharges => 'Outros lançamentos';

  @override
  String get planningBillOtherChargesHelp =>
      'Tarifas, juros ou compras que o banco não detalhou.';

  @override
  String get planningBillOtherCreditsHelp =>
      'Créditos que o banco não detalhou.';

  @override
  String get planningBillNoItems =>
      'O banco não detalhou as compras desta fatura.';

  @override
  String get planningBills => 'Faturas';

  @override
  String get planningNoBills => 'Nenhuma fatura de cartão vence neste mês.';

  @override
  String get planningFixed => 'Gastos fixos';

  @override
  String get planningFixedAdd => 'Adicionar';

  @override
  String get planningFixedEmpty =>
      'Mensalidade, aluguel, boletos: o que não passa no cartão. Adicione uma vez e ele entra em todo mês.';

  @override
  String planningFixedDay(int day) {
    return 'Todo dia $day';
  }

  @override
  String get planningFixedTotal => 'Total';

  @override
  String get planningFixedNew => 'Novo gasto fixo';

  @override
  String get planningFixedEdit => 'Editar gasto fixo';

  @override
  String get planningFixedHelp =>
      'Ele se repete todo mês. Nos dias 29, 30 e 31, cai no último dia dos meses mais curtos.';

  @override
  String get planningFixedAmount => 'Valor';

  @override
  String get planningFixedDayField => 'Dia do mês';

  @override
  String get planningFixedDayInvalid => 'Um dia de 1 a 31.';

  @override
  String get planningFixedCategory => 'Categoria (opcional)';

  @override
  String get planningFixedDelete => 'Excluir';

  @override
  String planningFixedDeleteConfirm(String name) {
    return 'Excluir $name? Ele sai de todos os meses.';
  }

  @override
  String get planningFixedSaved => 'Gasto fixo salvo.';

  @override
  String get planningFixedDeleted => 'Gasto fixo excluído.';

  @override
  String get planningSuggestions => 'Sugestões do seu extrato';

  @override
  String get planningSuggestionsHelp =>
      'Débitos que se repetem todo mês nas suas contas. Toque em um para adicionar.';

  @override
  String planningSuggestionDetails(String amount, int day, int months) {
    return '$amount · todo dia $day · em $months meses';
  }

  @override
  String get planningSuggestionAdd => 'Adicionar aos gastos fixos';

  @override
  String get planningSuggestionDismiss => 'Dispensar sugestão';

  @override
  String get planningSuggestionDismissed =>
      'Sugestão dispensada. Ela não volta a aparecer.';

  @override
  String planningSuggestionsMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'mais $count sugestões',
      one: 'mais 1 sugestão',
    );
    return '$_temp0';
  }

  @override
  String overviewLeftoverDetail(String salary, String bills, String fixed) {
    return 'Salário $salary · faturas $bills · fixos $fixed';
  }

  @override
  String get overviewLeftoverPredicted => 'Com o salário previsto.';

  @override
  String get overviewSeePlanning => 'Ver planejamento';

  @override
  String get connectionsEmpty =>
      'Nenhuma conexão ainda. Conecte seu banco no Meu Pluggy e vincule aqui pelo Item ID.';

  @override
  String get connectionsLink => 'Vincular conexão';

  @override
  String get connectionsLinkAction => 'Vincular';

  @override
  String get connectionsLinkHelp =>
      'Conecte o banco no painel do Meu Pluggy, abra a conexão e copie o Item ID. O Wallet só lê os dados: não faz pagamentos nem transferências.';

  @override
  String get connectionsItemId => 'Item ID';

  @override
  String get connectionsItemIdInvalid =>
      'O Item ID tem só letras, números e hífens.';

  @override
  String get connectionsDemoHint =>
      'Desenvolvimento: com o core em modo demonstração, use demo-nubank, demo-itau ou demo-xp (ou os básicos demo-banco e demo-corretora).';

  @override
  String get connectionsLinked =>
      'Conexão vinculada. Os dados chegam em instantes.';

  @override
  String get connectionsConnect => 'Conectar banco';

  @override
  String get connectionsEmptyConnect =>
      'Nenhuma conexão ainda. Toque em Conectar banco, escolha o banco e autorize no app dele.';

  @override
  String get connectionsLinkByItemId => 'Vincular pelo Item ID';

  @override
  String get connectionsConnected =>
      'Banco conectado. Os dados chegam em instantes.';

  @override
  String get connectOpening => 'Abrindo a Pluggy…';

  @override
  String get connectPageFailed =>
      'Não foi possível abrir a Pluggy. Confira sua internet e tente de novo.';

  @override
  String get connectionNeverSynced => 'Ainda não sincronizou';

  @override
  String connectionConsentUntil(String date) {
    return 'Consentimento válido até $date';
  }

  @override
  String get connectionNeedsAttention => 'O banco pede uma ação sua';

  @override
  String get connectionNeedsAttentionHelp =>
      'O banco pede uma ação sua: abra o Meu Pluggy, reconecte esta conta e depois toque em Atualizar.';

  @override
  String get connectionSync => 'Atualizar';

  @override
  String get connectionSyncing => 'Atualizando…';

  @override
  String get connectionSyncStarted => 'Atualização iniciada.';

  @override
  String get connectionUnlink => 'Desvincular';

  @override
  String connectionUnlinkConfirm(String name) {
    return 'Desvincular $name? As contas, cartões e investimentos desta conexão saem do Wallet.';
  }

  @override
  String get connectionUnlinked => 'Conexão desvinculada.';

  @override
  String get connectionStatusActive => 'Ativa';

  @override
  String get connectionStatusSyncing => 'Atualizando';

  @override
  String get connectionStatusNeedsAttention => 'Precisa de atenção';

  @override
  String get connectionStatusUnknown => 'Indefinido';

  @override
  String get settingsAccount => 'Conta';

  @override
  String get settingsSecurity => 'Segurança';

  @override
  String get settingsEditName => 'Alterar o nome';

  @override
  String get settingsChangeEmail => 'Trocar o e-mail';

  @override
  String settingsPendingEmail(String email) {
    return 'Aguardando a confirmação de $email';
  }

  @override
  String get nameDialogTitle => 'Seu nome';

  @override
  String get emailDialogTitle => 'Trocar o e-mail';

  @override
  String get emailDialogHelp =>
      'Mandamos um link para o novo endereço. A troca só vale depois que você abrir o link, e o e-mail atual recebe um aviso.';

  @override
  String get fieldNewEmail => 'Novo e-mail';

  @override
  String get fieldYourPassword => 'Sua senha';

  @override
  String emailDialogSent(String email) {
    return 'Link enviado para $email. Abra-o para concluir a troca.';
  }

  @override
  String get settingsTwoFactor => 'Verificação em duas etapas';

  @override
  String get settingsTwoFactorOn =>
      'Ligada: entrar pede também o código do app autenticador.';

  @override
  String get settingsTwoFactorOff =>
      'Desligada. Ligue para pedir também um código do app autenticador ao entrar.';

  @override
  String get twoFactorSetupTitle => 'Ligar a verificação em duas etapas';

  @override
  String get twoFactorPasswordHelp => 'Para começar, confirme a sua senha.';

  @override
  String get twoFactorScanHelp =>
      'Escaneie o QR code com um app autenticador (Google Authenticator, Microsoft Authenticator, 1Password...). Sem câmera? Digite a chave abaixo no app.';

  @override
  String get twoFactorSecretLabel => 'Chave';

  @override
  String get twoFactorCodeHelp =>
      'Depois, digite o código de 6 dígitos que aparece no app.';

  @override
  String get twoFactorEnableAction => 'Ligar';

  @override
  String get twoFactorRecoveryTitle => 'Guarde os códigos de recuperação';

  @override
  String get twoFactorRecoveryHelp =>
      'Cada código funciona uma vez, no lugar do app, se você perder o celular. Guarde num lugar seguro: eles não aparecem de novo.';

  @override
  String get twoFactorCopyCodes => 'Copiar os códigos';

  @override
  String get twoFactorCopied => 'Códigos copiados.';

  @override
  String get twoFactorDone => 'Pronto';

  @override
  String get twoFactorDisableTitle => 'Desligar a verificação em duas etapas';

  @override
  String get twoFactorDisableHelp =>
      'Confirme a sua senha e um código do app autenticador (ou um código de recuperação).';

  @override
  String get twoFactorDisableAction => 'Desligar';

  @override
  String get settingsBiometric => 'Pedir biometria ao voltar';

  @override
  String get settingsBiometricHelp =>
      'Depois de 5 minutos fora do app, ele pede sua digital, rosto ou senha do aparelho.';

  @override
  String get settingsBiometricUnavailable =>
      'Este aparelho não tem bloqueio de tela configurado.';

  @override
  String get settingsWindowsHello => 'Pedir o Windows Hello ao voltar';

  @override
  String get settingsWindowsHelloHelp =>
      'Depois de 5 minutos com o Wallet minimizado, ele pede o PIN, o rosto ou a digital do Windows Hello.';

  @override
  String get settingsWindowsHelloUnavailable =>
      'O Windows Hello não está configurado neste computador.';

  @override
  String get settingsWebSession => 'Sessão no navegador';

  @override
  String get settingsWebSessionHelp =>
      'Por segurança, a sessão termina depois de 30 minutos sem uso.';

  @override
  String get settingsSignOut => 'Sair';

  @override
  String get settingsYourData => 'Seus dados';

  @override
  String get settingsDownloadData => 'Baixar meus dados';

  @override
  String get settingsDownloadDataHelp =>
      'Um arquivo com tudo o que o Wallet guarda sobre você: perfil, bancos, contas, transações, cartões, investimentos e planejamento.';

  @override
  String get settingsDownloadDataBusy => 'Preparando o arquivo…';

  @override
  String settingsDownloadDataDone(String fileName) {
    return 'Pronto: $fileName';
  }

  @override
  String settingsDownloadDataDoneIn(String fileName, String folder) {
    return 'Pronto: $fileName, salvo em $folder';
  }

  @override
  String get settingsDownloadDataLimit =>
      'Você já baixou seus dados várias vezes hoje. Tente de novo amanhã.';

  @override
  String get settingsDownloadDataCancelled => 'Download cancelado.';

  @override
  String get settingsDownloadDataFailed =>
      'Não foi possível salvar o arquivo neste aparelho.';

  @override
  String get settingsDangerZone => 'Zona de perigo';

  @override
  String get settingsDeleteAccount => 'Excluir conta';

  @override
  String get settingsDeleteAccountHelp =>
      'Apaga sua conta e todos os dados no Wallet, e revoga o acesso aos seus bancos.';

  @override
  String get deleteAccountTitle => 'Excluir sua conta?';

  @override
  String get deleteAccountWarning =>
      'Isso não pode ser desfeito. Vão embora de vez:';

  @override
  String get deleteAccountWhatGoes =>
      '• suas contas, cartões, faturas, transações e investimentos no Wallet;\n• seu planejamento e gastos fixos;\n• o acesso do Wallet aos seus bancos: as conexões são revogadas na Pluggy.';

  @override
  String get deleteAccountConfirmCheck => 'Entendo que não dá para desfazer';

  @override
  String get deleteAccountPasswordHelp =>
      'Para confirmar, digite a senha da sua conta. Cada senha errada conta para o bloqueio temporário da conta.';

  @override
  String get deleteAccountPasswordRequired =>
      'Digite sua senha para confirmar.';

  @override
  String get deleteAccountAction => 'Excluir de vez';

  @override
  String get deleteAccountInProgress =>
      'Revogando o acesso aos seus bancos e apagando os dados. Pode levar até 2 minutos; não feche o app.';

  @override
  String get deleteAccountNotConfirmed =>
      'Não deu para confirmar a exclusão. Tente de novo: se a conta já tiver sido excluída, o Wallet avisa.';

  @override
  String get showPassword => 'Mostrar senha';

  @override
  String get hidePassword => 'Esconder senha';

  @override
  String get settingsAbout =>
      'Wallet lê seus dados pelo Open Finance, por meio da Pluggy. Nenhum pagamento sai daqui.';

  @override
  String get settingsDesktop => 'Computador';

  @override
  String get settingsCloseToTray => 'Ao fechar, continuar na bandeja';

  @override
  String get settingsCloseToTrayHelp =>
      'O X esconde o Wallet perto do relógio, onde ele continua se atualizando. Para sair de vez, use Sair no ícone da bandeja.';

  @override
  String get settingsLaunchAtStartup => 'Abrir com o Windows';

  @override
  String get settingsLaunchAtStartupHelp =>
      'Quando você entra no Windows, o Wallet já abre na bandeja.';

  @override
  String settingsVersion(String version) {
    return 'Versão $version';
  }

  @override
  String get trayOpen => 'Abrir o Wallet';

  @override
  String get trayQuit => 'Sair';

  @override
  String get updateDisabled =>
      'Build de desenvolvimento: não se atualiza sozinho.';

  @override
  String get updateUpToDate => 'Você está na versão mais nova.';

  @override
  String get updateChecking => 'Procurando atualizações…';

  @override
  String updateDownloading(String version) {
    return 'Baixando a versão $version…';
  }

  @override
  String updateReady(String version) {
    return 'A versão $version já foi baixada.';
  }

  @override
  String get updateFailed =>
      'Não deu para procurar atualizações agora. O Wallet tenta de novo mais tarde.';

  @override
  String get updateCheckNow => 'Procurar agora';

  @override
  String get updateRestart => 'Reiniciar e atualizar';

  @override
  String get updateLater => 'Depois';

  @override
  String get updateToastTitle => 'Nova versão disponível';

  @override
  String updateToastMessage(String version) {
    return 'O Wallet $version já foi baixado. Reinicie para atualizar.';
  }

  @override
  String get updateRestartShort => 'Reiniciar';

  @override
  String get updatedToastTitle => 'Wallet atualizado';

  @override
  String updatedToastMessage(String version) {
    return 'Agora você está na versão $version.';
  }

  @override
  String get updatedToastOk => 'Ok';

  @override
  String get settingsUpdates => 'Atualizações';

  @override
  String get updateInstall => 'Atualizar';

  @override
  String updateToastMessageConfirm(String version) {
    return 'O Wallet $version já foi baixado. Toque em Atualizar para instalar.';
  }

  @override
  String get updateAllowTitle => 'Permita as atualizações';

  @override
  String updateAllowMessage(String version) {
    return 'Na primeira vez, o Android pede para o Wallet poder instalar a versão $version. Em Configurações, ligue Permitir desta fonte e volte para o Wallet.';
  }

  @override
  String get updateAllowOpen => 'Abrir configurações';

  @override
  String updateNotAllowed(String version) {
    return 'Para instalar a versão $version, o Wallet precisa da sua permissão: em Configurações, ligue Permitir desta fonte.';
  }

  @override
  String get updateRefusedTitle => 'Atualização não instalada';

  @override
  String updateConflict(String version) {
    return 'O Android recusou a versão $version porque ela foi assinada com outra chave. Desinstale o Wallet e instale o APK dos Releases.';
  }

  @override
  String updateInstallFailed(String version) {
    return 'O Android não instalou a versão $version. Tente de novo.';
  }

  @override
  String get updateRetry => 'Tentar de novo';

  @override
  String get windowClose => 'Fechar';

  @override
  String get windowMinimize => 'Minimizar';

  @override
  String get windowMaximize => 'Maximizar';

  @override
  String get settingsWindowBackground => 'Fundo da janela';

  @override
  String get settingsWindowBackgroundHelp =>
      'Translúcido deixa ver, desfocado, o que está atrás da janela.';

  @override
  String get settingsWindowTranslucent => 'Translúcido';

  @override
  String get settingsWindowSolid => 'Sólido';

  @override
  String get errorNetworkUnreachable =>
      'Sem conexão com o servidor. Confira sua internet.';

  @override
  String get errorNetworkTimeout =>
      'O servidor demorou para responder. Tente de novo.';

  @override
  String get errorInvalidCredentials =>
      'E-mail ou senha incorretos. Se você acabou de criar a conta, abra antes o link que enviamos para o seu e-mail.';

  @override
  String get errorTwoFactorRequired =>
      'Digite o código do seu app autenticador.';

  @override
  String get errorInvalidTwoFactorCode =>
      'Código incorreto. Confira o app autenticador; cada erro conta para o bloqueio.';

  @override
  String get errorBusy =>
      'O Wallet está com muito movimento agora. Tente de novo em alguns segundos.';

  @override
  String get errorTwoFactorSetupCode =>
      'O código não confere. Confira o horário do celular e digite o código que estiver aparecendo agora.';

  @override
  String get errorTwoFactorNotSetUp =>
      'Comece de novo a configuração da verificação em duas etapas.';

  @override
  String get errorTwoFactorAlreadyEnabled =>
      'A verificação em duas etapas já está ligada.';

  @override
  String get errorTwoFactorNotEnabled =>
      'A verificação em duas etapas já está desligada.';

  @override
  String get errorEmailUnchanged => 'Esse já é o e-mail da conta.';

  @override
  String get errorLinkInvalid => 'Este link venceu ou já foi usado.';

  @override
  String get errorAuthLocked =>
      'Muitas tentativas erradas. Espere alguns minutos e tente de novo.';

  @override
  String errorAuthLockedUntil(String when) {
    return 'Muitas tentativas erradas. Tente de novo $when.';
  }

  @override
  String lockedUntilToday(String time) {
    return 'às $time';
  }

  @override
  String lockedUntilDay(String date, String time) {
    return 'em $date às $time';
  }

  @override
  String get errorWrongPassword =>
      'Senha incorreta. Cada erro conta para o bloqueio da conta.';

  @override
  String get errorEmailTaken => 'Já existe uma conta com este e-mail.';

  @override
  String get errorSessionExpired => 'Sua sessão expirou. Entre de novo.';

  @override
  String get errorValidation => 'Confira os campos e tente de novo.';

  @override
  String get errorRateLimited =>
      'Muitas requisições seguidas. Espere um minuto.';

  @override
  String get errorServiceUnavailable =>
      'O serviço está fora do ar agora. Tente em alguns minutos.';

  @override
  String get errorProviderNotConfigured =>
      'O servidor ainda não tem as credenciais da Pluggy.';

  @override
  String get errorProviderAuthFailed =>
      'A Pluggy recusou as credenciais do servidor.';

  @override
  String get errorProviderUpdating =>
      'O banco ainda está atualizando os dados. Tente em alguns minutos.';

  @override
  String get errorItemNotFound => 'Não achamos esse Item ID na Pluggy.';

  @override
  String get errorAlreadyLinked => 'Essa conexão já está no Wallet.';

  @override
  String get errorConnectionLimit =>
      'Você chegou ao limite de conexões. Desvincule uma para vincular outra.';

  @override
  String get errorConnectAttempts =>
      'Muitas tentativas de conectar banco em pouco tempo. Tente de novo mais tarde.';

  @override
  String get errorConnectionGone => 'Essa conexão não existe mais.';

  @override
  String get errorConnectionNeedsAttention =>
      'O banco pede uma ação sua antes de atualizar.';

  @override
  String get errorSyncTooSoon =>
      'Esta conexão tentou atualizar há poucos minutos. Tente de novo daqui a pouco.';

  @override
  String get errorSyncInProgress => 'Esta conexão já está atualizando.';

  @override
  String get errorAccountNotFound => 'Conta não encontrada.';

  @override
  String get errorInvalidFilter => 'Filtro inválido.';

  @override
  String get errorFixedExpenseNotFound => 'Esse gasto fixo não existe mais.';

  @override
  String get errorFixedExpenseLimit =>
      'Você chegou ao limite de 100 gastos fixos.';

  @override
  String get errorFixedExpenseSuggestionNotFound =>
      'Essa sugestão não está mais disponível.';

  @override
  String get errorUnexpected => 'Algo deu errado. Tente de novo.';

  @override
  String get errorConsentExpired =>
      'A autorização deste banco venceu. Renove para voltar a atualizar.';

  @override
  String get errorConsentNotRenewed =>
      'A renovação ainda não apareceu no banco. Tente de novo em instantes.';

  @override
  String get consentTitle => 'Antes de conectar';

  @override
  String get consentPurposeHeading => 'Para que';

  @override
  String get consentPurpose =>
      'Mostrar seus saldos, extratos, cartões, faturas e investimentos no Wallet.';

  @override
  String get consentDataHeading => 'Dados que o Wallet lê';

  @override
  String get consentDataAccounts => 'Contas e saldos';

  @override
  String get consentDataTransactions =>
      'Lançamentos (extrato) das contas e dos cartões';

  @override
  String get consentDataCards => 'Cartões de crédito e faturas';

  @override
  String get consentDataInvestments => 'Investimentos';

  @override
  String get consentReadOnly => 'O Wallet só lê: não movimenta dinheiro.';

  @override
  String get consentValidityHeading => 'Por quanto tempo';

  @override
  String get consentValidity =>
      'Até você desconectar aqui ou no app do banco, ou até a autorização vencer.';

  @override
  String get consentRevokeHeading => 'Como revogar';

  @override
  String get consentRevoke =>
      'Em Conexões, toque em Desvincular. Você também pode cancelar a autorização no app do banco.';

  @override
  String get consentCheckbox =>
      'Autorizo o Wallet a ler esses dados para essa finalidade.';

  @override
  String get consentContinue => 'Continuar';

  @override
  String get connectionConsentExpired =>
      'A autorização deste banco venceu. Renove para voltar a atualizar.';

  @override
  String connectionConsentExpiresIn(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'A autorização vence em $count dias.',
      one: 'A autorização vence em 1 dia.',
      zero: 'A autorização vence hoje.',
    );
    return '$_temp0';
  }

  @override
  String get connectionRenew => 'Renovar';

  @override
  String get connectionRenewOnPhone => 'Renove pelo app no celular.';

  @override
  String get connectionRenewed => 'Autorização renovada.';

  @override
  String get connectionStatusConsentExpired => 'Autorização vencida';

  @override
  String get loginForgotPassword => 'Esqueci minha senha';

  @override
  String get loginPasswordChanged =>
      'Senha trocada. Entre de novo com a senha nova.';

  @override
  String get forgotSubtitle => 'Vamos mandar um link para criar uma senha nova';

  @override
  String get forgotHelp => 'Digite o e-mail da sua conta.';

  @override
  String get forgotAction => 'Enviar o link';

  @override
  String get forgotSent =>
      'Se houver uma conta com esse e-mail, enviamos um link para criar uma senha nova.';

  @override
  String get settingsChangePassword => 'Trocar senha';

  @override
  String get settingsChangePasswordHelp =>
      'Pede a senha atual e tira todos os aparelhos da conta.';

  @override
  String get changePasswordHelp =>
      'Depois de trocar, todos os aparelhos saem da conta, este também, e você entra de novo com a senha nova.';

  @override
  String get changePasswordAction => 'Trocar senha';

  @override
  String get fieldCurrentPassword => 'Senha atual';

  @override
  String get fieldNewPassword => 'Nova senha';

  @override
  String get fieldRepeatNewPassword => 'Repita a nova senha';

  @override
  String get validationPasswordsDiffer => 'As senhas não são iguais.';

  @override
  String get validationPasswordSame =>
      'A nova senha precisa ser diferente da atual.';

  @override
  String get settingsNewRecoveryCodes => 'Gerar códigos novos';

  @override
  String get settingsNewRecoveryCodesHelp =>
      'Os códigos de recuperação de agora deixam de valer.';

  @override
  String get newRecoveryCodesTitle => 'Gerar códigos de recuperação novos';

  @override
  String get newRecoveryCodesHelp =>
      'Digite sua senha e um código do app autenticador. Os códigos de recuperação de agora deixam de valer.';

  @override
  String get newRecoveryCodesAction => 'Gerar';
}
