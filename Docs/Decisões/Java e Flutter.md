---
tags: [decisao, stack]
atualizado: 2026-09-29
status: decidido
---

# Java e Flutter

**Decidido pelo Rafael em 2026-09-29:** backend em **Java 25 + Spring Boot 4.1** e
app em **Flutter** (Android, iOS e Web). Ruby foi considerado e ficou de fora.

## Por quê

- **Java:** é a stack que ele domina e usa no Agibank. Entrega mais rápido, tem
  tipagem estática (importante com dinheiro) e roda no Windows sem atrito.
- **Ruby on Rails** era viável: aqui o backend é uma API web, que é o terreno do
  Rails (no TLT foi descartado por ser desktop). A comparação ficou registrada em
  [[Stack]].
- **Flutter:** um código só para o celular e para o navegador do PC. Ambiente já
  instalado e experiência do app do Trilha.

## Consequências

- Arquitetura em [[Visão Geral]], [[BFF]], [[Core]] e [[App Flutter]].
- O JDK 25 ainda não está instalado na máquina (só o 21). Ver
  [[Plano de Implementação]], tarefa T03.
