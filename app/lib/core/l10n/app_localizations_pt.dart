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
  String get loginSessionExpired => 'Sua sessão expirou. Entre de novo.';

  @override
  String get loginSessionIdle =>
      'Por segurança, sua sessão terminou depois de 30 minutos sem uso.';

  @override
  String get registerSubtitle => 'Crie sua conta do Wallet.';

  @override
  String get registerAction => 'Criar conta';

  @override
  String get registerGoToLogin => 'Já tenho conta';

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
    return 'Olá, $name';
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
      'Desenvolvimento: com o core em modo demonstração, use demo-banco ou demo-corretora.';

  @override
  String get connectionsLinked =>
      'Conexão vinculada. Os dados chegam em instantes.';

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
  String get settingsBiometric => 'Pedir biometria ao voltar';

  @override
  String get settingsBiometricHelp =>
      'Depois de 5 minutos fora do app, ele pede sua digital, rosto ou senha do aparelho.';

  @override
  String get settingsBiometricUnavailable =>
      'Este aparelho não tem bloqueio de tela configurado.';

  @override
  String get settingsWebSession => 'Sessão no navegador';

  @override
  String get settingsWebSessionHelp =>
      'Por segurança, a sessão termina depois de 30 minutos sem uso.';

  @override
  String get settingsSignOut => 'Sair';

  @override
  String get settingsAbout =>
      'Wallet lê seus dados pelo Open Finance, por meio da Pluggy. Nenhum pagamento sai daqui.';

  @override
  String get errorNetworkUnreachable =>
      'Sem conexão com o servidor. Confira sua internet.';

  @override
  String get errorNetworkTimeout =>
      'O servidor demorou para responder. Tente de novo.';

  @override
  String get errorInvalidCredentials => 'E-mail ou senha incorretos.';

  @override
  String get errorAuthLocked =>
      'Muitas tentativas erradas. Espere 15 minutos e tente de novo.';

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
  String get errorConnectionGone => 'Essa conexão não existe mais.';

  @override
  String get errorConnectionNeedsAttention =>
      'O banco pede uma ação sua antes de atualizar.';

  @override
  String get errorSyncTooSoon =>
      'Esta conexão acabou de atualizar. Tente de novo em alguns minutos.';

  @override
  String get errorSyncInProgress => 'Esta conexão já está atualizando.';

  @override
  String get errorAccountNotFound => 'Conta não encontrada.';

  @override
  String get errorInvalidFilter => 'Filtro inválido.';

  @override
  String get errorUnexpected => 'Algo deu errado. Tente de novo.';
}
