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

  /// No description provided for @settingsAbout.
  ///
  /// In pt, this message translates to:
  /// **'Wallet lê seus dados pelo Open Finance, por meio da Pluggy. Nenhum pagamento sai daqui.'**
  String get settingsAbout;

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
  /// **'E-mail ou senha incorretos.'**
  String get errorInvalidCredentials;

  /// No description provided for @errorAuthLocked.
  ///
  /// In pt, this message translates to:
  /// **'Muitas tentativas erradas. Espere 15 minutos e tente de novo.'**
  String get errorAuthLocked;

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
  /// **'Esta conexão acabou de atualizar. Tente de novo em alguns minutos.'**
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

  /// No description provided for @errorUnexpected.
  ///
  /// In pt, this message translates to:
  /// **'Algo deu errado. Tente de novo.'**
  String get errorUnexpected;
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
