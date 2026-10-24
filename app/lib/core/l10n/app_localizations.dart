import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_pt.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[Locale('pt')];

  /// No description provided for @appTitle.
  ///
  /// In pt, this message translates to:
  /// **'Wallet'**
  String get appTitle;

  /// No description provided for @actionCancel.
  ///
  /// In pt, this message translates to:
  /// **'Cancelar'**
  String get actionCancel;

  /// No description provided for @actionRefresh.
  ///
  /// In pt, this message translates to:
  /// **'Atualizar'**
  String get actionRefresh;

  /// No description provided for @actionRetry.
  ///
  /// In pt, this message translates to:
  /// **'Tentar de novo'**
  String get actionRetry;

  /// No description provided for @actionSeeAll.
  ///
  /// In pt, this message translates to:
  /// **'Ver todos'**
  String get actionSeeAll;

  /// No description provided for @actionSeeMore.
  ///
  /// In pt, this message translates to:
  /// **'Ver mais'**
  String get actionSeeMore;

  /// No description provided for @actionSave.
  ///
  /// In pt, this message translates to:
  /// **'Salvar'**
  String get actionSave;

  /// No description provided for @actionContinue.
  ///
  /// In pt, this message translates to:
  /// **'Continuar'**
  String get actionContinue;

  /// No description provided for @navOverview.
  ///
  /// In pt, this message translates to:
  /// **'Início'**
  String get navOverview;

  /// No description provided for @navTransactions.
  ///
  /// In pt, this message translates to:
  /// **'Extrato'**
  String get navTransactions;

  /// No description provided for @navCards.
  ///
  /// In pt, this message translates to:
  /// **'Cartões'**
  String get navCards;

  /// No description provided for @navInvestments.
  ///
  /// In pt, this message translates to:
  /// **'Investimentos'**
  String get navInvestments;

  /// No description provided for @navInvestmentsShort.
  ///
  /// In pt, this message translates to:
  /// **'Investir'**
  String get navInvestmentsShort;

  /// No description provided for @navInsights.
  ///
  /// In pt, this message translates to:
  /// **'Gastos'**
  String get navInsights;

  /// No description provided for @navPlanning.
  ///
  /// In pt, this message translates to:
  /// **'Planejamento'**
  String get navPlanning;

  /// No description provided for @navConnections.
  ///
  /// In pt, this message translates to:
  /// **'Conexões'**
  String get navConnections;

  /// No description provided for @navSettings.
  ///
  /// In pt, this message translates to:
  /// **'Ajustes'**
  String get navSettings;

  /// No description provided for @navMore.
  ///
  /// In pt, this message translates to:
  /// **'Mais'**
  String get navMore;

  /// No description provided for @dateToday.
  ///
  /// In pt, this message translates to:
  /// **'Hoje'**
  String get dateToday;

  /// No description provided for @dateYesterday.
  ///
  /// In pt, this message translates to:
  /// **'Ontem'**
  String get dateYesterday;

  /// No description provided for @monthPrevious.
  ///
  /// In pt, this message translates to:
  /// **'Mês anterior'**
  String get monthPrevious;

  /// No description provided for @monthNext.
  ///
  /// In pt, this message translates to:
  /// **'Próximo mês'**
  String get monthNext;

  /// No description provided for @timeAgoNow.
  ///
  /// In pt, this message translates to:
  /// **'agora há pouco'**
  String get timeAgoNow;

  /// No description provided for @timeAgoMinutes.
  ///
  /// In pt, this message translates to:
  /// **'{count, plural, =1{há 1 minuto} other{há {count} minutos}}'**
  String timeAgoMinutes(int count);

  /// No description provided for @timeAgoHours.
  ///
  /// In pt, this message translates to:
  /// **'{count, plural, =1{há 1 hora} other{há {count} horas}}'**
  String timeAgoHours(int count);

  /// No description provided for @timeAgoDays.
  ///
  /// In pt, this message translates to:
  /// **'{count, plural, =1{há 1 dia} other{há {count} dias}}'**
  String timeAgoDays(int count);

  /// No description provided for @updatedAgo.
  ///
  /// In pt, this message translates to:
  /// **'Atualizado {when}'**
  String updatedAgo(String when);

  /// No description provided for @splashUnreachable.
  ///
  /// In pt, this message translates to:
  /// **'Não foi possível falar com o servidor do Wallet. Confira sua conexão.'**
  String get splashUnreachable;

  /// No description provided for @loginSubtitle.
  ///
  /// In pt, this message translates to:
  /// **'Suas contas, cartões e investimentos num lugar só.'**
  String get loginSubtitle;

  /// No description provided for @loginAction.
  ///
  /// In pt, this message translates to:
  /// **'Entrar'**
  String get loginAction;

  /// No description provided for @loginGoToRegister.
  ///
  /// In pt, this message translates to:
  /// **'Ainda não tenho conta'**
  String get loginGoToRegister;

  /// No description provided for @serverLine.
  ///
  /// In pt, this message translates to:
  /// **'Servidor: {address}'**
  String serverLine(String address);

  /// No description provided for @serverChange.
  ///
  /// In pt, this message translates to:
  /// **'Alterar'**
  String get serverChange;

  /// No description provided for @serverDialogTitle.
  ///
  /// In pt, this message translates to:
  /// **'Endereço do servidor'**
  String get serverDialogTitle;

  /// No description provided for @serverDialogHelp.
  ///
  /// In pt, this message translates to:
  /// **'No celular, use o endereço do computador onde o Wallet está rodando, na mesma rede Wi-Fi. Exemplo: 192.168.0.10:8080.'**
  String get serverDialogHelp;

  /// No description provided for @serverFieldLabel.
  ///
  /// In pt, this message translates to:
  /// **'Endereço'**
  String get serverFieldLabel;

  /// No description provided for @serverInvalid.
  ///
  /// In pt, this message translates to:
  /// **'Digite um endereço como 192.168.0.10:8080.'**
  String get serverInvalid;

  /// No description provided for @serverSave.
  ///
  /// In pt, this message translates to:
  /// **'Salvar'**
  String get serverSave;

  /// No description provided for @serverUseDefault.
  ///
  /// In pt, this message translates to:
  /// **'Usar o padrão'**
  String get serverUseDefault;

  /// No description provided for @loginSessionExpired.
  ///
  /// In pt, this message translates to:
  /// **'Sua sessão expirou. Entre de novo.'**
  String get loginSessionExpired;

  /// No description provided for @loginSessionIdle.
  ///
  /// In pt, this message translates to:
  /// **'Por segurança, sua sessão terminou depois de 30 minutos sem uso.'**
  String get loginSessionIdle;

  /// No description provided for @loginAccountDeleted.
  ///
  /// In pt, this message translates to:
  /// **'Sua conta foi excluída, com todos os seus dados.'**
  String get loginAccountDeleted;

  /// No description provided for @registerSubtitle.
  ///
  /// In pt, this message translates to:
  /// **'Crie sua conta do Wallet.'**
  String get registerSubtitle;

  /// No description provided for @registerAction.
  ///
  /// In pt, this message translates to:
  /// **'Criar conta'**
  String get registerAction;

  /// No description provided for @registerGoToLogin.
  ///
  /// In pt, this message translates to:
  /// **'Já tenho conta'**
  String get registerGoToLogin;

  /// No description provided for @registerCheckEmailTitle.
  ///
  /// In pt, this message translates to:
  /// **'Confira seu e-mail'**
  String get registerCheckEmailTitle;

  /// No description provided for @registerCheckEmailBody.
  ///
  /// In pt, this message translates to:
  /// **'Enviamos um link para {email}. Abra o link para ativar a conta e depois entre aqui. Se não chegar em alguns minutos, olhe a caixa de spam.'**
  String registerCheckEmailBody(String email);

  /// No description provided for @registerResend.
  ///
  /// In pt, this message translates to:
  /// **'Reenviar o link'**
  String get registerResend;

  /// No description provided for @registerBackToLogin.
  ///
  /// In pt, this message translates to:
  /// **'Voltar para entrar'**
  String get registerBackToLogin;

  /// No description provided for @confirmationResent.
  ///
  /// In pt, this message translates to:
  /// **'Se este e-mail estiver esperando confirmação, um link novo chega em instantes.'**
  String get confirmationResent;

  /// No description provided for @loginNoConfirmation.
  ///
  /// In pt, this message translates to:
  /// **'Não recebeu o e-mail de confirmação?'**
  String get loginNoConfirmation;

  /// No description provided for @resendDialogTitle.
  ///
  /// In pt, this message translates to:
  /// **'Reenviar a confirmação'**
  String get resendDialogTitle;

  /// No description provided for @resendDialogHelp.
  ///
  /// In pt, this message translates to:
  /// **'Digite o e-mail da conta. Se ela estiver esperando confirmação, mandamos um link novo.'**
  String get resendDialogHelp;

  /// No description provided for @resendAction.
  ///
  /// In pt, this message translates to:
  /// **'Reenviar'**
  String get resendAction;

  /// No description provided for @loginTwoFactorHelp.
  ///
  /// In pt, this message translates to:
  /// **'Digite o código de 6 dígitos do seu app autenticador, ou um código de recuperação.'**
  String get loginTwoFactorHelp;

  /// No description provided for @fieldTwoFactorCode.
  ///
  /// In pt, this message translates to:
  /// **'Código'**
  String get fieldTwoFactorCode;

  /// No description provided for @fieldName.
  ///
  /// In pt, this message translates to:
  /// **'Nome'**
  String get fieldName;

  /// No description provided for @fieldEmail.
  ///
  /// In pt, this message translates to:
  /// **'E-mail'**
  String get fieldEmail;

  /// No description provided for @fieldPassword.
  ///
  /// In pt, this message translates to:
  /// **'Senha'**
  String get fieldPassword;

  /// No description provided for @validationRequired.
  ///
  /// In pt, this message translates to:
  /// **'Preencha este campo.'**
  String get validationRequired;

  /// No description provided for @validationEmail.
  ///
  /// In pt, this message translates to:
  /// **'Digite um e-mail válido.'**
  String get validationEmail;

  /// No description provided for @validationPasswordLength.
  ///
  /// In pt, this message translates to:
  /// **'Pelo menos {count} caracteres.'**
  String validationPasswordLength(int count);

  /// No description provided for @lockTitle.
  ///
  /// In pt, this message translates to:
  /// **'Wallet bloqueado'**
  String get lockTitle;

  /// No description provided for @lockUnlock.
  ///
  /// In pt, this message translates to:
  /// **'Desbloquear'**
  String get lockUnlock;

  /// No description provided for @lockReason.
  ///
  /// In pt, this message translates to:
  /// **'Desbloqueie para ver suas finanças'**
  String get lockReason;

  /// No description provided for @partUnavailable.
  ///
  /// In pt, this message translates to:
  /// **'Esta parte não carregou agora. Puxe para atualizar.'**
  String get partUnavailable;

  /// No description provided for @refreshFailed.
  ///
  /// In pt, this message translates to:
  /// **'Não deu para atualizar: {reason}'**
  String refreshFailed(String reason);

  /// No description provided for @institutionUnknown.
  ///
  /// In pt, this message translates to:
  /// **'Instituição'**
  String get institutionUnknown;

  /// No description provided for @overviewGreeting.
  ///
  /// In pt, this message translates to:
  /// **'Olá, {name}!'**
  String overviewGreeting(String name);

  /// No description provided for @overviewEmpty.
  ///
  /// In pt, this message translates to:
  /// **'Conecte um banco para ver seus saldos, cartões e investimentos aqui.'**
  String get overviewEmpty;

  /// No description provided for @overviewConnectFirst.
  ///
  /// In pt, this message translates to:
  /// **'Conectar banco'**
  String get overviewConnectFirst;

  /// No description provided for @overviewNetWorth.
  ///
  /// In pt, this message translates to:
  /// **'Patrimônio'**
  String get overviewNetWorth;

  /// No description provided for @overviewCash.
  ///
  /// In pt, this message translates to:
  /// **'Em conta'**
  String get overviewCash;

  /// No description provided for @overviewInvestments.
  ///
  /// In pt, this message translates to:
  /// **'Investimentos'**
  String get overviewInvestments;

  /// No description provided for @overviewCardDebt.
  ///
  /// In pt, this message translates to:
  /// **'Faturas de cartão'**
  String get overviewCardDebt;

  /// No description provided for @overviewMonth.
  ///
  /// In pt, this message translates to:
  /// **'Este mês'**
  String get overviewMonth;

  /// No description provided for @overviewIncome.
  ///
  /// In pt, this message translates to:
  /// **'Entradas'**
  String get overviewIncome;

  /// No description provided for @overviewExpenses.
  ///
  /// In pt, this message translates to:
  /// **'Saídas'**
  String get overviewExpenses;

  /// No description provided for @overviewMonthBalance.
  ///
  /// In pt, this message translates to:
  /// **'Saldo do mês'**
  String get overviewMonthBalance;

  /// No description provided for @overviewAccounts.
  ///
  /// In pt, this message translates to:
  /// **'Contas'**
  String get overviewAccounts;

  /// No description provided for @overviewNoBankAccounts.
  ///
  /// In pt, this message translates to:
  /// **'Nenhuma conta corrente ou poupança nesta conexão.'**
  String get overviewNoBankAccounts;

  /// No description provided for @overviewRecent.
  ///
  /// In pt, this message translates to:
  /// **'Últimas movimentações'**
  String get overviewRecent;

  /// No description provided for @overviewSeeStatement.
  ///
  /// In pt, this message translates to:
  /// **'Ver extrato'**
  String get overviewSeeStatement;

  /// No description provided for @overviewNoRecent.
  ///
  /// In pt, this message translates to:
  /// **'Nenhuma movimentação nos últimos 30 dias.'**
  String get overviewNoRecent;

  /// No description provided for @calendarTitle.
  ///
  /// In pt, this message translates to:
  /// **'Dias com gastos'**
  String get calendarTitle;

  /// No description provided for @calendarSummary.
  ///
  /// In pt, this message translates to:
  /// **'{count, plural, =0{Nenhum dia com gastos} =1{1 dia com gastos} other{{count} dias com gastos}}'**
  String calendarSummary(int count);

  /// No description provided for @calendarDaySpending.
  ///
  /// In pt, this message translates to:
  /// **'{date}: {amount} em gastos'**
  String calendarDaySpending(String date, String amount);

  /// No description provided for @calendarDayNoSpending.
  ///
  /// In pt, this message translates to:
  /// **'{date}: nenhum gasto'**
  String calendarDayNoSpending(String date);

  /// No description provided for @calendarListTitle.
  ///
  /// In pt, this message translates to:
  /// **'Gastos · {period}'**
  String calendarListTitle(String period);

  /// No description provided for @calendarHintPick.
  ///
  /// In pt, this message translates to:
  /// **'Toque num dia para ver só os gastos dele.'**
  String get calendarHintPick;

  /// No description provided for @calendarHintUnpick.
  ///
  /// In pt, this message translates to:
  /// **'Toque de novo no dia para voltar ao mês inteiro.'**
  String get calendarHintUnpick;

  /// No description provided for @calendarMonthEmpty.
  ///
  /// In pt, this message translates to:
  /// **'Nenhum gasto neste mês.'**
  String get calendarMonthEmpty;

  /// No description provided for @calendarDayEmpty.
  ///
  /// In pt, this message translates to:
  /// **'Nenhum gasto neste dia.'**
  String get calendarDayEmpty;

  /// No description provided for @themeToLight.
  ///
  /// In pt, this message translates to:
  /// **'Tema claro'**
  String get themeToLight;

  /// No description provided for @themeToDark.
  ///
  /// In pt, this message translates to:
  /// **'Tema escuro'**
  String get themeToDark;

  /// No description provided for @settingsAppearance.
  ///
  /// In pt, this message translates to:
  /// **'Aparência'**
  String get settingsAppearance;

  /// No description provided for @themeLight.
  ///
  /// In pt, this message translates to:
  /// **'Claro'**
  String get themeLight;

  /// No description provided for @themeDark.
  ///
  /// In pt, this message translates to:
  /// **'Escuro'**
  String get themeDark;

  /// No description provided for @themeSystem.
  ///
  /// In pt, this message translates to:
  /// **'Automático'**
  String get themeSystem;

  /// No description provided for @transactionsSearch.
  ///
  /// In pt, this message translates to:
  /// **'Buscar pela descrição'**
  String get transactionsSearch;

  /// No description provided for @transactionsEmpty.
  ///
  /// In pt, this message translates to:
  /// **'Nenhuma movimentação com esses filtros.'**
  String get transactionsEmpty;

  /// No description provided for @transactionPending.
  ///
  /// In pt, this message translates to:
  /// **'Pendente'**
  String get transactionPending;

  /// No description provided for @transactionInstallment.
  ///
  /// In pt, this message translates to:
  /// **'Parcela {installment}'**
  String transactionInstallment(String installment);

  /// No description provided for @filterAll.
  ///
  /// In pt, this message translates to:
  /// **'Tudo'**
  String get filterAll;

  /// No description provided for @filterInflow.
  ///
  /// In pt, this message translates to:
  /// **'Entradas'**
  String get filterInflow;

  /// No description provided for @filterOutflow.
  ///
  /// In pt, this message translates to:
  /// **'Saídas'**
  String get filterOutflow;

  /// No description provided for @filterAccount.
  ///
  /// In pt, this message translates to:
  /// **'Conta'**
  String get filterAccount;

  /// No description provided for @filterAllAccounts.
  ///
  /// In pt, this message translates to:
  /// **'Todas as contas'**
  String get filterAllAccounts;

  /// No description provided for @cardsEmpty.
  ///
  /// In pt, this message translates to:
  /// **'Nenhum cartão de crédito conectado.'**
  String get cardsEmpty;

  /// No description provided for @cardCurrentBill.
  ///
  /// In pt, this message translates to:
  /// **'Fatura atual'**
  String get cardCurrentBill;

  /// No description provided for @cardDueDate.
  ///
  /// In pt, this message translates to:
  /// **'Vencimento'**
  String get cardDueDate;

  /// No description provided for @cardClosingDate.
  ///
  /// In pt, this message translates to:
  /// **'Fechamento'**
  String get cardClosingDate;

  /// No description provided for @cardMinimumPayment.
  ///
  /// In pt, this message translates to:
  /// **'Pagamento mínimo'**
  String get cardMinimumPayment;

  /// No description provided for @cardNoBill.
  ///
  /// In pt, this message translates to:
  /// **'O banco ainda não mandou faturas deste cartão.'**
  String get cardNoBill;

  /// No description provided for @cardAvailable.
  ///
  /// In pt, this message translates to:
  /// **'Disponível {available} de {limit}'**
  String cardAvailable(String available, String limit);

  /// No description provided for @cardCurrentBalance.
  ///
  /// In pt, this message translates to:
  /// **'Gasto em aberto no cartão: {amount}'**
  String cardCurrentBalance(String amount);

  /// No description provided for @cardSeeBills.
  ///
  /// In pt, this message translates to:
  /// **'Faturas'**
  String get cardSeeBills;

  /// No description provided for @cardSeePurchases.
  ///
  /// In pt, this message translates to:
  /// **'Compras'**
  String get cardSeePurchases;

  /// No description provided for @cardBills.
  ///
  /// In pt, this message translates to:
  /// **'Faturas'**
  String get cardBills;

  /// No description provided for @cardBillsEmpty.
  ///
  /// In pt, this message translates to:
  /// **'Nenhuma fatura ainda.'**
  String get cardBillsEmpty;

  /// No description provided for @cardBillDue.
  ///
  /// In pt, this message translates to:
  /// **'Vence em {date}'**
  String cardBillDue(String date);

  /// No description provided for @cardBillPastDue.
  ///
  /// In pt, this message translates to:
  /// **'Venceu em {date}'**
  String cardBillPastDue(String date);

  /// No description provided for @cardLastBill.
  ///
  /// In pt, this message translates to:
  /// **'Última fatura'**
  String get cardLastBill;

  /// No description provided for @cardBillClosed.
  ///
  /// In pt, this message translates to:
  /// **'fechou em {date}'**
  String cardBillClosed(String date);

  /// No description provided for @cardBillMinimum.
  ///
  /// In pt, this message translates to:
  /// **'mínimo {amount}'**
  String cardBillMinimum(String amount);

  /// No description provided for @cardBillOpen.
  ///
  /// In pt, this message translates to:
  /// **'Em aberto'**
  String get cardBillOpen;

  /// No description provided for @investmentsEmpty.
  ///
  /// In pt, this message translates to:
  /// **'Nenhum investimento conectado.'**
  String get investmentsEmpty;

  /// No description provided for @investmentsTotal.
  ///
  /// In pt, this message translates to:
  /// **'Total investido'**
  String get investmentsTotal;

  /// No description provided for @investmentDue.
  ///
  /// In pt, this message translates to:
  /// **'vence em {date}'**
  String investmentDue(String date);

  /// No description provided for @investmentKindFixedIncome.
  ///
  /// In pt, this message translates to:
  /// **'Renda fixa'**
  String get investmentKindFixedIncome;

  /// No description provided for @investmentKindTreasury.
  ///
  /// In pt, this message translates to:
  /// **'Tesouro Direto'**
  String get investmentKindTreasury;

  /// No description provided for @investmentKindFund.
  ///
  /// In pt, this message translates to:
  /// **'Fundos'**
  String get investmentKindFund;

  /// No description provided for @investmentKindEquity.
  ///
  /// In pt, this message translates to:
  /// **'Ações e ETFs'**
  String get investmentKindEquity;

  /// No description provided for @investmentKindRetirement.
  ///
  /// In pt, this message translates to:
  /// **'Previdência'**
  String get investmentKindRetirement;

  /// No description provided for @investmentKindOther.
  ///
  /// In pt, this message translates to:
  /// **'Outros'**
  String get investmentKindOther;

  /// No description provided for @investmentsAllocation.
  ///
  /// In pt, this message translates to:
  /// **'Distribuição dos investimentos'**
  String get investmentsAllocation;

  /// No description provided for @donutHintPick.
  ///
  /// In pt, this message translates to:
  /// **'Toque numa fatia para ver a porcentagem e o valor dela.'**
  String get donutHintPick;

  /// No description provided for @donutHintUnpick.
  ///
  /// In pt, this message translates to:
  /// **'Toque de novo na fatia para voltar ao total.'**
  String get donutHintUnpick;

  /// No description provided for @investmentsEvolution.
  ///
  /// In pt, this message translates to:
  /// **'Evolução dos investimentos'**
  String get investmentsEvolution;

  /// No description provided for @investmentsEvolutionGrowing.
  ///
  /// In pt, this message translates to:
  /// **'O gráfico começa no primeiro dia de uso e ganha um ponto a cada dia em que o Wallet atualiza seus investimentos.'**
  String get investmentsEvolutionGrowing;

  /// No description provided for @investmentsChange.
  ///
  /// In pt, this message translates to:
  /// **'{amount} ({percent}) no período'**
  String investmentsChange(String amount, String percent);

  /// No description provided for @investmentPeriodOneMonth.
  ///
  /// In pt, this message translates to:
  /// **'1M'**
  String get investmentPeriodOneMonth;

  /// No description provided for @investmentPeriodOneMonthHint.
  ///
  /// In pt, this message translates to:
  /// **'1 mês'**
  String get investmentPeriodOneMonthHint;

  /// No description provided for @investmentPeriodThreeMonths.
  ///
  /// In pt, this message translates to:
  /// **'3M'**
  String get investmentPeriodThreeMonths;

  /// No description provided for @investmentPeriodThreeMonthsHint.
  ///
  /// In pt, this message translates to:
  /// **'3 meses'**
  String get investmentPeriodThreeMonthsHint;

  /// No description provided for @investmentPeriodSixMonths.
  ///
  /// In pt, this message translates to:
  /// **'6M'**
  String get investmentPeriodSixMonths;

  /// No description provided for @investmentPeriodSixMonthsHint.
  ///
  /// In pt, this message translates to:
  /// **'6 meses'**
  String get investmentPeriodSixMonthsHint;

  /// No description provided for @investmentPeriodOneYear.
  ///
  /// In pt, this message translates to:
  /// **'1A'**
  String get investmentPeriodOneYear;

  /// No description provided for @investmentPeriodOneYearHint.
  ///
  /// In pt, this message translates to:
  /// **'1 ano'**
  String get investmentPeriodOneYearHint;

  /// No description provided for @investmentPeriodAll.
  ///
  /// In pt, this message translates to:
  /// **'Tudo'**
  String get investmentPeriodAll;

  /// No description provided for @investmentPeriodAllHint.
  ///
  /// In pt, this message translates to:
  /// **'Todo o período'**
  String get investmentPeriodAllHint;

  /// No description provided for @insightsSpending.
  ///
  /// In pt, this message translates to:
  /// **'Gastos por categoria'**
  String get insightsSpending;

  /// No description provided for @insightsTotal.
  ///
  /// In pt, this message translates to:
  /// **'Total'**
  String get insightsTotal;

  /// No description provided for @insightsNoSpending.
  ///
  /// In pt, this message translates to:
  /// **'Nenhum gasto neste mês.'**
  String get insightsNoSpending;

  /// No description provided for @insightsOtherCategories.
  ///
  /// In pt, this message translates to:
  /// **'Outras categorias'**
  String get insightsOtherCategories;

  /// No description provided for @insightsNetWorth.
  ///
  /// In pt, this message translates to:
  /// **'Evolução do patrimônio'**
  String get insightsNetWorth;

  /// No description provided for @insightsNetWorthGrowing.
  ///
  /// In pt, this message translates to:
  /// **'O gráfico começa no primeiro dia de uso e ganha um ponto a cada dia em que o Wallet atualiza seus dados.'**
  String get insightsNetWorthGrowing;

  /// No description provided for @planningLeftover.
  ///
  /// In pt, this message translates to:
  /// **'Sobra do mês'**
  String get planningLeftover;

  /// No description provided for @planningLeftoverFormula.
  ///
  /// In pt, this message translates to:
  /// **'Salário − faturas − gastos fixos'**
  String get planningLeftoverFormula;

  /// No description provided for @planningShortfall.
  ///
  /// In pt, this message translates to:
  /// **'Faltam {amount} para fechar o mês.'**
  String planningShortfall(String amount);

  /// No description provided for @planningNoSalaryHint.
  ///
  /// In pt, this message translates to:
  /// **'Sem salário para este mês. Toque em Ajustar no salário e informe quanto você recebe.'**
  String get planningNoSalaryHint;

  /// No description provided for @planningLegendFixed.
  ///
  /// In pt, this message translates to:
  /// **'Gastos fixos'**
  String get planningLegendFixed;

  /// No description provided for @planningLegendLeftover.
  ///
  /// In pt, this message translates to:
  /// **'Sobra'**
  String get planningLegendLeftover;

  /// No description provided for @planningSalary.
  ///
  /// In pt, this message translates to:
  /// **'Salário líquido'**
  String get planningSalary;

  /// No description provided for @planningSalaryAdjust.
  ///
  /// In pt, this message translates to:
  /// **'Ajustar'**
  String get planningSalaryAdjust;

  /// No description provided for @planningSalaryPredicted.
  ///
  /// In pt, this message translates to:
  /// **'Previsto'**
  String get planningSalaryPredicted;

  /// No description provided for @planningSalaryReceived.
  ///
  /// In pt, this message translates to:
  /// **'Caiu na conta em {date}.'**
  String planningSalaryReceived(String date);

  /// No description provided for @planningSalaryFromPreviousMonth.
  ///
  /// In pt, this message translates to:
  /// **'Previsto pelo salário do mês anterior. Deve cair por volta de {date}.'**
  String planningSalaryFromPreviousMonth(String date);

  /// No description provided for @planningSalaryExpected.
  ///
  /// In pt, this message translates to:
  /// **'Valor que você definiu. Vale até o salário do mês cair na conta.'**
  String get planningSalaryExpected;

  /// No description provided for @planningSalaryNone.
  ///
  /// In pt, this message translates to:
  /// **'Nenhum salário no extrato deste mês nem dos anteriores.'**
  String get planningSalaryNone;

  /// No description provided for @planningSalaryDialogTitle.
  ///
  /// In pt, this message translates to:
  /// **'Salário líquido esperado'**
  String get planningSalaryDialogTitle;

  /// No description provided for @planningSalaryDialogHelp.
  ///
  /// In pt, this message translates to:
  /// **'Usado enquanto o salário do mês não cai na conta. Quando ele cair, vale o valor do extrato.'**
  String get planningSalaryDialogHelp;

  /// No description provided for @planningSalaryField.
  ///
  /// In pt, this message translates to:
  /// **'Valor líquido'**
  String get planningSalaryField;

  /// No description provided for @planningSalaryUseAutomatic.
  ///
  /// In pt, this message translates to:
  /// **'Usar o automático'**
  String get planningSalaryUseAutomatic;

  /// No description provided for @planningSalarySaved.
  ///
  /// In pt, this message translates to:
  /// **'Salário esperado salvo.'**
  String get planningSalarySaved;

  /// No description provided for @planningSalaryCleared.
  ///
  /// In pt, this message translates to:
  /// **'O salário voltou a ser previsto pelo mês anterior.'**
  String get planningSalaryCleared;

  /// No description provided for @planningAmountInvalid.
  ///
  /// In pt, this message translates to:
  /// **'Digite um valor como 1.234,56.'**
  String get planningAmountInvalid;

  /// No description provided for @planningBillOpen.
  ///
  /// In pt, this message translates to:
  /// **'Aberta'**
  String get planningBillOpen;

  /// No description provided for @planningBillOpenHelp.
  ///
  /// In pt, this message translates to:
  /// **'Fecha por volta de {date} e ainda pode crescer.'**
  String planningBillOpenHelp(String date);

  /// No description provided for @planningBillMore.
  ///
  /// In pt, this message translates to:
  /// **'{count, plural, =1{mais 1 compra} other{mais {count} compras}}'**
  String planningBillMore(int count);

  /// No description provided for @planningBillLess.
  ///
  /// In pt, this message translates to:
  /// **'Mostrar menos'**
  String get planningBillLess;

  /// No description provided for @planningBillRefund.
  ///
  /// In pt, this message translates to:
  /// **'Estorno'**
  String get planningBillRefund;

  /// No description provided for @planningBillOtherCharges.
  ///
  /// In pt, this message translates to:
  /// **'Outros lançamentos'**
  String get planningBillOtherCharges;

  /// No description provided for @planningBillOtherChargesHelp.
  ///
  /// In pt, this message translates to:
  /// **'Tarifas, juros ou compras que o banco não detalhou.'**
  String get planningBillOtherChargesHelp;

  /// No description provided for @planningBillOtherCreditsHelp.
  ///
  /// In pt, this message translates to:
  /// **'Créditos que o banco não detalhou.'**
  String get planningBillOtherCreditsHelp;

  /// No description provided for @planningBillNoItems.
  ///
  /// In pt, this message translates to:
  /// **'O banco não detalhou as compras desta fatura.'**
  String get planningBillNoItems;

  /// No description provided for @planningBills.
  ///
  /// In pt, this message translates to:
  /// **'Faturas'**
  String get planningBills;

  /// No description provided for @planningNoBills.
  ///
  /// In pt, this message translates to:
  /// **'Nenhuma fatura de cartão vence neste mês.'**
  String get planningNoBills;

  /// No description provided for @planningFixed.
  ///
  /// In pt, this message translates to:
  /// **'Gastos fixos'**
  String get planningFixed;

  /// No description provided for @planningFixedAdd.
  ///
  /// In pt, this message translates to:
  /// **'Adicionar'**
  String get planningFixedAdd;

  /// No description provided for @planningFixedEmpty.
  ///
  /// In pt, this message translates to:
  /// **'Mensalidade, aluguel, boletos: o que não passa no cartão. Adicione uma vez e ele entra em todo mês.'**
  String get planningFixedEmpty;

  /// No description provided for @planningFixedDay.
  ///
  /// In pt, this message translates to:
  /// **'Todo dia {day}'**
  String planningFixedDay(int day);

  /// No description provided for @planningFixedTotal.
  ///
  /// In pt, this message translates to:
  /// **'Total'**
  String get planningFixedTotal;

  /// No description provided for @planningFixedNew.
  ///
  /// In pt, this message translates to:
  /// **'Novo gasto fixo'**
  String get planningFixedNew;

  /// No description provided for @planningFixedEdit.
  ///
  /// In pt, this message translates to:
  /// **'Editar gasto fixo'**
  String get planningFixedEdit;

  /// No description provided for @planningFixedHelp.
  ///
  /// In pt, this message translates to:
  /// **'Ele se repete todo mês. Nos dias 29, 30 e 31, cai no último dia dos meses mais curtos.'**
  String get planningFixedHelp;

  /// No description provided for @planningFixedAmount.
  ///
  /// In pt, this message translates to:
  /// **'Valor'**
  String get planningFixedAmount;

  /// No description provided for @planningFixedDayField.
  ///
  /// In pt, this message translates to:
  /// **'Dia do mês'**
  String get planningFixedDayField;

  /// No description provided for @planningFixedDayInvalid.
  ///
  /// In pt, this message translates to:
  /// **'Um dia de 1 a 31.'**
  String get planningFixedDayInvalid;

  /// No description provided for @planningFixedCategory.
  ///
  /// In pt, this message translates to:
  /// **'Categoria (opcional)'**
  String get planningFixedCategory;

  /// No description provided for @planningFixedDelete.
  ///
  /// In pt, this message translates to:
  /// **'Excluir'**
  String get planningFixedDelete;

  /// No description provided for @planningFixedDeleteConfirm.
  ///
  /// In pt, this message translates to:
  /// **'Excluir {name}? Ele sai de todos os meses.'**
  String planningFixedDeleteConfirm(String name);

  /// No description provided for @planningFixedSaved.
  ///
  /// In pt, this message translates to:
  /// **'Gasto fixo salvo.'**
  String get planningFixedSaved;

  /// No description provided for @planningFixedDeleted.
  ///
  /// In pt, this message translates to:
  /// **'Gasto fixo excluído.'**
  String get planningFixedDeleted;

  /// No description provided for @planningSuggestions.
  ///
  /// In pt, this message translates to:
  /// **'Sugestões do seu extrato'**
  String get planningSuggestions;

  /// No description provided for @planningSuggestionsHelp.
  ///
  /// In pt, this message translates to:
  /// **'Débitos que se repetem todo mês nas suas contas. Toque em um para adicionar.'**
  String get planningSuggestionsHelp;

  /// No description provided for @planningSuggestionDetails.
  ///
  /// In pt, this message translates to:
  /// **'{amount} · todo dia {day} · em {months} meses'**
  String planningSuggestionDetails(String amount, int day, int months);

  /// No description provided for @planningSuggestionAdd.
  ///
  /// In pt, this message translates to:
  /// **'Adicionar aos gastos fixos'**
  String get planningSuggestionAdd;

  /// No description provided for @planningSuggestionDismiss.
  ///
  /// In pt, this message translates to:
  /// **'Dispensar sugestão'**
  String get planningSuggestionDismiss;

  /// No description provided for @planningSuggestionDismissed.
  ///
  /// In pt, this message translates to:
  /// **'Sugestão dispensada. Ela não volta a aparecer.'**
  String get planningSuggestionDismissed;

  /// No description provided for @planningSuggestionsMore.
  ///
  /// In pt, this message translates to:
  /// **'{count, plural, =1{mais 1 sugestão} other{mais {count} sugestões}}'**
  String planningSuggestionsMore(int count);

  /// No description provided for @overviewLeftoverDetail.
  ///
  /// In pt, this message translates to:
  /// **'Salário {salary} · faturas {bills} · fixos {fixed}'**
  String overviewLeftoverDetail(String salary, String bills, String fixed);

  /// No description provided for @overviewLeftoverPredicted.
  ///
  /// In pt, this message translates to:
  /// **'Com o salário previsto.'**
  String get overviewLeftoverPredicted;

  /// No description provided for @overviewSeePlanning.
  ///
  /// In pt, this message translates to:
  /// **'Ver planejamento'**
  String get overviewSeePlanning;

  /// No description provided for @connectionsEmpty.
  ///
  /// In pt, this message translates to:
  /// **'Nenhuma conexão ainda. Conecte seu banco no Meu Pluggy e vincule aqui pelo Item ID.'**
  String get connectionsEmpty;

  /// No description provided for @connectionsLink.
  ///
  /// In pt, this message translates to:
  /// **'Vincular conexão'**
  String get connectionsLink;

  /// No description provided for @connectionsLinkAction.
  ///
  /// In pt, this message translates to:
  /// **'Vincular'**
  String get connectionsLinkAction;

  /// No description provided for @connectionsLinkHelp.
  ///
  /// In pt, this message translates to:
  /// **'Conecte o banco no painel do Meu Pluggy, abra a conexão e copie o Item ID. O Wallet só lê os dados: não faz pagamentos nem transferências.'**
  String get connectionsLinkHelp;

  /// No description provided for @connectionsItemId.
  ///
  /// In pt, this message translates to:
  /// **'Item ID'**
  String get connectionsItemId;

  /// No description provided for @connectionsItemIdInvalid.
  ///
  /// In pt, this message translates to:
  /// **'O Item ID tem só letras, números e hífens.'**
  String get connectionsItemIdInvalid;

  /// No description provided for @connectionsDemoHint.
  ///
  /// In pt, this message translates to:
  /// **'Desenvolvimento: com o core em modo demonstração, use demo-nubank, demo-itau ou demo-xp (ou os básicos demo-banco e demo-corretora).'**
  String get connectionsDemoHint;

  /// No description provided for @connectionsLinked.
  ///
  /// In pt, this message translates to:
  /// **'Conexão vinculada. Os dados chegam em instantes.'**
  String get connectionsLinked;

  /// No description provided for @connectionsConnect.
  ///
  /// In pt, this message translates to:
  /// **'Conectar banco'**
  String get connectionsConnect;

  /// No description provided for @connectionsEmptyConnect.
  ///
  /// In pt, this message translates to:
  /// **'Nenhuma conexão ainda. Toque em Conectar banco, escolha o banco e autorize no app dele.'**
  String get connectionsEmptyConnect;

  /// No description provided for @connectionsLinkByItemId.
  ///
  /// In pt, this message translates to:
  /// **'Vincular pelo Item ID'**
  String get connectionsLinkByItemId;

  /// No description provided for @connectionsConnected.
  ///
  /// In pt, this message translates to:
  /// **'Banco conectado. Os dados chegam em instantes.'**
  String get connectionsConnected;

  /// No description provided for @connectOpening.
  ///
  /// In pt, this message translates to:
  /// **'Abrindo a Pluggy…'**
  String get connectOpening;

  /// No description provided for @connectPageFailed.
  ///
  /// In pt, this message translates to:
  /// **'Não foi possível abrir a Pluggy. Confira sua internet e tente de novo.'**
  String get connectPageFailed;

  /// No description provided for @connectionNeverSynced.
  ///
  /// In pt, this message translates to:
  /// **'Ainda não sincronizou'**
  String get connectionNeverSynced;

  /// No description provided for @connectionConsentUntil.
  ///
  /// In pt, this message translates to:
  /// **'Consentimento válido até {date}'**
  String connectionConsentUntil(String date);

  /// No description provided for @connectionNeedsAttention.
  ///
  /// In pt, this message translates to:
  /// **'O banco pede uma ação sua'**
  String get connectionNeedsAttention;

  /// No description provided for @connectionNeedsAttentionHelp.
  ///
  /// In pt, this message translates to:
  /// **'O banco pede uma ação sua: abra o Meu Pluggy, reconecte esta conta e depois toque em Atualizar.'**
  String get connectionNeedsAttentionHelp;

  /// No description provided for @connectionSync.
  ///
  /// In pt, this message translates to:
  /// **'Atualizar'**
  String get connectionSync;

  /// No description provided for @connectionSyncing.
  ///
  /// In pt, this message translates to:
  /// **'Atualizando…'**
  String get connectionSyncing;

  /// No description provided for @connectionSyncStarted.
  ///
  /// In pt, this message translates to:
  /// **'Atualização iniciada.'**
  String get connectionSyncStarted;

  /// No description provided for @connectionUnlink.
  ///
  /// In pt, this message translates to:
  /// **'Desvincular'**
  String get connectionUnlink;

  /// No description provided for @connectionUnlinkConfirm.
  ///
  /// In pt, this message translates to:
  /// **'Desvincular {name}? As contas, cartões e investimentos desta conexão saem do Wallet.'**
  String connectionUnlinkConfirm(String name);

  /// No description provided for @connectionUnlinked.
  ///
  /// In pt, this message translates to:
  /// **'Conexão desvinculada.'**
  String get connectionUnlinked;

  /// No description provided for @connectionStatusActive.
  ///
  /// In pt, this message translates to:
  /// **'Ativa'**
  String get connectionStatusActive;

  /// No description provided for @connectionStatusSyncing.
  ///
  /// In pt, this message translates to:
  /// **'Atualizando'**
  String get connectionStatusSyncing;

  /// No description provided for @connectionStatusNeedsAttention.
  ///
  /// In pt, this message translates to:
  /// **'Precisa de atenção'**
  String get connectionStatusNeedsAttention;

  /// No description provided for @connectionStatusUnknown.
  ///
  /// In pt, this message translates to:
  /// **'Indefinido'**
  String get connectionStatusUnknown;

  /// No description provided for @settingsAccount.
  ///
  /// In pt, this message translates to:
  /// **'Conta'**
  String get settingsAccount;

  /// No description provided for @settingsSecurity.
  ///
  /// In pt, this message translates to:
  /// **'Segurança'**
  String get settingsSecurity;

  /// No description provided for @settingsEditName.
  ///
  /// In pt, this message translates to:
  /// **'Alterar o nome'**
  String get settingsEditName;

  /// No description provided for @settingsChangeEmail.
  ///
  /// In pt, this message translates to:
  /// **'Trocar o e-mail'**
  String get settingsChangeEmail;

  /// No description provided for @settingsPendingEmail.
  ///
  /// In pt, this message translates to:
  /// **'Aguardando a confirmação de {email}'**
  String settingsPendingEmail(String email);

  /// No description provided for @nameDialogTitle.
  ///
  /// In pt, this message translates to:
  /// **'Seu nome'**
  String get nameDialogTitle;

  /// No description provided for @emailDialogTitle.
  ///
  /// In pt, this message translates to:
  /// **'Trocar o e-mail'**
  String get emailDialogTitle;

  /// No description provided for @emailDialogHelp.
  ///
  /// In pt, this message translates to:
  /// **'Mandamos um link para o novo endereço. A troca só vale depois que você abrir o link, e o e-mail atual recebe um aviso.'**
  String get emailDialogHelp;

  /// No description provided for @fieldNewEmail.
  ///
  /// In pt, this message translates to:
  /// **'Novo e-mail'**
  String get fieldNewEmail;

  /// No description provided for @fieldYourPassword.
  ///
  /// In pt, this message translates to:
  /// **'Sua senha'**
  String get fieldYourPassword;

  /// No description provided for @emailDialogSent.
  ///
  /// In pt, this message translates to:
  /// **'Link enviado para {email}. Abra-o para concluir a troca.'**
  String emailDialogSent(String email);

  /// No description provided for @settingsTwoFactor.
  ///
  /// In pt, this message translates to:
  /// **'Verificação em duas etapas'**
  String get settingsTwoFactor;

  /// No description provided for @settingsTwoFactorOn.
  ///
  /// In pt, this message translates to:
  /// **'Ligada: entrar pede também o código do app autenticador.'**
  String get settingsTwoFactorOn;

  /// No description provided for @settingsTwoFactorOff.
  ///
  /// In pt, this message translates to:
  /// **'Desligada. Ligue para pedir também um código do app autenticador ao entrar.'**
  String get settingsTwoFactorOff;

  /// No description provided for @twoFactorSetupTitle.
  ///
  /// In pt, this message translates to:
  /// **'Ligar a verificação em duas etapas'**
  String get twoFactorSetupTitle;

  /// No description provided for @twoFactorPasswordHelp.
  ///
  /// In pt, this message translates to:
  /// **'Para começar, confirme a sua senha.'**
  String get twoFactorPasswordHelp;

  /// No description provided for @twoFactorScanHelp.
  ///
  /// In pt, this message translates to:
  /// **'Escaneie o QR code com um app autenticador (Google Authenticator, Microsoft Authenticator, 1Password...). Sem câmera? Digite a chave abaixo no app.'**
  String get twoFactorScanHelp;

  /// No description provided for @twoFactorSecretLabel.
  ///
  /// In pt, this message translates to:
  /// **'Chave'**
  String get twoFactorSecretLabel;

  /// No description provided for @twoFactorCodeHelp.
  ///
  /// In pt, this message translates to:
  /// **'Depois, digite o código de 6 dígitos que aparece no app.'**
  String get twoFactorCodeHelp;

  /// No description provided for @twoFactorEnableAction.
  ///
  /// In pt, this message translates to:
  /// **'Ligar'**
  String get twoFactorEnableAction;

  /// No description provided for @twoFactorRecoveryTitle.
  ///
  /// In pt, this message translates to:
  /// **'Guarde os códigos de recuperação'**
  String get twoFactorRecoveryTitle;

  /// No description provided for @twoFactorRecoveryHelp.
  ///
  /// In pt, this message translates to:
  /// **'Cada código funciona uma vez, no lugar do app, se você perder o celular. Guarde num lugar seguro: eles não aparecem de novo.'**
  String get twoFactorRecoveryHelp;

  /// No description provided for @twoFactorCopyCodes.
  ///
  /// In pt, this message translates to:
  /// **'Copiar os códigos'**
  String get twoFactorCopyCodes;

  /// No description provided for @twoFactorCopied.
  ///
  /// In pt, this message translates to:
  /// **'Códigos copiados.'**
  String get twoFactorCopied;

  /// No description provided for @twoFactorDone.
  ///
  /// In pt, this message translates to:
  /// **'Pronto'**
  String get twoFactorDone;

  /// No description provided for @twoFactorDisableTitle.
  ///
  /// In pt, this message translates to:
  /// **'Desligar a verificação em duas etapas'**
  String get twoFactorDisableTitle;

  /// No description provided for @twoFactorDisableHelp.
  ///
  /// In pt, this message translates to:
  /// **'Confirme a sua senha e um código do app autenticador (ou um código de recuperação).'**
  String get twoFactorDisableHelp;

  /// No description provided for @twoFactorDisableAction.
  ///
  /// In pt, this message translates to:
  /// **'Desligar'**
  String get twoFactorDisableAction;

  /// No description provided for @settingsBiometric.
  ///
  /// In pt, this message translates to:
  /// **'Pedir biometria ao voltar'**
  String get settingsBiometric;

  /// No description provided for @settingsBiometricHelp.
  ///
  /// In pt, this message translates to:
  /// **'Depois de 5 minutos fora do app, ele pede sua digital, rosto ou senha do aparelho.'**
  String get settingsBiometricHelp;

  /// No description provided for @settingsBiometricUnavailable.
  ///
  /// In pt, this message translates to:
  /// **'Este aparelho não tem bloqueio de tela configurado.'**
  String get settingsBiometricUnavailable;

  /// No description provided for @settingsWindowsHello.
  ///
  /// In pt, this message translates to:
  /// **'Pedir o Windows Hello ao voltar'**
  String get settingsWindowsHello;

  /// No description provided for @settingsWindowsHelloHelp.
  ///
  /// In pt, this message translates to:
  /// **'Depois de 5 minutos com o Wallet minimizado, ele pede o PIN, o rosto ou a digital do Windows Hello.'**
  String get settingsWindowsHelloHelp;

  /// No description provided for @settingsWindowsHelloUnavailable.
  ///
  /// In pt, this message translates to:
  /// **'O Windows Hello não está configurado neste computador.'**
  String get settingsWindowsHelloUnavailable;

  /// No description provided for @settingsWebSession.
  ///
  /// In pt, this message translates to:
  /// **'Sessão no navegador'**
  String get settingsWebSession;

  /// No description provided for @settingsWebSessionHelp.
  ///
  /// In pt, this message translates to:
  /// **'Por segurança, a sessão termina depois de 30 minutos sem uso.'**
  String get settingsWebSessionHelp;

  /// No description provided for @settingsSignOut.
  ///
  /// In pt, this message translates to:
  /// **'Sair'**
  String get settingsSignOut;

  /// No description provided for @settingsDangerZone.
  ///
  /// In pt, this message translates to:
  /// **'Zona de perigo'**
  String get settingsDangerZone;

  /// No description provided for @settingsDeleteAccount.
  ///
  /// In pt, this message translates to:
  /// **'Excluir conta'**
  String get settingsDeleteAccount;

  /// No description provided for @settingsDeleteAccountHelp.
  ///
  /// In pt, this message translates to:
  /// **'Apaga sua conta e todos os dados no Wallet, e revoga o acesso aos seus bancos.'**
  String get settingsDeleteAccountHelp;

  /// No description provided for @deleteAccountTitle.
  ///
  /// In pt, this message translates to:
  /// **'Excluir sua conta?'**
  String get deleteAccountTitle;

  /// No description provided for @deleteAccountWarning.
  ///
  /// In pt, this message translates to:
  /// **'Isso não pode ser desfeito. Vão embora de vez:'**
  String get deleteAccountWarning;

  /// No description provided for @deleteAccountWhatGoes.
  ///
  /// In pt, this message translates to:
  /// **'• suas contas, cartões, faturas, transações e investimentos no Wallet;\n• seu planejamento e gastos fixos;\n• o acesso do Wallet aos seus bancos: as conexões são revogadas na Pluggy.'**
  String get deleteAccountWhatGoes;

  /// No description provided for @deleteAccountConfirmCheck.
  ///
  /// In pt, this message translates to:
  /// **'Entendo que não dá para desfazer'**
  String get deleteAccountConfirmCheck;

  /// No description provided for @deleteAccountPasswordHelp.
  ///
  /// In pt, this message translates to:
  /// **'Para confirmar, digite a senha da sua conta. Cada senha errada conta para o bloqueio temporário da conta.'**
  String get deleteAccountPasswordHelp;

  /// No description provided for @deleteAccountPasswordRequired.
  ///
  /// In pt, this message translates to:
  /// **'Digite sua senha para confirmar.'**
  String get deleteAccountPasswordRequired;

  /// No description provided for @deleteAccountAction.
  ///
  /// In pt, this message translates to:
  /// **'Excluir de vez'**
  String get deleteAccountAction;

  /// No description provided for @deleteAccountInProgress.
  ///
  /// In pt, this message translates to:
  /// **'Revogando o acesso aos seus bancos e apagando os dados. Pode levar até 2 minutos; não feche o app.'**
  String get deleteAccountInProgress;

  /// No description provided for @deleteAccountNotConfirmed.
  ///
  /// In pt, this message translates to:
  /// **'Não deu para confirmar a exclusão. Tente de novo: se a conta já tiver sido excluída, o Wallet avisa.'**
  String get deleteAccountNotConfirmed;

  /// No description provided for @showPassword.
  ///
  /// In pt, this message translates to:
  /// **'Mostrar senha'**
  String get showPassword;

  /// No description provided for @hidePassword.
  ///
  /// In pt, this message translates to:
  /// **'Esconder senha'**
  String get hidePassword;

  /// No description provided for @settingsAbout.
  ///
  /// In pt, this message translates to:
  /// **'Wallet lê seus dados pelo Open Finance, por meio da Pluggy. Nenhum pagamento sai daqui.'**
  String get settingsAbout;

  /// No description provided for @settingsDesktop.
  ///
  /// In pt, this message translates to:
  /// **'Computador'**
  String get settingsDesktop;

  /// No description provided for @settingsCloseToTray.
  ///
  /// In pt, this message translates to:
  /// **'Ao fechar, continuar na bandeja'**
  String get settingsCloseToTray;

  /// No description provided for @settingsCloseToTrayHelp.
  ///
  /// In pt, this message translates to:
  /// **'O X esconde o Wallet perto do relógio, onde ele continua se atualizando. Para sair de vez, use Sair no ícone da bandeja.'**
  String get settingsCloseToTrayHelp;

  /// No description provided for @settingsLaunchAtStartup.
  ///
  /// In pt, this message translates to:
  /// **'Abrir com o Windows'**
  String get settingsLaunchAtStartup;

  /// No description provided for @settingsLaunchAtStartupHelp.
  ///
  /// In pt, this message translates to:
  /// **'Quando você entra no Windows, o Wallet já abre na bandeja.'**
  String get settingsLaunchAtStartupHelp;

  /// No description provided for @settingsVersion.
  ///
  /// In pt, this message translates to:
  /// **'Versão {version}'**
  String settingsVersion(String version);

  /// No description provided for @trayOpen.
  ///
  /// In pt, this message translates to:
  /// **'Abrir o Wallet'**
  String get trayOpen;

  /// No description provided for @trayQuit.
  ///
  /// In pt, this message translates to:
  /// **'Sair'**
  String get trayQuit;

  /// No description provided for @updateDisabled.
  ///
  /// In pt, this message translates to:
  /// **'Build de desenvolvimento: não se atualiza sozinho.'**
  String get updateDisabled;

  /// No description provided for @updateUpToDate.
  ///
  /// In pt, this message translates to:
  /// **'Você está na versão mais nova.'**
  String get updateUpToDate;

  /// No description provided for @updateChecking.
  ///
  /// In pt, this message translates to:
  /// **'Procurando atualizações…'**
  String get updateChecking;

  /// No description provided for @updateDownloading.
  ///
  /// In pt, this message translates to:
  /// **'Baixando a versão {version}…'**
  String updateDownloading(String version);

  /// No description provided for @updateReady.
  ///
  /// In pt, this message translates to:
  /// **'A versão {version} já foi baixada.'**
  String updateReady(String version);

  /// No description provided for @updateFailed.
  ///
  /// In pt, this message translates to:
  /// **'Não deu para procurar atualizações agora. O Wallet tenta de novo mais tarde.'**
  String get updateFailed;

  /// No description provided for @updateCheckNow.
  ///
  /// In pt, this message translates to:
  /// **'Procurar agora'**
  String get updateCheckNow;

  /// No description provided for @updateRestart.
  ///
  /// In pt, this message translates to:
  /// **'Reiniciar e atualizar'**
  String get updateRestart;

  /// No description provided for @updateLater.
  ///
  /// In pt, this message translates to:
  /// **'Depois'**
  String get updateLater;

  /// No description provided for @updateToastTitle.
  ///
  /// In pt, this message translates to:
  /// **'Nova versão disponível'**
  String get updateToastTitle;

  /// No description provided for @updateToastMessage.
  ///
  /// In pt, this message translates to:
  /// **'O Wallet {version} já foi baixado. Reinicie para atualizar.'**
  String updateToastMessage(String version);

  /// No description provided for @updateRestartShort.
  ///
  /// In pt, this message translates to:
  /// **'Reiniciar'**
  String get updateRestartShort;

  /// No description provided for @updatedToastTitle.
  ///
  /// In pt, this message translates to:
  /// **'Wallet atualizado'**
  String get updatedToastTitle;

  /// No description provided for @updatedToastMessage.
  ///
  /// In pt, this message translates to:
  /// **'Agora você está na versão {version}.'**
  String updatedToastMessage(String version);

  /// No description provided for @updatedToastOk.
  ///
  /// In pt, this message translates to:
  /// **'Ok'**
  String get updatedToastOk;

  /// No description provided for @settingsUpdates.
  ///
  /// In pt, this message translates to:
  /// **'Atualizações'**
  String get settingsUpdates;

  /// No description provided for @updateInstall.
  ///
  /// In pt, this message translates to:
  /// **'Atualizar'**
  String get updateInstall;

  /// No description provided for @updateToastMessageConfirm.
  ///
  /// In pt, this message translates to:
  /// **'O Wallet {version} já foi baixado. Toque em Atualizar para instalar.'**
  String updateToastMessageConfirm(String version);

  /// No description provided for @updateAllowTitle.
  ///
  /// In pt, this message translates to:
  /// **'Permita as atualizações'**
  String get updateAllowTitle;

  /// No description provided for @updateAllowMessage.
  ///
  /// In pt, this message translates to:
  /// **'Na primeira vez, o Android pede para o Wallet poder instalar a versão {version}. Em Configurações, ligue Permitir desta fonte e volte para o Wallet.'**
  String updateAllowMessage(String version);

  /// No description provided for @updateAllowOpen.
  ///
  /// In pt, this message translates to:
  /// **'Abrir configurações'**
  String get updateAllowOpen;

  /// No description provided for @updateNotAllowed.
  ///
  /// In pt, this message translates to:
  /// **'Para instalar a versão {version}, o Wallet precisa da sua permissão: em Configurações, ligue Permitir desta fonte.'**
  String updateNotAllowed(String version);

  /// No description provided for @updateRefusedTitle.
  ///
  /// In pt, this message translates to:
  /// **'Atualização não instalada'**
  String get updateRefusedTitle;

  /// No description provided for @updateConflict.
  ///
  /// In pt, this message translates to:
  /// **'O Android recusou a versão {version} porque ela foi assinada com outra chave. Desinstale o Wallet e instale o APK dos Releases.'**
  String updateConflict(String version);

  /// No description provided for @updateInstallFailed.
  ///
  /// In pt, this message translates to:
  /// **'O Android não instalou a versão {version}. Tente de novo.'**
  String updateInstallFailed(String version);

  /// No description provided for @updateRetry.
  ///
  /// In pt, this message translates to:
  /// **'Tentar de novo'**
  String get updateRetry;

  /// No description provided for @windowClose.
  ///
  /// In pt, this message translates to:
  /// **'Fechar'**
  String get windowClose;

  /// No description provided for @windowMinimize.
  ///
  /// In pt, this message translates to:
  /// **'Minimizar'**
  String get windowMinimize;

  /// No description provided for @windowMaximize.
  ///
  /// In pt, this message translates to:
  /// **'Maximizar'**
  String get windowMaximize;

  /// No description provided for @settingsWindowBackground.
  ///
  /// In pt, this message translates to:
  /// **'Fundo da janela'**
  String get settingsWindowBackground;

  /// No description provided for @settingsWindowBackgroundHelp.
  ///
  /// In pt, this message translates to:
  /// **'Translúcido deixa ver, desfocado, o que está atrás da janela.'**
  String get settingsWindowBackgroundHelp;

  /// No description provided for @settingsWindowTranslucent.
  ///
  /// In pt, this message translates to:
  /// **'Translúcido'**
  String get settingsWindowTranslucent;

  /// No description provided for @settingsWindowSolid.
  ///
  /// In pt, this message translates to:
  /// **'Sólido'**
  String get settingsWindowSolid;

  /// No description provided for @errorNetworkUnreachable.
  ///
  /// In pt, this message translates to:
  /// **'Sem conexão com o servidor. Confira sua internet.'**
  String get errorNetworkUnreachable;

  /// No description provided for @errorNetworkTimeout.
  ///
  /// In pt, this message translates to:
  /// **'O servidor demorou para responder. Tente de novo.'**
  String get errorNetworkTimeout;

  /// No description provided for @errorInvalidCredentials.
  ///
  /// In pt, this message translates to:
  /// **'E-mail ou senha incorretos. Se você acabou de criar a conta, abra antes o link que enviamos para o seu e-mail.'**
  String get errorInvalidCredentials;

  /// No description provided for @errorTwoFactorRequired.
  ///
  /// In pt, this message translates to:
  /// **'Digite o código do seu app autenticador.'**
  String get errorTwoFactorRequired;

  /// No description provided for @errorInvalidTwoFactorCode.
  ///
  /// In pt, this message translates to:
  /// **'Código incorreto. Confira o app autenticador; cada erro conta para o bloqueio.'**
  String get errorInvalidTwoFactorCode;

  /// No description provided for @errorBusy.
  ///
  /// In pt, this message translates to:
  /// **'O Wallet está com muito movimento agora. Tente de novo em alguns segundos.'**
  String get errorBusy;

  /// No description provided for @errorTwoFactorSetupCode.
  ///
  /// In pt, this message translates to:
  /// **'O código não confere. Confira o horário do celular e digite o código que estiver aparecendo agora.'**
  String get errorTwoFactorSetupCode;

  /// No description provided for @errorTwoFactorNotSetUp.
  ///
  /// In pt, this message translates to:
  /// **'Comece de novo a configuração da verificação em duas etapas.'**
  String get errorTwoFactorNotSetUp;

  /// No description provided for @errorTwoFactorAlreadyEnabled.
  ///
  /// In pt, this message translates to:
  /// **'A verificação em duas etapas já está ligada.'**
  String get errorTwoFactorAlreadyEnabled;

  /// No description provided for @errorTwoFactorNotEnabled.
  ///
  /// In pt, this message translates to:
  /// **'A verificação em duas etapas já está desligada.'**
  String get errorTwoFactorNotEnabled;

  /// No description provided for @errorEmailUnchanged.
  ///
  /// In pt, this message translates to:
  /// **'Esse já é o e-mail da conta.'**
  String get errorEmailUnchanged;

  /// No description provided for @errorLinkInvalid.
  ///
  /// In pt, this message translates to:
  /// **'Este link venceu ou já foi usado.'**
  String get errorLinkInvalid;

  /// No description provided for @errorAuthLocked.
  ///
  /// In pt, this message translates to:
  /// **'Muitas tentativas erradas. Espere alguns minutos e tente de novo.'**
  String get errorAuthLocked;

  /// No description provided for @errorAuthLockedUntil.
  ///
  /// In pt, this message translates to:
  /// **'Muitas tentativas erradas. Tente de novo {when}.'**
  String errorAuthLockedUntil(String when);

  /// No description provided for @lockedUntilToday.
  ///
  /// In pt, this message translates to:
  /// **'às {time}'**
  String lockedUntilToday(String time);

  /// No description provided for @lockedUntilDay.
  ///
  /// In pt, this message translates to:
  /// **'em {date} às {time}'**
  String lockedUntilDay(String date, String time);

  /// No description provided for @errorWrongPassword.
  ///
  /// In pt, this message translates to:
  /// **'Senha incorreta. Cada erro conta para o bloqueio da conta.'**
  String get errorWrongPassword;

  /// No description provided for @errorEmailTaken.
  ///
  /// In pt, this message translates to:
  /// **'Já existe uma conta com este e-mail.'**
  String get errorEmailTaken;

  /// No description provided for @errorSessionExpired.
  ///
  /// In pt, this message translates to:
  /// **'Sua sessão expirou. Entre de novo.'**
  String get errorSessionExpired;

  /// No description provided for @errorValidation.
  ///
  /// In pt, this message translates to:
  /// **'Confira os campos e tente de novo.'**
  String get errorValidation;

  /// No description provided for @errorRateLimited.
  ///
  /// In pt, this message translates to:
  /// **'Muitas requisições seguidas. Espere um minuto.'**
  String get errorRateLimited;

  /// No description provided for @errorServiceUnavailable.
  ///
  /// In pt, this message translates to:
  /// **'O serviço está fora do ar agora. Tente em alguns minutos.'**
  String get errorServiceUnavailable;

  /// No description provided for @errorProviderNotConfigured.
  ///
  /// In pt, this message translates to:
  /// **'O servidor ainda não tem as credenciais da Pluggy.'**
  String get errorProviderNotConfigured;

  /// No description provided for @errorProviderAuthFailed.
  ///
  /// In pt, this message translates to:
  /// **'A Pluggy recusou as credenciais do servidor.'**
  String get errorProviderAuthFailed;

  /// No description provided for @errorProviderUpdating.
  ///
  /// In pt, this message translates to:
  /// **'O banco ainda está atualizando os dados. Tente em alguns minutos.'**
  String get errorProviderUpdating;

  /// No description provided for @errorItemNotFound.
  ///
  /// In pt, this message translates to:
  /// **'Não achamos esse Item ID na Pluggy.'**
  String get errorItemNotFound;

  /// No description provided for @errorAlreadyLinked.
  ///
  /// In pt, this message translates to:
  /// **'Essa conexão já está no Wallet.'**
  String get errorAlreadyLinked;

  /// No description provided for @errorConnectionLimit.
  ///
  /// In pt, this message translates to:
  /// **'Você chegou ao limite de conexões. Desvincule uma para vincular outra.'**
  String get errorConnectionLimit;

  /// No description provided for @errorConnectAttempts.
  ///
  /// In pt, this message translates to:
  /// **'Muitas tentativas de conectar banco em pouco tempo. Tente de novo mais tarde.'**
  String get errorConnectAttempts;

  /// No description provided for @errorConnectionGone.
  ///
  /// In pt, this message translates to:
  /// **'Essa conexão não existe mais.'**
  String get errorConnectionGone;

  /// No description provided for @errorConnectionNeedsAttention.
  ///
  /// In pt, this message translates to:
  /// **'O banco pede uma ação sua antes de atualizar.'**
  String get errorConnectionNeedsAttention;

  /// No description provided for @errorSyncTooSoon.
  ///
  /// In pt, this message translates to:
  /// **'Esta conexão tentou atualizar há poucos minutos. Tente de novo daqui a pouco.'**
  String get errorSyncTooSoon;

  /// No description provided for @errorSyncInProgress.
  ///
  /// In pt, this message translates to:
  /// **'Esta conexão já está atualizando.'**
  String get errorSyncInProgress;

  /// No description provided for @errorAccountNotFound.
  ///
  /// In pt, this message translates to:
  /// **'Conta não encontrada.'**
  String get errorAccountNotFound;

  /// No description provided for @errorInvalidFilter.
  ///
  /// In pt, this message translates to:
  /// **'Filtro inválido.'**
  String get errorInvalidFilter;

  /// No description provided for @errorFixedExpenseNotFound.
  ///
  /// In pt, this message translates to:
  /// **'Esse gasto fixo não existe mais.'**
  String get errorFixedExpenseNotFound;

  /// No description provided for @errorFixedExpenseLimit.
  ///
  /// In pt, this message translates to:
  /// **'Você chegou ao limite de 100 gastos fixos.'**
  String get errorFixedExpenseLimit;

  /// No description provided for @errorFixedExpenseSuggestionNotFound.
  ///
  /// In pt, this message translates to:
  /// **'Essa sugestão não está mais disponível.'**
  String get errorFixedExpenseSuggestionNotFound;

  /// No description provided for @errorUnexpected.
  ///
  /// In pt, this message translates to:
  /// **'Algo deu errado. Tente de novo.'**
  String get errorUnexpected;

  /// No description provided for @loginForgotPassword.
  ///
  /// In pt, this message translates to:
  /// **'Esqueci minha senha'**
  String get loginForgotPassword;

  /// No description provided for @loginPasswordChanged.
  ///
  /// In pt, this message translates to:
  /// **'Senha trocada. Entre de novo com a senha nova.'**
  String get loginPasswordChanged;

  /// No description provided for @forgotSubtitle.
  ///
  /// In pt, this message translates to:
  /// **'Vamos mandar um link para criar uma senha nova'**
  String get forgotSubtitle;

  /// No description provided for @forgotHelp.
  ///
  /// In pt, this message translates to:
  /// **'Digite o e-mail da sua conta.'**
  String get forgotHelp;

  /// No description provided for @forgotAction.
  ///
  /// In pt, this message translates to:
  /// **'Enviar o link'**
  String get forgotAction;

  /// No description provided for @forgotSent.
  ///
  /// In pt, this message translates to:
  /// **'Se houver uma conta com esse e-mail, enviamos um link para criar uma senha nova.'**
  String get forgotSent;

  /// No description provided for @settingsChangePassword.
  ///
  /// In pt, this message translates to:
  /// **'Trocar senha'**
  String get settingsChangePassword;

  /// No description provided for @settingsChangePasswordHelp.
  ///
  /// In pt, this message translates to:
  /// **'Pede a senha atual e tira todos os aparelhos da conta.'**
  String get settingsChangePasswordHelp;

  /// No description provided for @changePasswordHelp.
  ///
  /// In pt, this message translates to:
  /// **'Depois de trocar, todos os aparelhos saem da conta, este também, e você entra de novo com a senha nova.'**
  String get changePasswordHelp;

  /// No description provided for @changePasswordAction.
  ///
  /// In pt, this message translates to:
  /// **'Trocar senha'**
  String get changePasswordAction;

  /// No description provided for @fieldCurrentPassword.
  ///
  /// In pt, this message translates to:
  /// **'Senha atual'**
  String get fieldCurrentPassword;

  /// No description provided for @fieldNewPassword.
  ///
  /// In pt, this message translates to:
  /// **'Nova senha'**
  String get fieldNewPassword;

  /// No description provided for @fieldRepeatNewPassword.
  ///
  /// In pt, this message translates to:
  /// **'Repita a nova senha'**
  String get fieldRepeatNewPassword;

  /// No description provided for @validationPasswordsDiffer.
  ///
  /// In pt, this message translates to:
  /// **'As senhas não são iguais.'**
  String get validationPasswordsDiffer;

  /// No description provided for @validationPasswordSame.
  ///
  /// In pt, this message translates to:
  /// **'A nova senha precisa ser diferente da atual.'**
  String get validationPasswordSame;

  /// No description provided for @settingsNewRecoveryCodes.
  ///
  /// In pt, this message translates to:
  /// **'Gerar códigos novos'**
  String get settingsNewRecoveryCodes;

  /// No description provided for @settingsNewRecoveryCodesHelp.
  ///
  /// In pt, this message translates to:
  /// **'Os códigos de recuperação de agora deixam de valer.'**
  String get settingsNewRecoveryCodesHelp;

  /// No description provided for @newRecoveryCodesTitle.
  ///
  /// In pt, this message translates to:
  /// **'Gerar códigos de recuperação novos'**
  String get newRecoveryCodesTitle;

  /// No description provided for @newRecoveryCodesHelp.
  ///
  /// In pt, this message translates to:
  /// **'Digite sua senha e um código do app autenticador. Os códigos de recuperação de agora deixam de valer.'**
  String get newRecoveryCodesHelp;

  /// No description provided for @newRecoveryCodesAction.
  ///
  /// In pt, this message translates to:
  /// **'Gerar'**
  String get newRecoveryCodesAction;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['pt'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'pt':
      return AppLocalizationsPt();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
